# Running the Java world-copy test

`run_java_test.py` starts a throwaway Paper server directly on the host. Nothing is containerized.

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

---

## Previously: the Docker arrangement (removed)

This test used to run the Paper server inside a container built from a `docker/` directory in this
folder. It was removed in favour of running Paper natively, which is faster to start, needs no
container runtime, and removes the bind-mount indirection. This section records what the Docker
setup looked like so it can be restored if a reason resurfaces.

⚠ **Open question:** nobody remembers why this was containerized in the first place. Reproducible
JRE version and host isolation are the plausible motives, but neither was written down. If you are
considering re-dockerizing, work out the actual requirement first.

`docker/` contained four files:

- **`Dockerfile`** - based on `eclipse-temurin:21-jre`. Build args `PAPER_VERSION=1.20.4`,
  `PAPER_BUILD=499`, `COMMANDAPI_VERSION=9.4.1`, `NBTAPI_VERSION=2.15.2`. A single `RUN` used
  `curl -fSL` to fetch `paper.jar` into `/server/` and `CommandAPI.jar` / `NBTAPI.jar` into
  `/server/plugins/`, so those downloads were baked into the image layer instead of a host cache
  and there was no checksum verification. It then wrote `eula=true` to `/server/eula.txt`, copied
  `server.properties`, `log4j2.xml` and `entrypoint.sh` in, and set `WORKDIR /server`.
  Build args `UID=1000` / `GID=1000` were passed from the host at build time
  (`--build-arg UID=$(id -u) --build-arg GID=$(id -g)`); the image `chown`ed `/server` and `/work`
  to them and ran `USER ${UID}:${GID}` with `ENV HOME=/server`, so files the copier wrote into the
  bind-mounted `outputs/` were owned by the invoking host user without any `chown` step. This
  uid/gid baking meant the image had to be rebuilt per developer, which is what `--rebuild` was for.
- **`entrypoint.sh`** - `exec java -Dlog4j2.configurationFile=log4j2.xml -Xmx512M -jar paper.jar nogui`.
  Identical to the command the native runner uses now.
- **`server.properties`** and **`log4j2.xml`** - unchanged; they now live in `server_files/` and are
  copied into the throwaway server directory.

`run_java_test.py` drove it with:

- `ensure_image(rebuild)` - `docker images -q monumenta-world-copy-test` to check for the image, and
  `docker build --build-arg UID=... --build-arg GID=... -t monumenta-world-copy-test docker/`
  when missing or when `--rebuild` was passed.
- `docker run --rm` with `-e MONUMENTA_WORLD_COPY_TEST=1`,
  `-e MONUMENTA_WORLD_COPY_TEST_INPUTS=/work/inputs`,
  `-e MONUMENTA_WORLD_COPY_TEST_OUTPUTS=/work/outputs`,
  `-v <inputs>:/work/inputs:ro`, `-v <outputs>:/work/outputs`, plus one
  `-v <host jar>:/server/plugins/<name>:ro` per Monumenta plugin jar. `--verbose` added
  `-e MONUMENTA_WORLD_COPY_TEST_LOG_LEVEL=TRACE`. There was no timeout.

To restore it you would need: the `docker/` files above, the image build/run helpers, the
`--rebuild` flag back in place of `--refresh`, and the plugin-jar globs updated to `-all.jar` (the
Docker path predated the version adapters and bind-mounted the unshaded `assemble` output, which
would no longer work).
