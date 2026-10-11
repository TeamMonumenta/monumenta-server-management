# Ideas for future test coverage

Not covered by `plugin/src/test` today, roughly in order of value. See the
[Testing](README.md#testing) section of the README for what is covered and how the tests work.

## Behaviors with no test

- **World selection on load.** A saved world UUID should win over a saved world name, and a saved
  world that is not loaded falls back to the first world, silently relocating the player.
- **`saving_disabled: true`.** Both save handlers cancel the server's own save before checking the
  flag, so with saving disabled data goes neither to redis nor to disk. Confirm that is intended
  before pinning it.
- **History depth.** Saves trim with `ltrim(path, 0, historyAmount)`, which is inclusive, so a
  configured 20 keeps 21 entries. Stash get, rollback and loadFromPlayer push onto every history
  list without trimming at all. Decide what is intended, then pin it.
- **Named stashes**: stash put and get by name, the stash list, and stash info.
- **Misaligned history**: rollback or stash put against a player whose history lists differ in
  length.
- **Concurrent saves**: two saves in flight at once are both waited on - by a reconnect, a
  transfer and shutdown.
- **A login with no UUID** at pre-login is refused as `unverified_username`.
- **A transfer's return location.** `sendPlayer` with a return location applies it to the
  transfer save only; nothing checks that it lands in the saved shard data.
- **Shoulder entities of a locked player** must not spawn here, and must spawn again once the
  lock ends.
- **An offline edit while the player is configuring.** They have a session but are not yet in
  `Bukkit.getPlayer`, so only the session check refuses the edit.

## Races the current tools cannot force

`CLIENT PAUSE WRITE` stalls every client at once, so it cannot reorder one of the plugin's two
redis connections against the other. A proxy that delays one connection only (toxiproxy, or a
small TCP relay in the harness) would allow tests for:

- **loadFromPlayer waiting for the source player's saves.** The copy's reads queue behind the
  source's playerdata save on the same connection, so a test passes with or without the wait.
  Only the scores and plugin data, saved over the other connection, need it.
- **Torn saves**: a save whose playerdata commits but whose scores and plugin data do not, as a
  crash between the two would leave.
- **Logins reading across connections**: any load that reads one connection before a save on the
  other has committed.

## Pure unit tests

- **Redis key shapes.** `MonumentaRedisSyncAPI.getRedis*Path` and `getRedisPerShardDataWorldKey`
  are string formatting. A golden test would catch a prefix change that orphans every player's
  data. It needs no harness, but loading `MonumentaRedisSyncAPI` pulls in CommandAPI through the
  static `SUGGESTIONS_ALL_CACHED_PLAYER_NAMES`; moving that to a nested holder class would let
  such a test load the class on its own.
- **Score format.** `ScoreboardUtils.loadFromJsonObject` (load) and the version adapter's
  `getPlayerScoresAsJson` (save) are two halves of one format that nothing cross-checks;
  `TestVersionAdapter` only agrees with itself. Note that `loadFromJsonObject` skips objectives it
  cannot modify, dropping those scores.
- `utils/Trie`.

## Beyond the current suite

- **Transfers end to end.** Two harnesses sharing one redis could model a transfer arriving at the
  target shard, and a player reaching another shard while their last save here still commits
  (the known cross-shard gap).
- **The real version adapter.** NBT serialization and the dataconverter upgrade path in
  `VersionAdapter_v1_20_R3` need a real server; MockBukkit cannot run alongside paperweight.
- **Many fuzz seeds.** Once CI exists, a scheduled run of `LifecycleFuzzTest` with a few hundred
  seeds (`-Pmrs.fuzz.seeds=300`) would explore far more interleavings than the default 8.
