# Working with Redis in Monumenta Plugins

Redis is the primary data store for Monumenta's multi-shard network. The
`MonumentaRedisSync` plugin (this repo) manages the connection and exposes both
low-level Lettuce access and several high-level convenience APIs. This document
is the one-stop reference for any plugin dev working with Redis.

---

## Table of Contents

1. [Threading model - the golden rule](#1-threading-model-the-golden-rule)
2. [APIs at a glance](#2-apis-at-a-glance)
3. [Low-level access - `RedisAPI`](#3-low-level-access-redisapi)
   - [Regular access - `borrow()` / `borrowStringBytes()`](#31-regular-access-borrow-borrowstringbytes)
   - [Atomic transactions - `multi()`](#32-atomic-transactions-multi)
4. [High-level APIs](#4-high-level-apis)
   - [MonumentaRedisSyncAPI](#41-monumentaredissyncapi)
   - [RBoardAPI](#42-rboardapi)
   - [RemoteDataAPI](#43-remotedataapi)
   - [LeaderboardAPI](#44-leaderboardapi)
   - [BukkitConfigAPI / CommonConfig](#45-bukkitconfigapi-commonconfig)
   - [Advanced / rarely needed APIs](#46-advanced-rarely-needed-apis)
5. [Core async patterns](#5-core-async-patterns)
   - [NEVER - block the main thread](#51-never-block-the-main-thread)
   - [OLD - `runTaskAsynchronously` + `.join()`](#52-old-runtaskasynchronously-join)
   - [PREFERRED - `.whenComplete()` + Bukkit scheduler](#53-preferred-whencomplete-bukkit-scheduler)
   - [Error logging for writes](#54-error-logging-for-writes)
   - [Multiple parallel requests with `CompletableFuture.allOf()`](#55-multiple-parallel-requests-with-completablefutureallof)
   - [Batching on an async thread - `LettuceFutures.awaitAll()`](#56-batching-on-an-async-thread-lettucefuturesawaitall)
6. [Player plugin data - load / save lifecycle](#6-player-plugin-data-load-save-lifecycle)
7. [Key naming conventions](#7-key-naming-conventions)
8. [Quick reference](#8-quick-reference)

---

## 1. Threading model - the golden rule

Minecraft Paper runs a **single main game thread**. Nearly all Bukkit/Paper API
methods are **not thread-safe** and must be called from the main thread.

Redis round-trips can take anywhere from a few milliseconds to over 100 ms.
Blocking the main thread for that long causes visible lag for every player on
the server.

**The rules:**

| Where you are | What you may do |
|---|---|
| Main thread | Call Bukkit API freely. **Never** call `.join()` or `.get()` on a future. |
| Async thread (Bukkit scheduler or future callback) | Safe to block / wait. **Never** call Bukkit API methods except those in the table below. |

**Bukkit / Paper methods that are safe to call from async threads:**

| Method | Notes |
|---|---|
| `player.sendMessage(Component)` | Paper queues the packet; safe from any thread |
| `CommandSender.sendMessage(Component)` | Same |
| `plugin.getLogger().*` | Standard Java logging; always thread-safe |
| `Bukkit.getScheduler().runTask(plugin, ...)` | Just enqueues - does not call Bukkit API itself |
| `Bukkit.getScheduler().runTaskAsynchronously(plugin, ...)` | Same |
| `player.getUniqueId()` | Immutable; safe |
| `player.getName()` | Read-only in practice; safe |

When in doubt, use `Bukkit.getScheduler().runTask(plugin, ...)` to get back
onto the main thread before touching anything else.

`RedisAPI.borrow()` returns a `RedisFuture<T>`, which extends
`CompletableFuture<T>`. Futures complete on a Lettuce I/O thread - neither
the main thread nor a Bukkit async thread. When the callback needs to call
Bukkit API, you **must** schedule back onto the main thread explicitly.

---

## 2. APIs at a glance

| Class | Purpose |
|---|---|
| [`RedisAPI`](plugin/src/main/java/com/playmonumenta/redissync/RedisAPI.java) | Raw Lettuce connection. Entry point for all custom Redis commands. |
| [`MonumentaRedisSyncAPI`](plugin/src/main/java/com/playmonumenta/redissync/MonumentaRedisSyncAPI.java) | Player data, UUID<->name lookup, server transfers, offline data access. |
| [`RBoardAPI`](plugin/src/main/java/com/playmonumenta/redissync/RBoardAPI.java) | Persistent cross-shard hash board (integers by name+key). |
| [`RemoteDataAPI`](plugin/src/main/java/com/playmonumenta/redissync/RemoteDataAPI.java) | Per-player string key/value store, accessible from any shard. |
| [`LeaderboardAPI`](plugin/src/main/java/com/playmonumenta/redissync/LeaderboardAPI.java) | Sorted-set leaderboards. |
| [`BukkitConfigAPI`](plugin/src/main/java/com/playmonumenta/redissync/BukkitConfigAPI.java) | Server configuration values (domain, shard name, etc.). |

---

## 3. Low-level access - `RedisAPI`

### 3.1 Regular access - `borrow()` / `borrowStringBytes()`

All Redis commands must go through a `BorrowedCommands` handle obtained from
`borrow()` or `borrowStringBytes()`. This handle holds a lock that prevents
other threads from interleaving commands on the shared connection while it is
open. The lock is held only for the duration of the try block - in practice
just long enough to enqueue the command, which is near-instant.

```java
// String keys, String values  (most common)
try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
    conn.hget("my:key", "field").whenComplete((value, ex) -> { ... });
}

// String keys, byte[] values  (binary data, e.g. NBT)
try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
    conn.lindex("my:key", 0).whenComplete((bytes, ex) -> { ... });
}
```

`BorrowedCommands<K, V>` extends Lettuce's `AbstractRedisAsyncCommands<K, V>`,
so it exposes the full async command set - `hget`, `hset`, `hmget`, `hgetall`,
`hincrby`, `hdel`, `lpush`, `lindex`, `zadd`, `sadd`, etc. All commands return
`RedisFuture<T>` (which extends `CompletableFuture<T>`).

**Important rules:**
- Always use `borrow()` inside a `try-with-resources` statement.
- **Do not call join() or get() inside this try block!**
- Do not store the `BorrowedCommands` object or pass it outside the try block.
- Do not call `multi()` / `exec()` directly on a borrowed connection - use
  [`RedisAPI.multi()`](#32-atomic-transactions-multi) instead.

> `async()` and `asyncStringBytes()` are still present but **deprecated**.
> Migrate any call sites to `borrow()` / `borrowStringBytes()`.

---

### 3.2 Atomic transactions - `multi()`

Redis `MULTI`/`EXEC` runs a block of commands atomically - either all succeed
or all fail. The shared Lettuce connection is a single TCP pipeline, which
means if multiple threads independently call `multi()` and `exec()`, commands
from different threads can interleave between them, corrupting the transaction.

`RedisAPI.multi()` solves this by holding the connection lock for the entire
block, issuing `MULTI` before the lambda and `EXEC` automatically afterwards.
The connection cannot escape the lambda, and `EXEC` is always called (or
`DISCARD` on exception).

```java
// ✅ Atomic transaction - both writes succeed or both fail
RedisAPI.multi(conn -> {
    conn.hset("my:key", "field1", "value1");
    conn.hset("my:key", "field2", "value2");
}).whenComplete((transactionResult, ex) -> {
    Bukkit.getScheduler().runTask(plugin, () -> {
        if (ex != null) {
            plugin.getLogger().severe("Transaction failed: " + ex.getMessage());
            return;
        }
        // transactionResult contains the result of each command in order
    });
});
```

The `CompletableFuture<TransactionResult>` returned by `multi()` completes when
Redis has executed the transaction. `TransactionResult` contains the individual
command results in the order they were issued, accessible by index.

**Why not just call `conn.multi()` / `conn.exec()` inside `borrow()`?**
You can, but the lock is released when the try block exits - which may happen
before `exec()` is called if your code throws. `RedisAPI.multi()` guarantees
`EXEC` or `DISCARD` is always called before the lock is released.

---

## 4. High-level APIs

### 4.1 `MonumentaRedisSyncAPI`

[Source](plugin/src/main/java/com/playmonumenta/redissync/MonumentaRedisSyncAPI.java)

#### UUID / name resolution

```java
// Thread-safe - reads from an in-memory ConcurrentHashMap, callable from any thread
@Nullable String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
@Nullable UUID   uuid = MonumentaRedisSyncAPI.cachedNameToUuid("PlayerName");

// Async - queries Redis, returns CompletableFuture (completes on Lettuce thread)
CompletableFuture<String> nameFuture = MonumentaRedisSyncAPI.uuidToName(uuid);
CompletableFuture<UUID>   uuidFuture = MonumentaRedisSyncAPI.nameToUUID("PlayerName");
```

Use cached variants whenever possible. A player on the current shard is
guaranteed to be in the cache. A player on a different shard might not be -
handle the `null` case rather than falling back to a Redis lookup.

#### Server transfers

```java
// Must be called from main thread
MonumentaRedisSyncAPI.sendPlayer(player, "targetShardName");
MonumentaRedisSyncAPI.sendPlayer(player, "targetShardName", returnLocation);
```

#### `runOnMainThreadWhenComplete` helper

A convenience wrapper `MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin, future, func)`
exists but the explicit `.whenComplete()` + `Bukkit.getScheduler().runTask()` pattern
(see [section 5.3](#53-preferred-whencomplete-bukkit-scheduler)) is preferred in new code.

---

### 4.2 `RBoardAPI`

[Source](plugin/src/main/java/com/playmonumenta/redissync/RBoardAPI.java)

A cross-shard persistent hash-board. Each **board** is identified by a name
(only `[-_$0-9A-Za-z]` are valid); each board is a map of `String -> long`.
Stored at `{serverDomain}:rboard:{name}`.

```java
// Write / increment
RBoardAPI.set("HuntsBoard", "NextQuarry", 2L);          // CompletableFuture<Long>
RBoardAPI.add("HuntsBoard", "TotalHunts", 1L);          // atomic increment

// Read single value with default
RBoardAPI.getAsLong("HuntsBoard", "NextQuarry", 0L)
    .whenComplete((val, ex) -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                plugin.getLogger().severe("RBoard read failed: " + ex.getMessage());
                return;
            }
            applyNextQuarry(val.intValue());
        });
    });

// Read multiple keys at once
RBoardAPI.get("HuntsBoard", "NextQuarry", "IsBaited")
    .whenComplete((map, ex) -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) { /* log */ return; }
            long quarry = Long.parseLong(map.getOrDefault("NextQuarry", "0"));
            boolean baited = Long.parseLong(map.getOrDefault("IsBaited", "0")) > 0;
        });
    });

// Atomic read-and-delete
RBoardAPI.getAndReset("HuntsBoard", "TriggerKey");

// Delete entire board
RBoardAPI.resetAll("HuntsBoard");
```

---

### 4.3 `RemoteDataAPI`

[Source](plugin/src/main/java/com/playmonumenta/redissync/RemoteDataAPI.java)

Per-player string key/value store visible from any shard.
Stored at `{serverDomain}:playerdata:{uuid}:remotedata`.

All methods dispatch immediately (suitable from main or async thread) but
**complete on a Lettuce thread** - schedule back to main if you need Bukkit API.

```java
// Read one key
RemoteDataAPI.get(uuid, "myKey")
    .whenComplete((value, ex) -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) { /* log */ return; }
            if (value != null) { /* use value */ }
        });
    });

// Write
RemoteDataAPI.set(uuid, "myKey", "myValue")
    .exceptionally(ex -> {
        plugin.getLogger().severe("RemoteData set failed: " + ex.getMessage());
        return false;
    });

// Atomic increment
RemoteDataAPI.increment(uuid, "killCount", 1);

// Read multiple keys
RemoteDataAPI.getMulti(uuid, "key1", "key2")
    .whenComplete((map, ex) -> { /* ... */ });

// Delete
RemoteDataAPI.del(uuid, "myKey");
```

---

### 4.4 `LeaderboardAPI`

[Source](plugin/src/main/java/com/playmonumenta/redissync/LeaderboardAPI.java)

Redis sorted-set leaderboard. One sorted set per `objective` name.
Stored at `{serverDomain}:leaderboard:{objective}`.

```java
// Fire-and-forget update (no return value needed)
LeaderboardAPI.updateAsync("TopKills", player.getName(), killCount);

// Read top 10 entries (0-indexed, descending)
LeaderboardAPI.get("TopKills", 0, 9, false)
    .whenComplete((entries, ex) -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) { /* log */ return; }
            // entries: LinkedHashMap<name, score>, ordered by rank
            entries.forEach((name, score) ->
                player.sendMessage(name + ": " + score));
        });
    });
```

---

### 4.5 `BukkitConfigAPI` / `CommonConfig`

[Source](plugin/src/main/java/com/playmonumenta/redissync/BukkitConfigAPI.java)

```java
// The domain groups servers sharing the same player data (e.g. "monumenta")
String domain = CommonConfig.getServerDomain();   // e.g. "monumenta"
String shard  = CommonConfig.getShardName();      // e.g. "valley"
```

**Always** prefix custom Redis keys with the server domain so different
deployments don't collide. See [section 7](#7-key-naming-conventions).

---

### 4.6 Advanced / rarely needed APIs

These are low-use APIs exposed by `MonumentaRedisSyncAPI`. You should not need
them in typical plugin development.

#### Player scores (online or offline)

Fetches a player's full scoreboard as a `Map<objective, score>`. If the player
is online it reads from the live scoreboard (main thread); if offline it loads
from the most recent Redis save (async).

```java
// The returned future always completes on the MAIN thread
CompletableFuture<Map<String, Integer>> scoresFuture =
    MonumentaRedisSyncAPI.getPlayerScores(uuid);

scoresFuture.whenComplete((scores, ex) -> {
    // Already on main thread - no extra scheduling needed
    if (ex != null) {
        plugin.getLogger().severe("Failed to get scores: " + ex.getMessage());
        return;
    }
    int kills = scores.getOrDefault("Kills", 0);
});
```

#### Offline player data (full save blob)

Loads the complete save data (inventory, advancements, scores, plugin data) for
a player who is **not** currently online on this shard.

```java
// Throws if the player is online
CompletableFuture<MonumentaRedisSyncAPI.RedisPlayerData> future =
    MonumentaRedisSyncAPI.getOfflinePlayerData(uuid);

future.whenComplete((data, ex) -> {
    Bukkit.getScheduler().runTask(plugin, () -> {
        if (ex != null || data == null) {
            plugin.getLogger().severe("Could not load offline data: " +
                (ex != null ? ex.getMessage() : "null result"));
            return;
        }
        // use data.getPluginData(), data.getScores(), etc.
    });
});
```

---

## 5. Core async patterns

### 5.1 NEVER - block the main thread

```java
// ❌ WRONG - blocks main thread, causes server lag - even if the caller is async!
public void myMethod() {
    String value;
    try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
        value = conn.hget("my:key", "field")
            .toCompletableFuture()
            .join(); // NEVER call join() / get() on the main thread
    }
}
```

---

### 5.2 PREFERRED - `.whenComplete()` + Bukkit scheduler

Issue the Redis call from any thread, attach a callback that schedules onto
the main thread. No threads are blocked, errors are handled explicitly.

```java
// PREFERRED pattern
try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
    conn.hget(stashPath, playerUuid + "-history")
        .whenComplete((history, ex) -> {
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (ex != null) {
                    plugin.getLogger().severe("Redis error: " + ex.getMessage());
                    ex.printStackTrace();
                    return;
                }
                if (history == null) { /* handle missing data */ return; }
                player.sendMessage(history);
            });
        });
}
```

The `borrow()` lock is released as soon as the try block exits - immediately
after the command is enqueued, not when the future completes. The
`.whenComplete()` callback runs later on a Lettuce I/O thread, and the inner
`Bukkit.getScheduler().runTask()` schedules the result-handling onto the main
thread.

**Rule of thumb:** every `whenComplete` that touches Bukkit objects (players,
worlds, entities, scoreboards) must have a `Bukkit.getScheduler().runTask()`
wrapper inside it.

---

### 5.4 Error logging for writes

Writes that don't need a result should still log failures. `.exceptionally()` is
the most concise way - it runs similarly to `.whenComplete()` but only when there
is an exception.

```java
// ✅ Write with error logging
try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
    conn.hset("my:key", "field", "value")
        .exceptionally(ex -> {
            plugin.getLogger().severe("Redis hset failed: " + ex.getMessage());
            return null; // return value is required
        });
}
```

Alternatively, use `whenComplete` when you want to handle both success and
failure (e.g. to update local state after a confirmed write):

```java
RemoteDataAPI.set(uuid, "questFlag", "done")
    .whenComplete((success, ex) -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                plugin.getLogger().severe("Failed to set quest flag: " + ex.getMessage());
                return;
            }
            // confirmed write succeeded - safe to update local state
            mLocalFlags.put(uuid, true);
        });
    });
```

> **Note:** Java's `CompletableFuture` has no `.whenCompleteExceptionally()`.
> `.exceptionally(ex -> { ...; return null; })` is the standard concise form
> for "log the error, ignore the result."

---

### 5.5 Multiple parallel requests with `CompletableFuture.allOf()`

When you need results from several independent Redis keys, fire all requests
at once and pass the individual `CompletableFuture` references to
`CompletableFuture.allOf()`. When the `allOf` callback fires, every constituent
future is **guaranteed to be complete**, so calling `.join()` on them is
non-blocking. Error checking can be done in one place in the outer callback.

```java
// ✅ Fan-out pattern - all futures dispatched simultaneously
CompletableFuture<Long> quarryFuture = RBoardAPI.getAsLong("HuntsBoard", "NextQuarry", 0L);
CompletableFuture<Long> baitedFuture = RBoardAPI.getAsLong("HuntsBoard", "IsBaited", 0L);

CompletableFuture.allOf(quarryFuture, baitedFuture)
    .whenComplete((unused, ex) -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                // At least one future failed - ex is the first failure encountered
                plugin.getLogger().severe("Failed to refresh hunts state: " + ex.getMessage());
                return;
            }
            // All futures succeeded. .join() is non-blocking here - they are already done.
            long nextQuarry = quarryFuture.join();
            boolean isBaited = baitedFuture.join() > 0;
            applyHuntsState(nextQuarry, isBaited);
        });
    });
```

> Real usage: see `HuntsManager.refresh()` in `monumenta-plugins`.

**How `allOf` handles errors:** if any constituent future fails, `allOf()`
completes exceptionally **immediately** with that failure, without waiting for
the remaining futures. `ex` will be non-null and the early `return` ensures
`.join()` is never called - those calls cannot throw. If both futures fail,
`allOf()` captures only the first exception; the second is silently dropped.

---

### 5.6 Batching on an async thread - `LettuceFutures.awaitAll()`

Some code already runs on an async thread (e.g. inside
`waitForPlayerToSaveThenAsync`). In that context it is acceptable to block the
thread, but **fire all futures first**, then wait for them together rather than
calling `.get()` sequentially.

**You should avoid this pattern unless others really do not work for your use case.**

```java
// ⚠ Acceptable (already on async thread) but fire-then-await, not sequential get()
DataEventListener.waitForPlayerToSaveThenAsync(player, () -> {
    // Fire all reads at once - each borrow() is very brief
    RedisFuture<byte[]> dataFuture;
    RedisFuture<String> scoresFuture;
    RedisFuture<String> pluginFuture;

    try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
        dataFuture = conn.hget(stashPath, name + "-data");
    }
    try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
        scoresFuture = conn.hget(stashPath, name + "-scores");
        pluginFuture = conn.hget(stashPath, name + "-plugins");
    }

    // Wait for all of them together (one network round-trip worth of latency)
    try {
        if (!LettuceFutures.awaitAll(10, TimeUnit.SECONDS,
                dataFuture, scoresFuture, pluginFuture)) {
            plugin.getLogger().severe("Timeout waiting for stash data");
            return;
        }
    } catch (InterruptedException ex) {
        plugin.getLogger().severe("Interrupted waiting for stash data: " + ex.getMessage());
        return;
    }

    // Futures are resolved - .get() does not block here
    byte[] data    = dataFuture.get();
    String scores  = scoresFuture.get();
    String plugins = pluginFuture.get();

    if (data == null || scores == null || plugins == null) {
        /* handle missing data */
        return;
    }

    // Do further async work, then schedule main-thread work when needed
    Bukkit.getScheduler().runTask(plugin, () -> player.kick(Component.text("Data loaded")));
});
```

> Real usage: `MonumentaRedisSyncAPI.stashGet()` / `playerRollback()`.

---

## 6. Player plugin data - load / save lifecycle

The most common pattern for plugins that need persistent per-player data.
Data is stored as a `JsonObject` inside the player's plugin data blob, keyed
by a unique plugin identifier string.

```java
public class MyPluginListener implements Listener {

    // Must be unique across all plugins - use your plugin name
    private static final String KEY = "MyPlugin";

    private final Map<UUID, MyData> mPlayerData = new HashMap<>();
    private final Plugin mPlugin;

    public MyPluginListener(Plugin plugin) {
        mPlugin = plugin;
    }

    /**
     * Load data on join. Data is retrieved from an in-memory cache - no Redis
     * round-trip occurs here. Must run at MONITOR priority (after redis-sync
     * has populated the cache at lower priorities).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        JsonObject data = MonumentaRedisSyncAPI.getPlayerPluginData(player.getUniqueId(), KEY);
        if (data != null) {
            mPlayerData.put(player.getUniqueId(), MyData.fromJson(data));
        } else {
            mPlayerData.put(player.getUniqueId(), new MyData());
        }
    }

    /**
     * Serialize data into the save event. redis-sync calls this when it is
     * about to persist player data (on quit, autosave, transfer, etc.).
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerSave(PlayerSaveEvent event) {
        MyData data = mPlayerData.get(event.getPlayer().getUniqueId());
        if (data != null) {
            event.setPluginData(KEY, data.toJson());
        }
    }

    /**
     * Clean up local cache after quit. Use a short delay to avoid a race
     * where the save event fires after quit but before cleanup.
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onPlayerQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
            if (!event.getPlayer().isOnline()) {
                mPlayerData.remove(event.getPlayer().getUniqueId());
            }
        }, 2);
    }

    public @Nullable MyData getData(Player player) {
        return mPlayerData.get(player.getUniqueId());
    }
}
```

See the full working example at
[`example/src/main/java/.../ExampleServerListener.java`](example/src/main/java/com/playmonumenta/redissync/example/ExampleServerListener.java).

---

## 7. Key naming conventions

All keys must start with the server domain to prevent cross-deployment
collisions:

```
{serverDomain}:{subsystem}:{...}
```

Use `CommonConfig.getServerDomain()` (or `BukkitConfigAPI.getServerDomain()`)
to get the configured domain (e.g. `"monumenta"`).

| Built-in key patterns (for reference) | Meaning |
|---|---|
| `{domain}:playerdata:{uuid}:plugins` | Player plugin data blob |
| `{domain}:playerdata:{uuid}:scores` | Player scoreboard data |
| `{domain}:playerdata:{uuid}:data` | Player NBT data (binary) |
| `{domain}:playerdata:{uuid}:remotedata` | RemoteDataAPI store |
| `{domain}:rboard:{name}` | RBoardAPI hash |
| `{domain}:leaderboard:{objective}` | LeaderboardAPI sorted set |
| `{domain}:stash` | Player stash hash |

For custom plugin keys, choose a descriptive subsystem name:

```java
private static final String REDIS_PATH =
    BukkitConfigAPI.getServerDomain() + ":myplugin:mydata";
```

---

## 8. Quick reference

```java
// -- Borrow the connection (String/String) ------------------------------------
try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
    conn.hget(key, field).whenComplete(...);
    conn.hset(key, field, value).exceptionally(...);
}

// -- Borrow the connection (String/byte[]) ------------------------------------
try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
    conn.lpush(key, byteArray).whenComplete(...);
}

// -- Common hash operations (all return RedisFuture / CompletableFuture) ------
conn.hget(key, field)                     // read one field
conn.hmget(key, field1, field2, ...)      // read multiple fields
conn.hgetall(key)                         // read entire hash
conn.hset(key, field, value)              // write one field
conn.hset(key, map)                       // write multiple fields at once
conn.hincrby(key, field, delta)           // atomic increment
conn.hdel(key, field1, ...)               // delete fields
conn.del(key)                             // delete entire key

// -- Atomic transaction (MULTI/EXEC) ------------------------------------------
RedisAPI.multi(conn -> {
    conn.hset(key, field1, value1);       // enqueued
    conn.hset(key, field2, value2);       // enqueued
}).whenComplete((transactionResult, ex) -> {
    Bukkit.getScheduler().runTask(plugin, () -> {
        if (ex != null) { /* log/handle */ return; }
        // transactionResult.get(0), .get(1) - results by command index
    });
});

// -- Attach callback, schedule to main thread ---------------------------------
future.whenComplete((result, ex) -> {
    Bukkit.getScheduler().runTask(plugin, () -> {
        if (ex != null) { /* log/handle */ return; }
        // use result on main thread
    });
});

// -- Error-only logging for fire-and-forget writes ----------------------------
future.exceptionally(ex -> {
    plugin.getLogger().severe("Redis write failed: " + ex.getMessage());
    return null;
});

// -- Wait for multiple futures in parallel ------------------------------------
CompletableFuture<T> futureA = ...;
CompletableFuture<T> futureB = ...;
CompletableFuture.allOf(futureA, futureB)
    .whenComplete((unused, ex) -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) { /* log */ return; }
            // .join() is non-blocking - futures are already complete
            T a = futureA.join();
            T b = futureB.join();
        });
    });

// -- Wait for multiple futures on an async thread ----------------------------
LettuceFutures.awaitAll(10, TimeUnit.SECONDS, redisFuture1, redisFuture2);
// futures are now resolved; .get() won't block

// -- High-level APIs ----------------------------------------------------------
RBoardAPI.getAsLong(boardName, key, defaultValue)   // CompletableFuture<Long>
RBoardAPI.set(boardName, key, value)                // CompletableFuture<Long>
RBoardAPI.add(boardName, key, delta)                // atomic increment

RemoteDataAPI.get(uuid, key)                        // CompletableFuture<String>
RemoteDataAPI.set(uuid, key, value)                 // CompletableFuture<Boolean>
RemoteDataAPI.increment(uuid, key, delta)           // CompletableFuture<Long>

LeaderboardAPI.get(objective, start, stop, asc)     // CompletableFuture<Map<String,Integer>>
LeaderboardAPI.updateAsync(objective, name, value)  // fire-and-forget

MonumentaRedisSyncAPI.cachedUuidToName(uuid)        // @Nullable String, instant, thread-safe
MonumentaRedisSyncAPI.cachedNameToUuid(name)        // @Nullable UUID, instant, thread-safe
MonumentaRedisSyncAPI.uuidToName(uuid)              // CompletableFuture<String>
MonumentaRedisSyncAPI.getPlayerPluginData(uuid, id) // @Nullable JsonObject, instant (main thread only)
MonumentaRedisSyncAPI.getPlayerScores(uuid)         // CompletableFuture, completes on main thread
MonumentaRedisSyncAPI.getOfflinePlayerData(uuid)    // CompletableFuture<RedisPlayerData>
MonumentaRedisSyncAPI.sendPlayer(player, shard)     // transfer player (main thread)
```
