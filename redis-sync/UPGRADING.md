# Refactoring `RedisAPI.async()` -> `borrow()` / `borrowStringBytes()`

## Background

`RedisAPI.async()` and `RedisAPI.asyncStringBytes()` are deprecated. They return a
shared `RedisAsyncCommands` object with **no locking**, which allows commands from
different threads to interleave on the single Lettuce connection. The new API uses
try-with-resources handles that hold a `ReentrantLock` for the duration of the block:

```java
// OLD (deprecated)
RedisAPI.getInstance().async().hget("key", "field").toCompletableFuture();

// NEW - lock held only while enqueuing (near-instant), released on try-block exit
try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
    conn.hget("key", "field").toCompletableFuture();
}

// For binary values
try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
    conn.lindex("key", 0);
}
```

Key rules:
- Always use inside a `try-with-resources`.
- **Do NOT call `.join()`/`.get()` inside the try block** - this holds the lock while
  waiting for the network, blocking other threads from using Redis. Capture the future,
  exit the try block, then call `.join()`/`.get()` outside.
- Do not store the `BorrowedCommands` object or pass it outside the try block.
- For MULTI/EXEC atomic transactions, use `RedisAPI.multi()` or `RedisAPI.multiStringBytes()`.
  Never call `conn.multi()`/`conn.exec()` directly inside a `borrow()` block.


## Common patterns used in the refactor

### Single async command -> borrow()
```java
// Before
return RedisAPI.getInstance().async().hget(path, field).toCompletableFuture();

// After
try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
    return conn.hget(path, field).toCompletableFuture();
}
```

### .join() / .get() must be OUTSIDE the try block
```java
// WRONG - holds the lock while waiting for the network response
try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
    result = conn.hget(path, field).toCompletableFuture().join();
}

// CORRECT - lock released immediately after enqueue; block after
CompletableFuture<String> future;
try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
    future = conn.hget(path, field).toCompletableFuture();
}
result = future.join(); // lock already released
```

### Atomic transaction -> RedisAPI.multi() / multiStringBytes()

`RedisAPI.multi()` (String/String) and `RedisAPI.multiStringBytes()` (String/byte[])
both hold the connection lock for the entire MULTI/EXEC block, guaranteeing EXEC or
DISCARD is always called even if the lambda throws.

```java
// String/String transaction
RedisAPI.multi(conn -> {
    conn.lpush(path, value);
    conn.ltrim(path, 0, limit);
}).whenComplete((result, ex) -> { ... });

// String/byte[] transaction
RedisAPI.multiStringBytes(conn -> {
    conn.lpush(path, byteArray1);
    conn.lpush(path, byteArray2);
}).whenComplete((result, ex) -> { ... });
```

### Using TransactionResult from multi()

`multiStringBytes()` (and `multi()`) return `CompletableFuture<TransactionResult>`.
The `TransactionResult` holds the result of each command by index (0 = first command):

```java
// Read 5 keys atomically and transform the results
return RedisAPI.multiStringBytes(conn -> {
    conn.lindex(getRedisDataPath(uuid), 0);
    conn.lindex(getRedisAdvancementsPath(uuid), 0);
    conn.lindex(getRedisScoresPath(uuid), 0);
    conn.lindex(getRedisPluginDataPath(uuid), 0);
    conn.lindex(getRedisHistoryPath(uuid), 0);
}).thenApply(result -> transform(result.get(0), result.get(1), result.get(2),
    result.get(3), result.get(4)));
```

### Capturing a future from within a multi() transaction

When you need to chain async work off a specific command's result (rather than the
full `TransactionResult`), use a one-element array to capture the `RedisFuture` from
inside the lambda. Java requires lambda-captured variables to be effectively final,
so a plain local variable cannot be assigned inside a lambda. The array provides a
fixed memory location the lambda can write into.

```java
@SuppressWarnings("unchecked")
// fixed-memory-location array needed to store future from lambda
final RedisFuture<List<KeyValue<String, String>>>[] hmgetRef = new RedisFuture[1];
RedisAPI.multi(conn -> {
    conn.hincrby(path, key, 0);
    hmgetRef[0] = conn.hmget(path, keys);
});
return hmgetRef[0].toCompletableFuture().thenApply(list -> { ... });
```

### Waiting on multiple futures
```java
CompletableFuture<String> futureA;
CompletableFuture<String> futureB;
CompletableFuture<String> futureC;
try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
    futureA = conn.hget(path, "fieldA").toCompletableFuture();
    futureB = conn.hget(path, "fieldB").toCompletableFuture();
    futureC = conn.hget(path, "fieldC").toCompletableFuture();
}
CompletableFuture.allOf(futureA, futureB, futureC).whenComplete((unused, ex) -> {
    Bukkit.getScheduler().runTask(plugin, () -> {
        if (ex != null) {
            plugin.getLogger().severe("Redis read failed: " + ex.getMessage());
            return;
        }
        // .join() is non-blocking here - all futures are already complete
        String a = futureA.join();
        String b = futureB.join();
        String c = futureC.join();
    });
});
```

### DANGER: do not block the Lettuce I/O thread inside whenComplete()

`whenComplete()` callbacks run on the **Lettuce I/O thread** — the single thread
that both sends commands to and reads responses from Redis. Blocking it, or
keeping it busy for a long time, stalls every other pending Redis response on the
server.

**Deadlock** — issuing a new Redis command and calling `.join()`/`.get()` on it
inside a `whenComplete()` callback:

```java
// ❌ DEADLOCK - callback blocks the I/O thread waiting for a response it
//    can never deliver to itself
future.whenComplete((result, ex) -> {
    try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
        String value = conn.hget("key", "field")
            .toCompletableFuture()
            .join(); // blocks forever - deadlock
    }
});
```

**Fix option 1 — chain another `.whenComplete()`** (the current callback returns
immediately; the new callback fires later on the I/O thread):

```java
// ✅ Non-blocking chain
future.whenComplete((result, ex) -> {
    if (ex != null) { /* log */ return; }
    try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
        conn.hget("key", "field").whenComplete((value, ex2) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ex2 != null) { /* log */ return; }
                doSomething(value);
            });
        });
    }
});
```

**Fix option 2 — schedule onto a Bukkit async thread** (safe to block there):

```java
// ✅ Hand off to async thread before blocking
future.whenComplete((result, ex) -> {
    if (ex != null) { /* log */ return; }
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        CompletableFuture<String> next;
        try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
            next = conn.hget("key", "field").toCompletableFuture();
        }
        String value = next.join(); // safe on a Bukkit async thread
        Bukkit.getScheduler().runTask(plugin, () -> doSomething(value));
    });
});
```

**Guideline:** keep `whenComplete()` callbacks short and non-blocking. Even
work that never calls `.join()` / `.get()` — large JSON parsing, loops over
big data sets, multiple chained Redis calls — should be handed off to a Bukkit
async thread via `runTaskAsynchronously()` so the I/O thread is freed
immediately.

**Safe exception — `.join()` on already-complete futures:** inside a
`CompletableFuture.allOf()` callback, the constituent futures are **guaranteed
complete** before the callback fires, so `.join()` returns instantly without
blocking. This is safe. Never call `.join()` on a future unless you have such a
guarantee.

---

### Replacing runTaskAsynchronously + .join() with whenComplete() -> runTask()

A common old pattern uses `runTaskAsynchronously` to get off the main thread, calls
`borrow()` + `.join()` to block until Redis responds, then dispatches back with
`runTask`. The entire `runTaskAsynchronously` wrapper can be eliminated - `borrow()`
enqueues the command and returns immediately (no blocking), so it is safe to call on
the main thread. Chain `whenComplete` directly inside the `try` block and dispatch
any Bukkit API work back to the main thread via `runTask` from within the callback.

```java
// BEFORE
private static void myMethod(Plugin plugin) {
    Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
        CompletableFuture<String> future;
        try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
            future = conn.hget(path, field).toCompletableFuture();
        }
        String result = future.join();
        Bukkit.getScheduler().runTask(plugin, () -> {
            doSomethingWith(result);
        });
    });
}

// AFTER
private static void myMethod(Plugin plugin) {
    try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
        conn.hget(path, field).toCompletableFuture().whenComplete((result, ex) -> {
            if (ex != null) {
                plugin.getLogger().severe("Redis hget failed: " + ex.getMessage());
                ex.printStackTrace();
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> {
                doSomethingWith(result);
            });
        });
    }
}
```

Key points:
- The intermediate `CompletableFuture` variable can be eliminated by chaining
  `.whenComplete()` directly on `.toCompletableFuture()` inside the `try` block.
- Always handle the error case in `whenComplete` - the old `.join()` would throw
  unchecked, losing the error; `whenComplete` requires explicit handling. Log with
  `plugin.getLogger().severe(...)` and `ex.printStackTrace()`.
- `whenComplete` callbacks run on a Lettuce I/O thread - always dispatch back to
  the main thread via `runTask` before touching any Bukkit/server state.
- **Check indentation after refactoring** - removing the `runTaskAsynchronously`
  wrapper reduces nesting by one level; the inner `runTask` lambda body should
  be indented accordingly.
- Remove any imports that become unused after the refactor (`CompletableFuture`,
  `java.util.List`, etc.) - PMD will flag them.

### Adding a multi() future to a pending-saves list

When you need to track a transaction alongside other futures for later blocking wait
(e.g. `blockingWaitForPlayerToSave`), add the `CompletableFuture<TransactionResult>`
returned by `multi()` directly to the list:

```java
// mPendingSaves: Map<UUID, List<CompletableFuture<?>>>
List<CompletableFuture<?>> futures = mPendingSaves.remove(uuid);
futures.add(RedisAPI.multi(conn -> {
    conn.lpush(path, value);
    conn.ltrim(path, 0, limit);
}));
mPendingSaves.put(uuid, futures);

// When waiting (on an async thread):
@SuppressWarnings("unchecked")
CompletableFuture<?>[] arr = futures.toArray(new CompletableFuture[0]);
CompletableFuture.allOf(arr).get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
```
