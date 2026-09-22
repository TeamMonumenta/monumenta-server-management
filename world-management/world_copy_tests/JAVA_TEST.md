# Running the Java world-copy test

`run_java_test.py` starts a throwaway Paper server directly on the host.

Per run it:

1. Generates the fixtures and clears `outputs/` (`_runner.py`).
2. Builds the four plugin jars via `./gradlew :<module>:shadowJar`.
3. Downloads Paper, CommandAPI and NBT-API into `.paper-cache/` (self-ignoring: the directory
   contains its own `.gitignore` with `*`). The Paper jar's sha256 is verified on every run and the
   jar is re-downloaded if it does not match. `--refresh` forces a re-download of all three.
4. Assembles a disposable server directory under the system temp dir: `paper.jar` (a symlink into
   the cache), `eula.txt`, `server.properties` and `log4j2.xml` from `server_files/`, and a
   `plugins/` directory of symlinks to the cached and built jars.
5. Runs `java -Dlog4j2.configurationFile=log4j2.xml -Xmx512M -jar paper.jar nogui` with
   `MONUMENTA_WORLD_COPY_TEST=1` and the inputs/outputs env vars pointed at the real host
   directories, streaming the log to stdout and to `server.log` in the server directory.
   `WorldCopyTestHarness` copies every fixture in `onLoad` and halts the JVM, so the process exits
   on its own; a 300 second watchdog kills it and fails loudly otherwise.
6. Deletes the server directory on success, keeps it (and prints the path) on failure or `--keep`.
7. Runs `validate.py` and exits with its status.

## The plugin jars must be the shaded ones

The runner globs for `-all.jar` (the `shadowJar` output), not the plain `jar` that `assemble`
produces. `world-management` gained two version-adapter subprojects (`adapter_api`, shaded in as an
`implementation` dependency, and `adapter_v1_20_R3`, a `runtimeOnly` dependency on the reobfuscated
artifact). `WorldStorageAdapters` resolves `WorldStorageAdapter_<version>` reflectively from the
plugin classloader, so both must be inside the jar Paper loads. Only `shadowJar` bundles them;
`assemble` does not even run `shadowJar`. The same applies to `MonumentaRedisSync`, whose Lettuce
dependency is likewise only in its `-all.jar`.

No separate staging of the adapter jar is needed. If the shaded jar ever stops carrying the adapter,
`unzip -l` on the plugin jar should show `com/playmonumenta/worlds/adapters/WorldStorageAdapter_*`;
if it does not, the server will fail during `onLoad` with "No world storage adapter for server
version ...".

## Pinned versions

| Component | Version |
|---|---|
| Paper | 1.20.4 build 499 |
| CommandAPI | 9.4.1 |
| NBT-API | 2.15.2 |

Paper is resolved through `https://fill.papermc.io/v3/projects/paper/versions/1.20.4/builds`
(the old `api.papermc.io/v2` endpoint now returns HTTP 410). The runner falls back to the pinned
`fill-data.papermc.io` object URL if the lookup API is unreachable.
