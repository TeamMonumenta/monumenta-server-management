# WorldCopier Test Fixtures

Eight fixture worlds for testing `WorldCopier.copyWorldRegenUuids`. Fixtures
W2-W8 are regenerable via `.venv/bin/python generate.py inputs/`; W1 is an optional
slot for a real world (see below).

## Usage

### Environment setup

One-time setup, done from the repository root unless stated otherwise.

**1. Check out the submodules.** Everything here runs against `monumenta-automation`, its
nested `quarry` submodule, and quarry's own nested `brigadier.py` submodule
(`quarry.types.nbt` imports `brigadier.string_reader` from it):
```bash
git submodule update --init --recursive
```
`monumenta-automation/.gitmodules` and `quarry/.gitmodules` both use SSH URLs
(`git@github.com:...`). Without a GitHub SSH key the nested clones fail; rewrite them to
HTTPS first:
```bash
git config --global url."https://github.com/".insteadOf git@github.com:
```

**2. Two quarry capabilities are required.** The pins already point at a quarry that has both, so
`git submodule update --init --recursive` is enough; these are the failure modes if a pin ever moves
backwards.

- `RegionFile.save_chunk(compression_type=...)`, used by W6. Missing it fails generation with
  `TypeError: RegionFile.save_chunk() got an unexpected keyword argument 'compression_type'`.
- A correct Anvil chunk length field (see "The quarry Anvil length field" below). Missing it
  generates plausible-looking fixtures that fail W6 in the Java stage with an `EOFException`.

quarry is a nested submodule of `monumenta-automation`, so a quarry fix reaches this repo only after
both pins are bumped, and checking out a branch by hand is undone by the next `git submodule update`.

**3. Create the venv and install dependencies.** The automation libs target `pypy3`
(`copy_world.py` is shebanged `#!/usr/bin/env pypy3`), so build the venv from `pypy3`. On
Debian/Ubuntu that needs the `pypy3` and `pypy3-venv` packages:
```bash
cd world-management/world_copy_tests
pypy3 -m venv .venv
.venv/bin/pip install -r requirements.txt
```

**4. Run everything through `.venv/bin/python`.** `_runner.py` launches every child process
(`generate.py`, `copy_world.py`, `validate.py`) with `sys.executable`, so the interpreter
that starts an entrypoint is the one the whole flow uses; `copy_world.py`'s shebang is
deliberately bypassed. Running with the system `python3` fails on the missing dependencies.

### End-to-end entrypoints

There are three end-to-end entrypoints:

`run_python_test.py` - Python `copy_world.py`
`run_java_test.py` - Java `WorldCopier` (via a throwaway Paper server)
`run_load_test.py` - Java `WorldCopier`, then loads the results on a real server

The first two run the same `generate -> copy -> validate` flow (shared in `_runner.py`),
validating the copy by re-reading its NBT with quarry. The third answers a different
question: whether Minecraft itself can load what the copier produced.

### Python
Run the full Python end-to-end test (generate, copy every fixture with
`copy_world.py`, validate):
```bash
cd world-management/world_copy_tests
.venv/bin/python run_python_test.py
```

### Java
Run the full Java end-to-end test (generate, copy every fixture with the real
Java `WorldCopier`, validate). The copy runs unattended inside a throwaway Paper
server: the world-management plugin, when `MONUMENTA_WORLD_COPY_TEST` is set,
copies the fixtures in its `onLoad` hook (see `WorldCopyTestHarness`) and exits
before any world loads. Paper and its plugin jars are downloaded and cached in
`.paper-cache/`, and the server itself runs in a temporary directory that is
deleted unless the run fails. Requires a JDK (for both the gradle build and the
server):
```bash
.venv/bin/python run_java_test.py
```
Flags: `--no-build` skips rebuilding the plugin jars (reuse the last gradle output);
`--refresh` re-downloads the cached Paper and plugin jars; `--keep` retains the
temporary server directory (and its `server.log`) after a successful run;
`--verbose` raises the server log level to TRACE.

`JAVA_TEST.md` documents what each run does step by step, why the plugin jars have
to be the shaded ones, and the pinned Paper/CommandAPI/NBT-API versions.

### Load verification

`validate.py` checks the copier's output by parsing it with quarry and asserting NBT-level
properties. That cannot catch a world that is structurally valid NBT but that a real server
refuses to load, which is exactly the failure mode an earlier version of this code had.

`run_load_test.py` closes that gap. It copies the fixtures with the Java copier, then starts a
Paper server carrying nothing but a small purpose-built plugin (`verifier/`, compiled with plain
`javac`, not part of the gradle build). For both the input world and the output world it
enumerates every chunk present in `region/*.mca`, force-loads each one through Bukkit, and counts
the entities and block entities the server actually sees. Input and output must agree exactly.

```bash
.venv/bin/python run_load_test.py
```

Comparing input against output, rather than against fixed expected numbers, is what makes the
result interpretable: a real world legitimately produces chunk-load warnings (light data,
heightmap size), and seeing the identical count on both sides is what distinguishes a property of
the source world from damage done by the copier.

Only `01_real_world` gets meaningful coverage here. The synthetic fixtures are reported as SKIP:
their stub `level.dat` has no `WorldGenSettings`, so a real server cannot open them at all, and
even with that fixed their chunks have no block sections, so vanilla discards every block entity
(`Skipping BlockEntity with id minecraft:spawner`) and the comparison would assert 0 against 0.
`UNLOADABLE_FIXTURES` in `run_load_test.py` records this, and `--all-fixtures` re-runs the
experiment for anyone who wants to recheck the assumption. Do not add entries to that list to
silence a genuine failure.

### Running individual stages

Generate inputs (idempotent, regenerates from source):
```bash
.venv/bin/python generate.py inputs/
```

Run the Python reference copy for a single world:
```bash
.venv/bin/python ../../monumenta-automation/utility_code/copy_world.py inputs/<name> outputs/<name>
```

Validate outputs against inputs:
```bash
.venv/bin/python validate.py [--python-reference] inputs/ outputs/
```

## Modifying the Java `WorldCopier`

Debugging: the runner streams the Paper log to stdout and to `server.log` in the
temporary server directory, which is kept on failure. The harness prints
`[world-copy-test] PASS/FAIL <fixture>`, and a failing copy prints a full Java stack trace
pinpointing the `WorldCopier` line. `WorldCopier` also emits `MMLog.trace` diagnostics at the
chunk level for deeper inspection; pass `--verbose` to see them.

### Key files

| File | Role |
|---|---|
| `world-management/src/.../paper/WorldCopier.java` | The copier implementation |
| `world-management/src/.../paper/RegionFileRewriter.java` | Walks a region/ or entities/ folder, regenerating UUIDs chunk by chunk |
| `world-management/src/.../paper/EntityUuidRegenerator.java` | The typed NBT walk deciding which UUIDs are entity identities and get replaced |
| `world-management/src/.../paper/WorldStorageAdapters.java` | Picks the adapter matching the running server version |
| `world-management/adapter_api/.../WorldStorageAdapter.java` | Version-agnostic world storage boundary: chunk coordinates and NBT only |
| `world-management/adapter_v1_20_R3/.../WorldStorageAdapter_v1_20_R3.java` | 1.20.4 implementation, on the server's own `RegionFile` and `NbtIo` |
| `world-management/src/.../paper/WorldCopyTestHarness.java` | Test entrypoint: copies every fixture in `onLoad` when `MONUMENTA_WORLD_COPY_TEST` is set, then exits |
| `world-management/src/.../paper/WorldManagementPlugin.java` | `onLoad` calls the harness when the env var is set, before normal startup |
| `run_java_test.py` / `_runner.py` | Orchestration: generate -> build jars -> run Paper -> validate |
| `server_files/` | `server.properties` and `log4j2.xml` copied into the throwaway server |
| `JAVA_TEST.md` | Per-run mechanics of the Java stage: jar requirements and pinned versions |
| `validate.py` | Asserts copy correctness at the NBT level; shared by the Python and Java entrypoints |
| `run_load_test.py` / `verifier/` | Loads copied worlds on a real Paper server and compares what it sees against the input |
| `_server.py` | Shared jar caching, Paper download, and throwaway-server plumbing |

## Development

Lint and type-check the scripts (no external test runner; `pylint` and `pyright`
must be on PATH):
```
pylint *.py
pyright
```

## Implementation observations (not guaranteed behavior)

`validate.py` is designed to pass against both the Java `WorldCopier` and the Python
`copy_world.py` reference tool. The notes below describe how those two implementations happen to
behave as of 2026-08-24. They are breadcrumbs for anyone debugging the copiers, *not* a contract:
the validators are written to tolerate either behavior and must **not** start depending on any of
them. If an implementation changes, update these notes rather than tightening the tests.

- **Empty entity chunks**: Java leaves entity chunks whose `Entities` list is empty exactly as
  they were, because it only rewrites chunks whose UUIDs changed; Python rewrites them as
  present-but-empty chunks. Validators accept a chunk that is absent or present-but-empty.
- **Compression type of verbatim chunks**: Java preserves the original compression type for
  chunks it copies without modification; Python rewrites all chunks as type 2 (zlib). The
  W6 validator only asserts compression type for modified (UUID-bearing) chunks.
- **Top-level copy whitelist**: only the Java copier has one. `copy_world.py` writes
  `level.dat` plus the `region/`/`entities/` chunk data and nothing else, so it never copies
  `monumenta/`. Unlike the items below this is a capability gap rather than an incidental
  difference, so the W2 validator gates it on the `--python-reference` flag
  (`run_python_test.py` passes it; the Java stage still asserts the whitelist in full) rather
  than tolerating a missing `monumenta/` unconditionally.
- **Force-external small chunks**: Python re-serializes every chunk, so a chunk forced external
  via the `force_external` flag (but small enough to fit inline) collapses back inline. Java only
  rewrites chunks whose UUIDs changed, so such a chunk stays external unless it also carried a
  UUID. Only genuinely oversized chunks (data > 255 sectors) stay external either way. The W7
  validator requires the genuinely oversized chunk to remain external and permits, but does not
  require, a `.mcc` for the unmodified force-external chunk.

## The quarry Anvil length field

The Anvil per-chunk length field counts the compression-type byte plus the payload, and Minecraft
reads exactly `length - 1` payload bytes (`RegionFile.getChunkDataInputStream`). quarry's
`RegionFile.save_chunk` used to store just the payload length, one byte short, and its reader
compensated for its own writer. quarry round-tripped with itself perfectly while producing files a
real server misread by a byte: invisible for zlib and gzip, where the NBT payload is fully decoded
before the missing byte is ever needed, and fatal for uncompressed chunks, which lose the last byte
of their NBT.

This is fixed in quarry as of the `compress` branch. `save_chunk` writes the spec length, and
`load_chunk` hands zlib and gzip everything left in the reserved sectors rather than slicing to the
declared length, so it still reads the one-byte-short files that every previously copied world
contains. That backward compatibility is load-bearing: `copy_world.py` also writes through quarry,
so every world Monumenta's automation has ever produced carries the short field.

Two consequences worth remembering:

- The fixtures are byte-accurate Anvil straight out of `generate.py`, with no post-processing. If a
  future quarry regresses this, W6 fails immediately, because its uncompressed chunks are the case
  that actually breaks.
- Real Anvil writers do not under-report chunk lengths. An earlier Java copier carried a
  "truncation tolerant" inflate loop on that premise; it was compensating for quarry, and it hid the
  fact that the fixtures were not valid Anvil at all. Do not reintroduce it: if a chunk will not
  inflate at its declared length, the file is wrong and the copier should say so.
- The other framing value worth checking is the location header's sector count (its low 8 bits). An
  external (`.mcc`) chunk occupies exactly one sector regardless of payload size; storing the
  payload's count strands sectors, and at exactly 256 truncates to 0 so the chunk reads as absent.

## Fixtures

---

**W1 - `01_real_world` (optional)**

An empty placeholder folder for dropping in a real world, to exercise the copier against a
large volume of real data. `generate.py` creates it empty and never overwrites it, so a world
placed here survives regeneration.

Validator:
- If the folder is empty (no `level.dat`), the fixture is reported `SKIP` and does not fail.
- Otherwise: every entity UUID in the input is regenerated (input and output UUID sets are
  disjoint) with no UUIDs added or dropped (counts match), and every region/entities chunk is
  structurally identical after stripping the UUID family (only UUIDs differ). Empty entity
  chunks dropped by the Java copier are tolerated. `LevelName` is not asserted (both copiers
  rename it to the destination folder name, which differs from the source world's name).

---

**W2 - `02_baseline`**

Terrain-only region with no entities and no UUIDs. Includes non-whitelisted entries that must be
dropped (`data/`, `poi/`, `level.dat_old`, `session.lock`, `uid.dat`, and `datapacks/`) alongside
the `monumenta/` subdirectory that must be copied verbatim. Covers the top-level copy whitelist,
`level.dat` rename, and the region verbatim path.

The copy whitelist is `level.dat`, `region/`, `entities/`, and `monumenta/`; both copiers drop
everything else. `datapacks/` is intentionally excluded: a sub-world is not expected to carry its
own datapacks.

```
02_baseline/
|-- data/
|   `-- raids.dat                      # not whitelisted; dropped from output
|-- datapacks/
|   `-- vanilla/
|       `-- pack.mcmeta                # not whitelisted; dropped from output
|-- monumenta/
|   `-- marker.txt                     # whitelisted; verbatim-copied to output
|-- poi/                               # not whitelisted; dropped from output
|-- region/
|   `-- r.0.0.mca
|       |-- chunk (0,0): terrain only
|       |-- chunk (1,0): terrain only
|       `-- chunk (2,0): terrain only
|-- level.dat
|-- level.dat_old                      # not whitelisted; dropped from output
|-- session.lock                       # not whitelisted; dropped from output
`-- uid.dat                            # not whitelisted; dropped from output
```

Validator: output omits every non-whitelisted entry (including `datapacks/`); `monumenta/` is
copied byte-identically; `LevelName` is `"02_baseline"`; all region chunks structurally identical
to input; no UUIDs anywhere.

---

**W3 - `03_entities_basic`**

An `entities/` region exercising UUID regen on the three entity-origin edge types, plus the
empty-chunk drop rule.

```
03_entities_basic/
|-- entities/
|   `-- r.0.0.mca
|       |-- chunk (0,0):
|       |   |-- zombie+UUID
|       |   |-- creeper+UUID { Passengers: [skeleton+UUID] }                                # Entity->Entity
|       |   `-- armor_stand+UUID { HandItems: [_, zombie_spawn_egg{tag.EntityTag: zombie+UUID}] }  # Entity->Item->Entity
|       `-- chunk (1,0): Entities=[]   # dropped by Java; kept (empty) by Python
`-- level.dat
```

Validator: all entity/passenger/nested-entity UUIDs changed; entity order and count
preserved; empty chunk absent (Java) or present with empty `Entities` list (Python); no
UUID in output collides with any UUID in input.

---

**W4 - `04_block_entities`**

A `region/` chunk with three block entities covering `BlockEntity -> Entity` and
`BlockEntity -> Item` edges, including the conditional-regen rule and the 1.20.4 beehive
format.

```
04_block_entities/
|-- region/
|   `-- r.0.0.mca
|       `-- chunk (0,0):
|           |-- block_entity[0]: spawner
|           |   |-- SpawnData.entity = { id: zombie }                      # no UUID; must remain UUID-free
|           |   `-- SpawnPotentials[0].data.entity = skeleton+UUID         # must be regenerated
|           |-- block_entity[1]: beehive
|           |   `-- bees[0].entity_data = bee+UUID                         # 1.20.4 spec format; must be regenerated
|           `-- block_entity[2]: chest
|               `-- Items = [stone, written_book, music_disc_13]           # no UUIDs; structurally identical
`-- level.dat
```

Validator: `SpawnPotentials` skeleton UUID changed; `SpawnData` zombie has no UUID in
output; beehive bee UUID changed; whole chunk structurally identical after stripping UUIDs
(spawner/beehive fields, `FlowerPos`, chest items); no UUID collision between input and output.

Note: beehive NBT uses the 1.20.4 spec (`bees[].entity_data`). The automation library
multipath has been updated to match this path. If a Minecraft version changes the beehive
schema, regenerate and re-confirm.

---

**W5 - `05_item_recursion`**

A chest whose items exercise every Item-origin edge, completing the link-transition matrix.

```
05_item_recursion/
|-- region/
|   `-- r.0.0.mca
|       `-- chunk (0,0):
|           `-- block_entity[0]: chest
|               `-- Items:
|                   |-- [0] spawner { tag.BlockEntityTag: spawner { SpawnData.entity: zombie+UUID } }  # Item->BlockEntity->Entity
|                   |-- [1] zombie_spawn_egg { tag.EntityTag: zombie+UUID }                            # Item->Entity
|                   `-- [2] bundle { tag.Items: [ zombie_spawn_egg { tag.EntityTag: zombie+UUID } ] }  # Item->Item->Entity
`-- level.dat
```

Validator: each UUID along each chain changed; all surrounding item NBT (ids, counts, other
tags) identical after stripping UUID family; no UUID collision.

---

**W6 - `06_compression_variants`**

A single region file with six chunks covering all three Anvil compression types, split into
modified (UUID-bearing) and verbatim (terrain-only) pairs.

```
06_compression_variants/
|-- region/
|   `-- r.0.0.mca
|       |-- chunk (0,0): spawner+UUID, compression type 1 (gzip)       # modified; output must be type 2
|       |-- chunk (1,0): spawner+UUID, compression type 2 (zlib)       # modified; output type 2
|       |-- chunk (2,0): spawner+UUID, compression type 3 (raw/none)   # modified; output type 2
|       |-- chunk (3,0): terrain only, compression type 1 (gzip)       # verbatim
|       |-- chunk (4,0): terrain only, compression type 2 (zlib)       # verbatim
|       `-- chunk (5,0): terrain only, compression type 3 (raw/none)   # verbatim
`-- level.dat
```

Validator: input chunks have the expected compression types; modified output chunks (0-2)
are all type 2 and have regenerated UUIDs; verbatim output chunks (3-5) have structurally
identical NBT. Compression type preservation for verbatim chunks is only asserted by the
Java stage, not the Python reference tool.

---

**W7 - `07_external_mcc`**

Region and entities files with oversized chunks stored in sibling `.mcc` files. Contains
two categories: chunks forced external via the `force_external` flag (small data, may become
inline after Python copy) and a genuinely oversized chunk (~1.1 MB padded, always stays
external). Also includes a negative-coordinate region to exercise `c.<cx>.<cz>.mcc` naming
with negative values.

```
07_external_mcc/
|-- entities/
|   |-- c.0.0.mcc              # chunk (0,0): zombie+UUID  [force_external]
|   `-- r.0.0.mca              # chunk (0,0) stub -> c.0.0.mcc
|-- region/
|   |-- c.-32.-32.mcc          # chunk global (-32,-32): spawner+UUID  [force_external]
|   |-- c.0.0.mcc              # chunk (0,0): terrain only  [force_external]
|   |-- c.1.0.mcc              # chunk (1,0): spawner+UUID  [force_external]
|   |-- c.2.0.mcc              # chunk (2,0): terrain + ~1.1 MB random padding  [genuinely oversized, auto-spills]
|   |-- r.-1.-1.mca            # chunk local (0,0) [global (-32,-32)] stub -> c.-32.-32.mcc
|   `-- r.0.0.mca              # chunks (0,0),(1,0),(2,0) stubs -> c.0.0.mcc, c.1.0.mcc, c.2.0.mcc
`-- level.dat
```

Validator:
- `r.0.0.mca` chunk (0,0): terrain NBT unchanged.
- `r.0.0.mca` chunk (1,0): spawner UUID changed.
- `r.0.0.mca` chunk (2,0): still external in output (data too large to inline); `_test_padding`
  byte-array has expected length (`_BIG_CHUNK_PADDING_SIZE`).
- `r.-1.-1.mca` chunk (0,0): spawner UUID changed.
- `entities/r.0.0.mca` chunk (0,0): entity UUID changed.
- Only `c.2.0.mcc` remains in the output `region/` directory (force-external small chunks
  become inline and their `.mcc` files are cleaned up by the save path).

---

**W8 - `08_scores_world_uuid`**

Exercises the two strip-on-copy behaviors that are not UUID regeneration: Bukkit world UUIDs
(`WorldUUIDMost`/`WorldUUIDLeast`) and Monumenta entity scores
(`BukkitValues."monumenta:entity_scores"`). Both must be removed from every entity and block
entity, at any nesting depth.

```
08_scores_world_uuid/
|-- entities/
|   `-- r.0.0.mca
|       `-- chunk (0,0):
|           `-- zombie+UUID { WorldUUID*, BukkitValues: { entity_scores, other } }   # scores stripped, "other" kept
|               `-- Passengers: [ skeleton+UUID { WorldUUID*, BukkitValues: { entity_scores } } ]  # empty BukkitValues removed
|-- region/
|   `-- r.0.0.mca
|       `-- chunk (0,0):
|           `-- block_entity[0]: spawner { WorldUUID* }                              # world UUID stripped from block entity
|               `-- SpawnPotentials[0].data.entity = skeleton+UUID { WorldUUID*, BukkitValues: { entity_scores } }
`-- level.dat
```

Validator: no `WorldUUIDMost`/`WorldUUIDLeast` or `monumenta:entity_scores` key survives anywhere
in either output chunk; nested entity UUIDs are regenerated; a non-score `BukkitValues` entry
(`monumenta:other`) is preserved; a `BukkitValues` compound left empty after stripping scores is
removed entirely.
