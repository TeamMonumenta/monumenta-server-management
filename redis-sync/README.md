# Monumenta Redis Sync

## Introduction

The purpose of this plugin is to manage player data for a large number of
minecraft servers by storing it in a Redis database.

When a user logs in, instead of reading their data from the world, it instead
loads it from Redis. And, likewise, when the player logs out or their data is
saved, that data get stored into Redis.

This plugin also provides commands to transfer players between servers on a
bungeecord network, allowing a game world to be broken up across many minecraft
servers for performance or abstraction purposes.

Player data is versioned, by default saving the previous 20 versions. This
allows you to roll back a player to a previous save point. Additionally, there
is a "stash" mechanism that lets you temporarily save your current player data
and easily return to it later - or load a different player's stashed data as if
it was your own.

## Current status

This plugin is functional and is being used in production on the Monumenta
server network spanning 20+ servers and hundreds of players. It is still a
little rough around the edges, missing some nice-to-have features.

Here's a list of currently supported things:

- Save playerdata, advancements, and scoreboard data to/from Redis
- Function & command block accessible /transferserver command to move players
  between servers on the same bungeecord network
- /playerhistory to inspect a player's save history
- /playerrollback to roll them back to a previous version
- /stash [put|get|info] to temporarily save and load your playerdata
- A plugin API to use those features directly from other plugins
- A player transfer event, which allows plugins to manipulate player data prior
  to a player transferring servers
- Support for multiple different "domains". Each server in the same domain will
  use the same player data. No data transfer is possible between domains
  (useful if you have different types of servers)
- Exposes the Redis API (via Lettuce) for access by other plugins
- Also loads on Bungeecord, though currently does nothing there besides provide
  access to the Lettuce API
- An API to allow plugins to save additional information about the player for
  transfer
- An API and in-game commands to access a global redis scoreboard (the 'rboard')
  which lets you access and share data from different minecraft servers
  concurrently. This is particularly useful for data that needs to be accessed
  from both bungeecord and minecraft servers simultaneously.

Planned features:

- Automatic config file creation
- Storage of player stats data

## Example Dependant Plugin

See the [example](example) directory for a complete example on how you might
use this plugin as a dependency of one of your plugins, either for Paper or
bungeecord.

## Dependencies

This plugin requires a Paper-based minecraft server, compiled with specific
patches to enable high-performance access to player data save/load events. This
is a significantly more cumbersome requirement than most plugins. The `1.20.4`
version loads these patches at runtime with the Mixin bytecode transformation
framework, which requires using a [fabric mod loader for paper](https://github.com/Floweynt/fabric-paper-modloader)
as the server software. The mixin implementation is [here](https://github.com/TeamMonumenta/monumenta-mixins).

A fork of paper with these patches (and others) can be found here:
https://github.com/TeamMonumenta/monumenta-paperfork

If you are interested in getting this working / trying it out yourself, join
the Monumenta Discord (https://discord.gg/eep9qcu) and message @Combustible.

This plugin also requires CommandAPI 6.0. Other versions might also work, worth
testing. If you try with a version that doesn't work (log errors, commands
don't work, etc.) it's not going to damage your player data.

The current version of this plugin requires Minecraft 1.18.2. Other versions
could be easily supported, just ask.

## Maven dependency

```xml

<repository>
	<id>monumenta</id>
	<name>Monumenta Maven Repo</name>
	<url>https://maven.playmonumenta.com/releases</url>
</repository>
<dependencies>
<dependency>
	<groupId>com.playmonumenta</groupId>
	<artifactId>redissync</artifactId>
	<version>4.1</version>
	<scope>provided</scope>
</dependency>
</dependencies>
```

Gradle (kotlin):

```kts
maven {
	name = "monumenta"
	url = uri("https://maven.playmonumenta.com/releases")
}

dependencies {
	compileOnly("com.playmonumenta:redissync:4.1")
}
```

Gradle (groovy):

```groovy
maven {
	name "monumenta"
	url "https://maven.playmonumenta.com/releases"
}

dependencies {
	compileOnly "com.playmonumenta:redissync:4.1"
}
```

## How player data is saved and loaded

Player data lives in redis as per-player history lists, newest first: playerdata (NBT),
advancements, scores, plugin data, content, and a history line naming who saved it. Each save
pushes one entry onto every list. Position and world are kept per shard in a hash. How the plugin
tracks a player and decides when to load and save is described in
[ARCHITECTURE.md](ARCHITECTURE.md); this section is what other plugins can rely on, on one shard.

- **A login always loads the newest save.** If the player's previous connection here has not
  closed, or its last save has not committed, the login waits for it (up to `TIMEOUT_SECONDS`)
  and is refused if it does not finish. If the old connection is still open, the newest login
  wins: the old one is kicked, or, if still loading, never saved and kicked once it joins.
- **Saves are made only from the tick after PlayerJoinEvent**, so other plugins can load their
  own state in their join handlers. No save is made while the player is transferring, after their
  load failed, or after a data handoff replaced their data.
- **Only the `Player` object of the current connection counts.** A plugin holding one from an
  earlier connection cannot save its state over the current one, and `sendPlayer` throws for it.
- **Fake players** (NPC plugins) load as usual but are never saved, since they are not logins.
- **A failed load** refuses or kicks the player, and nothing they do is saved. They can log in
  again once whatever broke is fixed. A player online without a session, such as after a plugin
  reload, is kicked, since nothing of theirs would be saved.
- **Without a session**, `getPlayerPluginData` and `getPlayerShardData` return null,
  `getPlayerContentData` returns a fresh empty object that is not kept, and `savePlayerContent` is
  ignored with a warning.
- **`sendPlayer`** saves and locks the player, and tells the proxy to move them only once that
  save has committed. The lock times out after 10 seconds, or 25 ticks if network relay does not
  list the target as online. A transfer that times out, or whose save is too slow to commit,
  unlocks the player and fires PlayerTransferFailEvent.
- **Stash get, rollback and loadFromPlayer** save and lock the player, wait for the save, write
  the replacement and kick the player to rejoin with it. If the replacement is missing or cannot
  be read, the player is unlocked, and the player (or the moderator, for a rollback) is told.
  Once it is being written, the player is never saved again and is kicked until they leave, even
  if another plugin cancels the kick.
- **Offline edits** (`getOfflinePlayerData`, then `saveOfflinePlayerData`) are refused, returning
  false, if the player is on this shard, logging in, just leaving, or has saved since the data
  was read. A login waits for an offline edit in flight.
- **Shutdown** waits for every save and other player data write in flight before closing redis.

### Known limitations

- **Other shards.** All of the above is within one shard. Nothing stops a player reaching
  another shard by some other route (a proxy `/server`, or a kick redirect) while their last
  save here is still committing. Closing that would need a per-player lock in redis.
- **Two connections for one account at once.** Velocity never gives one backend two live
  connections for a player, but a proxy bug could. If two logins pass pre-login together, the
  second to load is refused. If one drops while the other is still configuring, its close
  cannot be told apart from the other's (the event carries only a UUID), so the other is refused
  too. The worst case is a refused login, never unsaved play. Telling connections apart for
  certain would need a monumenta-mixins hook giving the close event a connection identity.
- **A dead connection stuck in configuration** cannot be kicked through the Bukkit API. A
  reconnect waits up to `TIMEOUT_SECONDS` for Paper to time it out, and is refused if it has
  not.
- **Changes made in the join tick** are not saved by a transfer or handoff started in that same
  tick, because saves open on the next tick. Rollback accounts for its own skipped save.
- **Failed saves are not retried.** A reconnect after one loads the save before it. A MULTI that
  may have partly applied cannot safely be retried.
- **A crash, or the shutdown wait running out,** loses whatever had not committed. A save is two
  transactions on two connections, so it can be torn between them. Making each save one
  transaction on one connection would close that, at the cost of encoding the string data as
  bytes.
- **stashPut** stashes the newest save once its own save has committed. If the player saves
  again before then, the stash is of that later save.
- **Shutdown without monumenta-mixins.** Vanilla Paper disables plugins before it saves the
  players still online, so that save goes to disk, not redis.

## Testing

From the repository root:

```bash
./gradlew :redis-sync:redissync:test
```

No setup is needed. A redis binary bundled in `embedded-redis` starts on an ephemeral port, shared
by every test in the run; the host needs glibc, but not docker. To use an external redis instead (a container sidecar, or one
already running):

```bash
MRS_TEST_REDIS=localhost:6379 ./gradlew :redis-sync:redissync:test
```

Each test uses its own key prefix, so an external redis is never flushed. Tests do stall every
client of the redis they use (`CLIENT PAUSE`), so do not point them at one anything else relies
on, and expect timing failures if two runs share one.

CI is not set up for these tests yet. When it is, add `-PfailOnSkippedTests`. MockBukkit reports a call to an API it does not implement as
a skipped test rather than a failure, and on this suite a silent skip means a save/load path went
untested while the build stayed green.

### How the tests work

The tests run the plugin's real player data listeners against a real redis, with
[MockBukkit](https://github.com/MockBukkit/MockBukkit) standing in for the server.
`RedisSyncTestHarness` wires these together. Its `login`, `disconnect` and `TestPlayer.kick`
replay Paper's event order (see [ARCHITECTURE.md](ARCHITECTURE.md#the-paper-event-order-this-relies-on)) rather than MockBukkit's, which fires events in an order no
real server does and never saves in between. `pauseRedisWrites` issues `CLIENT PAUSE <ms> WRITE`,
which reproduces the window between a save being made and it committing.

Tests describe a player's state as one number, their "progress", which the harness stores in
three places: playerdata, which is saved over one redis connection, and a score and
advancements, which are saved over the other. Reading it back fails if the three disagree, so
every check of what loaded covers both connections.

Other pieces: `TestRedisServer` provides the redis, and `adapters/TestVersionAdapter` stands in
for the NMS version adapter. The tests are in package `com.playmonumenta.redissync` so they can
reach the plugin's package-private internals, such as `RedisAPI`'s and `BukkitConfigAPI`'s
constructors and `MonumentaRedisSync.registerPlayerDataListeners`.

| Class | Covers |
|---|---|
| `SaveLoadTest` | what is saved comes back; history lists stay aligned, including across datapack reloads |
| `SessionLifecycleTest` | when a session starts and ends, and which saves it accepts |
| `LoadFailureTest` | a failed load is neither played nor saved over, and does not lock the player out |
| `ReconnectTest` | reconnects inside the commit window, reconnects over a connection that is still open, and overlapping or dropped logins |
| `TransferTest` | the proxy is told to move a player only once their save has committed; lock timeouts |
| `DataHandoffTest` | stash get, rollback and loadFromPlayer |
| `ShutdownTest` | everything in flight at shutdown commits: final saves, the saves of players still online, other API writes, a rollback mid-write |
| `OfflineEditTest` | offline edits never bury newer progress |
| `LifecycleFuzzTest` | random sequences of all of the above, checked against a model of the save history |

`LifecycleFuzzTest` runs 8 fixed seeds by default. Run more with
`./gradlew :redis-sync:redissync:test --tests '*LifecycleFuzzTest' -Pmrs.fuzz.seeds=200`, or one
with `-Pmrs.fuzz.seed=N`. Redis timing is real, so a seed reproduces a failure's operations but
not always the failure. Whether a save should land depends on whether the player is locked for a
transfer, which the fuzz test asks the plugin rather than predicting, so lock timing is covered
by `TransferTest` and `DataHandoffTest` alone.

The plugin keeps its state in static singletons, so the tests must run sequentially in one JVM.
That is JUnit's default; do not enable parallel execution. `MonumentaRedisSyncAPI`'s player name
caches are not reset between tests.

### Limits

MockBukkit is not Paper, so these tests cover the plugin's own logic only. The real version
adapter (`VersionAdapter_v1_20_R3`), with NBT serialization and the dataconverter upgrade path, has
no coverage: MockBukkit cannot run alongside paperweight, which is why paperweight is applied
only to the `adapter_v1_20_R3` projects and the plugin builds against plain `paper-api`.
`TestVersionAdapter` stores "NBT" as JSON, and is not a specification of the save format.

The tests use `com.github.seeseemelk:MockBukkit-v1.20:3.93.2`, the last MockBukkit release for
1.20. It requires paper-api 1.20.6 on the test classpath, a version skew declared explicitly in
the repository's `gradle/libs.versions.toml`. Later MockBukkit releases moved to `org.mockbukkit.mockbukkit`, which
starts at 1.21. Moving to it means renaming the `be.seeseemelk.mockbukkit` package and about 60
classes, for which OpenRewrite recipes exist, and moving from JUnit 5 to JUnit 6.
`RedisSyncTestHarness` is the only file that imports MockBukkit, to keep that change small.

Ideas for further coverage are in [FUTURE_TESTS.md](FUTURE_TESTS.md).
