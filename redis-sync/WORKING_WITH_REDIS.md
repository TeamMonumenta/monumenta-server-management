# Working with Redis in Monumenta Plugins

Redis is the primary data store for Monumenta's multi-shard network. The
`MonumentaRedisSync` plugin (this repo) manages the connection and exposes both
low-level Lettuce access and several high-level convenience APIs. This document
is the one-stop reference for any plugin dev working with Redis.

---

## Table of Contents

1. [Threading model — the golden rule](#1-threading-model--the-golden-rule)
2. [APIs at a glance](#2-apis-at-a-glance)
3. [Low-level access — `RedisAPI`](#3-low-level-access--redisapi)
4. [High-level APIs](#4-high-level-apis)
   - [MonumentaRedisSyncAPI](#41-monumentaredissyncapi)
   - [RBoardAPI](#42-rboardapi)
   - [RemoteDataAPI](#43-remotedataapi)
   - [LeaderboardAPI](#44-leaderboardapi)
   - [BukkitConfigAPI / CommonConfig](#45-bukkitconfigapi--commonconfig)
   - [Advanced / rarely needed APIs](#46-advanced--rarely-needed-apis)
5. [Core async patterns](#5-core-async-patterns)
   - [NEVER — block the main thread](#51-never--block-the-main-thread)
   - [OLD — `runTaskAsynchronously` + `.join()`](#52-old--runtaskasynchronously--join)
   - [PREFERRED — `.whenComplete()` + Bukkit scheduler](#53-preferred--whencomplete--bukkit-scheduler)
   - [Error logging for writes](#54-error-logging-for-writes)
   - [Multiple parallel requests with `CompletableFuture.allOf()`](#55-multiple-parallel-requests-with-completablefutureallof)
   - [Batching on an async thread — `LettuceFutures.awaitAll()`](#56-batching-on-an-async-thread--lettucefuturesawaitall)
6. [Player plugin data — load / save lifecycle](#6-player-plugin-data--load--save-lifecycle)
7. [Key naming conventions](#7-key-naming-conventions)
8. [Quick reference](#8-quick-reference)

---

## 1. Threading model — the golden rule

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
| `Bukkit.getScheduler().runTask(plugin, ...)` | Just enqueues — does not call Bukkit API itself |
| `Bukkit.getScheduler().runTaskAsynchronously(plugin, ...)` | Same |
| `player.getUniqueId()` | Immutable; safe |
| `player.getName()` | Read-only in practice; safe |

When in doubt, use `Bukkit.getScheduler().runTask(plugin, ...)` to get back
onto the main thread before touching anything else.

The Lettuce library (`RedisAPI.getInstance().async()`) returns
`RedisFuture<T>`, which extends `CompletableFuture<T>`. Futures complete on
a Lettuce I/O thread — neither the main thread nor a Bukkit async thread.
When the callback needs to call Bukkit API, you **must** schedule back onto
the main thread explicitly.

---

## 2. APIs at a glance

| Class | Purpose |
|---|---|
| [`RedisAPI`](plugin/src/main/java/com/playmonumenta/redissync/RedisAPI.java) | Raw Lettuce connection. Entry point for all custom Redis commands. |
| [`MonumentaRedisSyncAPI`](plugin/src/main/java/com/playmonumenta/redissync/MonumentaRedisSyncAPI.java) | Player data, UUID↔name lookup, server transfers, offline data access. |
| [`RBoardAPI`](plugin/src/main/java/com/playmonumenta/redissync/RBoardAPI.java) | Persistent cross-shard hash board (integers by name+key). |
| [`RemoteDataAPI`](plugin/src/main/java/com/playmonumenta/redissync/RemoteDataAPI.java) | Per-player string key/value store, accessible from any shard. |
| [`LeaderboardAPI`](plugin/src/main/java/com/playmonumenta/redissync/LeaderboardAPI.java) | Sorted-set leaderboards. |
| [`BukkitConfigAPI`](plugin/src/main/java/com/playmonumenta/redissync/BukkitConfigAPI.java) | Server configuration values (domain, shard name, etc.). |

---

## 3. Low-level access — `RedisAPI`

```java
// Always-valid singleton once MonumentaRedisSync is loaded
RedisAPI api = RedisAPI.getInstance();

// String keys, String values  (most common)
RedisAsyncCommands<String, String> cmds = api.async();

// String keys, byte[] values  (binary data, e.g. NBT)
RedisAsyncCommands<String, byte[]> cmds = api.asyncStringBytes();
```

---

## 4. High-level APIs

### 4.1 `MonumentaRedisSyncAPI`

[Source](plugin/src/main/java/com/playmonumenta/redissync/MonumentaRedisSyncAPI.java)

#### UUID / name resolution

```java
// Thread-safe — reads from an in-memory ConcurrentHashMap, callable from any thread
@Nullable String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
@Nullable UUID   uuid = MonumentaRedisSyncAPI.cachedNameToUuid("PlayerName");

// Async — queries Redis, returns CompletableFuture (completes on Lettuce thread)
CompletableFuture<String> nameFuture = MonumentaRedisSyncAPI.uuidToName(uuid);
CompletableFuture<UUID>   uuidFuture = MonumentaRedisSyncAPI.nameToUUID("PlayerName");
```

Use cached variants whenever possible. A player on the current shard is
guaranteed to be in the cache. A player on a different shard might not be —
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
(see [section 5.3](#53-preferred--whencomplete--bukkit-scheduler)) is preferred in new code.

---

### 4.2 `RBoardAPI`

[Source](plugin/src/main/java/com/playmonumenta/redissync/RBoardAPI.java)

A cross-shard persistent hash-board. Each **board** is identified by a name
(only `[-_$0-9A-Za-z]` are valid); each board is a map of `String → long`.
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
**complete on a Lettuce thread** — schedule back to main if you need Bukkit API.

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
    // Already on main thread — no extra scheduling needed
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

### 5.1 NEVER — block the main thread

```java
// ❌ WRONG — blocks main thread, causes server lag
@EventHandler
public void onPlayerJoin(PlayerJoinEvent event) {
    String value = RedisAPI.getInstance().async()
        .hget("my:key", "field")
        .toCompletableFuture()
        .join(); // NEVER call join() / get() on the main thread
}
```

---

### 5.2 OLD — `runTaskAsynchronously` + `.join()`

You will see this pattern in older code. It is **safe** (`.join()` is only
called on an async thread, not the main thread) but suboptimal because it
occupies a Bukkit thread for the entire duration of the blocking wait.

```java
// OLD pattern — acceptable but avoid in new code
Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
    String history = RedisAPI.getInstance().async()
        .hget(stashPath, playerUuid + "-history")
        .toCompletableFuture()
        .join();                         // blocks this async thread

    Bukkit.getScheduler().runTask(plugin, () -> {
        if (history == null) { /* ... */ return; }
        player.sendMessage(history);
    });
});
```

---

### 5.3 PREFERRED — `.whenComplete()` + Bukkit scheduler

Issue the Redis call from any thread, attach a callback that schedules onto
the main thread. No threads are blocked, errors are handled explicitly.

```java
// PREFERRED pattern
RedisAPI.getInstance().async()
    .hget(stashPath, playerUuid + "-history")
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
```

The callback lambda passed to `whenComplete` runs on a Lettuce I/O thread.
The `Bukkit.getScheduler().runTask(plugin, ...)` call inside it schedules the
inner lambda to run on the main thread on the next tick.

**Rule of thumb:** every `whenComplete` that touches Bukkit objects (players,
worlds, entities, scoreboards) must have a `Bukkit.getScheduler().runTask()`
wrapper inside it.

---

### 5.4 Error logging for writes

Writes that don't need a result should still log failures. `.exceptionally()` is
the most concise way — it runs similarly to .whenComplete() but only when there
is an exception.

```java
// ✅ Write with error logging
RedisAPI.getInstance().async()
    .hset("my:key", "field", "value")
    .exceptionally(ex -> {
        plugin.getLogger().severe("Redis hset failed: " + ex.getMessage());
        return null; // return value is required
    });
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
            // confirmed write succeeded — safe to update local state
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
// ✅ Fan-out pattern — all futures dispatched simultaneously
CompletableFuture<Long> quarryFuture = RBoardAPI.getAsLong("HuntsBoard", "NextQuarry", 0L);
CompletableFuture<Long> baitedFuture = RBoardAPI.getAsLong("HuntsBoard", "IsBaited", 0L);

CompletableFuture.allOf(quarryFuture, baitedFuture)
    .whenComplete((unused, ex) -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) {
                // At least one future failed — ex is the first failure encountered
                plugin.getLogger().severe("Failed to refresh hunts state: " + ex.getMessage());
                return;
            }
            // All futures succeeded. .join() is non-blocking here — they are already done.
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
`.join()` is never called — those calls cannot throw. If both futures fail,
`allOf()` captures only the first exception; the second is silently dropped.

---

### 5.6 Batching on an async thread — `LettuceFutures.awaitAll()`

Some code already runs on an async thread (e.g. inside
`waitForPlayerToSaveThenAsync`). In that context it is acceptable to block the
thread, but **fire all futures first**, then wait for them together rather than
calling `.get()` sequentially.

**You should avoid this pattern unless others really do not work for your use case.**

```java
// ⚠ Acceptable (already on async thread) but fire-then-await, not sequential get()
DataEventListener.waitForPlayerToSaveThenAsync(player, () -> {
    RedisAPI api = RedisAPI.getInstance();

    // Fire all reads at once
    RedisFuture<byte[]>  dataFuture    = api.asyncStringBytes().hget(stashPath, name + "-data");
    RedisFuture<String>  scoresFuture  = api.async().hget(stashPath, name + "-scores");
    RedisFuture<String>  pluginFuture  = api.async().hget(stashPath, name + "-plugins");

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

    // Futures are resolved — .get() does not block here
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

## 6. Player plugin data — load / save lifecycle

The most common pattern for plugins that need persistent per-player data.
Data is stored as a `JsonObject` inside the player's plugin data blob, keyed
by a unique plugin identifier string.

```java
public class MyPluginListener implements Listener {

    // Must be unique across all plugins — use your plugin name
    private static final String KEY = "MyPlugin";

    private final Map<UUID, MyData> mPlayerData = new HashMap<>();
    private final Plugin mPlugin;

    public MyPluginListener(Plugin plugin) {
        mPlugin = plugin;
    }

    /**
     * Load data on join. Data is retrieved from an in-memory cache — no Redis
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
[`example/src/main/java/…/ExampleServerListener.java`](example/src/main/java/com/playmonumenta/redissync/example/ExampleServerListener.java).

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
// ── Get the low-level async connection ──────────────────────────────────────
RedisAsyncCommands<String, String> cmds = RedisAPI.getInstance().async();
RedisAsyncCommands<String, byte[]> bin  = RedisAPI.getInstance().asyncStringBytes();

// ── Common hash operations (all return CompletableFuture / RedisFuture) ─────
cmds.hget(key, field)                     // read one field
cmds.hmget(key, field1, field2, ...)      // read multiple fields
cmds.hgetall(key)                         // read entire hash
cmds.hset(key, field, value)              // write one field
cmds.hset(key, map)                       // write multiple fields at once
cmds.hincrby(key, field, delta)           // atomic increment
cmds.hdel(key, field1, ...)               // delete fields
cmds.del(key)                             // delete entire key

// ── Transaction (atomic multi-command block) ─────────────────────────────────
cmds.multi();
cmds.hset(...);     // enqueued
cmds.hset(...);     // enqueued
cmds.exec();        // fires all; returns CompletableFuture<TransactionResult>

// ── Attach callback, schedule to main thread ─────────────────────────────────
future.whenComplete((result, ex) -> {
    Bukkit.getScheduler().runTask(plugin, () -> {
        if (ex != null) { /* log/handle */ return; }
        // use result on main thread
    });
});

// ── Error-only logging for fire-and-forget writes ────────────────────────────
future.exceptionally(ex -> {
    plugin.getLogger().severe("Redis write failed: " + ex.getMessage());
    return null;
});

// ── Wait for multiple futures in parallel ────────────────────────────────────
CompletableFuture<T> futureA = ...;
CompletableFuture<T> futureB = ...;
CompletableFuture.allOf(futureA, futureB)
    .whenComplete((unused, ex) -> {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (ex != null) { /* log */ return; }
            // .join() is non-blocking — futures are already complete
            T a = futureA.join();
            T b = futureB.join();
        });
    });

// ── Wait for multiple futures on an async thread ────────────────────────────
LettuceFutures.awaitAll(10, TimeUnit.SECONDS, redisFuture1, redisFuture2);
// futures are now resolved; .get() won't block

// ── High-level APIs ──────────────────────────────────────────────────────────
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
