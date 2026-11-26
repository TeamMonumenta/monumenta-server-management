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
- `getDeparture` and `PlayerSessionEndEvent`, which tell plugins why a player is
  leaving and when their connection's state can be dropped. The old transfer
  events (`PlayerServerTransferEvent`, `PlayerTransferFailEvent`) and
  `isPlayerTransferring` remain only as deprecated bridges, scheduled for removal
- Support for multiple different "domains". Each server in the same domain will
  use the same player data. No data transfer is possible between domains
  (useful if you have different types of servers)
- Exposes the Redis API (via Lettuce) for access by other plugins
- Also loads on Velocity, where it makes every server switch a clean one: the
  player leaves their server completely before the next one loads them (see
  [ARCHITECTURE.md](ARCHITECTURE.md#the-proxy-clean-switches))
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
pushes one entry onto every list. Position and world are kept per shard in a hash. How it all
works, including the proxy's part, is described in [ARCHITECTURE.md](ARCHITECTURE.md); this
section is what other plugins can rely on.

- **A login always loads the newest save, on any shard.** A player's data has one owner at a
  time: the session holding their **session lease** in redis (`<domain>:session:<uuid>`). A login
  takes it in pre-login, waiting up to 20 seconds for the previous holder, on this shard or any
  other, to finish saving and release it, and is refused ("Your previous session is still
  saving") if it does not. If the player's previous connection here is still open, the newest
  login wins: the old one is kicked, or, if still loading, never saved and kicked once it joins.
- **A lease never outlives its shard for long.** It expires 90 seconds after its shard stops
  renewing it, so a crashed or hung shard locks its players out for at most that.
  `/sessionlease <player> [clear]` shows or deletes one. Every save checks the lease in the same
  script that writes it, so a session that has lost it writes nothing, and is kicked.
- **Saves are made only from the tick after PlayerJoinEvent**, so other plugins can load their
  own state in their join handlers. No save is made after a load failed. A player who is
  transferring, or waiting for a data handoff, is saved as usual until they leave.
- **Only the `Player` object of the current connection counts.** A plugin holding one from an
  earlier connection cannot save its state over the current one. `sendPlayer` through one, or
  through a `Player` that has left the game, logs a warning and does nothing else. Stash get, rollback and loadFromPlayer throw for it.
- **Fake players** (NPC plugins) load as usual but are never saved, since they are not logins.
- **A failed load** refuses or kicks the player, and nothing they do is saved. They can log in
  again once whatever broke is fixed. A player online without a session, such as after a plugin
  reload, is kicked, since nothing of theirs would be saved.
- **Without a session**, `getPlayerPluginData` and `getPlayerShardData` return null,
  `getPlayerContentData` returns a fresh empty object that is not kept, and `savePlayerContent` is
  ignored with a warning.
- **A connection's life, as plugins see it:** PlayerJoinEvent (`getPlayerPluginData` holds what
  the last save stored), then saves (PlayerSaveEvent, where plugins store theirs), then
  PlayerQuitEvent, the final PlayerSaveEvent, and PlayerSessionEndEvent. PlayerSessionEndEvent is
  where to drop whatever a plugin keeps for the player: it comes after the final save, and before
  the player can join any shard again, this one included (a rollback or stash get reconnects them
  here within a second). No timer is needed, and join handlers find nothing left from the
  previous connection. A player who never had a session here (saving disabled) gets it at their
  quit. It does not fire at shutdown, when plugins are disabled before players are removed.
- **`getDeparture`** says why the player is leaving: `Departure.Transfer` (target shard, and the
  content the transfer was requested with, if any) or `Departure.Handoff` (stash get, rollback,
  loadFromPlayer). It holds from the request until they have left, so in PlayerQuitEvent and the
  final save, and PlayerSessionEndEvent carries it. Null means an ordinary logout, including a
  switch the proxy started on its own, such as `/send` (a debugging tool, disabled in
  production).
- **`sendPlayer`** only asks the proxy to move the player (BungeeCord "Connect"), after requesting
  the content passed to it, if any, and applies the return location, if any, to their saves from
  then on. The proxy takes the player off this shard first and holds their client on its
  "Reconfiguring..." screen: here they log out as usual, and their final save, made after
  PlayerQuitEvent (so quit handlers' changes are in it), is what the target loads. Nothing is
  frozen in between, so anything a transfer must enforce in that save (dropping items,
  dismounting) belongs in PlayerQuitEvent, checked with `getDeparture`. A vehicle ridden by the
  player alone as they leave travels with them (vanilla saves it with the player). If they are
  still here after 10 seconds (25 ticks if network relay does not list the target as online), the
  request is dropped and the player told. Once they have left, a failed transfer is the proxy's to
  handle: it sends them back to where they came from, as an ordinary login, or disconnects them.
- **Deprecated, scheduled for removal:** `PlayerServerTransferEvent`, `PlayerTransferFailEvent`
  (redis-sync and MonumentaCommon copies) and `isPlayerTransferring` still work, so plugins built
  against older versions load, but they no longer mean the player is frozen. The transfer event
  fires at the `sendPlayer` request (cancelling it cancels the transfer); the fail event fires only
  if the request times out or throws. Use `getDeparture` and PlayerSessionEndEvent instead.
- **Every server switch through the proxy is a clean one**: transfers, Velocity's `/send` and
  `/server`, other plugins. Logins and redirects after a kick, which have no server to leave, are
  left alone.
- **Stash get, rollback and loadFromPlayer** read the replacement first; if it is missing, the
  player (or the moderator) is told and nothing else happens. Otherwise the proxy reconnects the
  player to this shard: as they leave, their final save is made, the replacement is pushed after
  it, and they come back with it. The final save stays in history just below, as a way back. A
  rollback index counts back from the newest save made before the call. `playerrollback` can be
  run from the console.
- **Offline edits** (`getOfflinePlayerData`, then `saveOfflinePlayerData`) are refused, returning
  false, if the player is online anywhere (holds a lease), is logging in here, or has saved since
  the data was read. A login here waits for an offline edit in flight.
- **Shutdown** writes pending handoffs, then waits for every save and other player data write in
  flight before closing redis, and releases the leases whose saves committed.

### Known limitations

- **The proxy plugin is required, and must be deployed before or with the shards.** Without it,
  Velocity's own switch connects to the target before leaving the source, so the target's login
  waits for a lease the source only releases after that login, and is refused after 20 seconds.
  The proxy plugin finds the Velocity internals it needs by reflection at startup ("Clean server
  switches enabled"); if they are missing, a switch asks the player to reconnect instead, with
  their login server set to the target.
- **Two connections for one account at once.** Velocity never gives one backend two live
  connections for a player, but a proxy bug could. The second login waits for the first's lease
  and is refused. Should both get through anyway (the lease vanished), the second to load is
  refused. If one drops while the other is still configuring, its close cannot be told apart
  from the other's (the event carries only a UUID), so the other is refused too. The worst case
  is a refused login, never unsaved play.
- **A dead connection stuck in configuration** cannot be kicked through the Bukkit API. A
  reconnect waits up to `TIMEOUT_SECONDS` for Paper to time it out, and is refused if it has
  not.
- **Changes made in the join tick** are not saved by a transfer or handoff in that same tick,
  because saves open on the next tick.
- **Failed saves are not retried.** A reconnect after one loads the save before it. A script that
  failed part way may have partly applied, so it cannot safely be retried.
- **A crash, or the shutdown wait running out,** loses whatever had not committed. Each save is
  one atomic script (then advancements, as a second script on the same connection), so a save is
  never torn across the main lists.
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
three places: playerdata, a score and advancements, each saved by a different part of a save.
Reading it back fails if the three disagree, so every check of what loaded covers all of them.

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
| `TransferTest` | a transfer only asks the proxy; the logout save, with the return location, is what the target loads; timeouts, including one racing the proxy |
| `SessionLeaseTest` | taking, waiting for, renewing, fencing and releasing the session lease |
| `DataHandoffTest` | stash get, rollback and loadFromPlayer: read first, replacement pushed after the final save, timeouts |
| `ShutdownTest` | everything in flight at shutdown commits: final saves, the saves of players still online, other API writes, a pending rollback |
| `OfflineEditTest` | offline edits never bury newer progress |
| `LifecycleFuzzTest` | random sequences of all of the above, checked against a model of the save history |

`LifecycleFuzzTest` runs 8 fixed seeds by default. Run more with
`./gradlew :redis-sync:redissync:test --tests '*LifecycleFuzzTest' -Pmrs.fuzz.seeds=200`, or one
with `-Pmrs.fuzz.seed=N`. Redis timing is real, so a seed reproduces a failure's operations but
not always the failure. The proxy side of a transfer is not covered by unit tests; the tests
stand in for the proxy by disconnecting the player. It is tested end to end with simulated and
real players against a running proxy and shards: see monumenta-testbot's `TESTING.md`.

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
