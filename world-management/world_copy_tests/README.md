# WorldCopier Test Fixtures

Eight fixture worlds for testing `WorldCopier.copyWorldRegenUuids`. Fixtures
W2-W8 are regenerable via `python3 generate.py inputs/`; W1 is an optional
slot for a real world (see below).

## Usage

There are two end-to-end entrypoints, one per copier implementation:

`run_python_test.py` - Python `copy_world.py`
`run_java_test.py` - Java `WorldCopier` (via Dockerized Paper server)

Both run the same `generate -> copy -> validate` flow (shared in `_runner.py`).

### Python
Run the full Python end-to-end test (generate, copy every fixture with
`copy_world.py`, validate):
```bash
cd world-management/world_copy_tests
python3 run_python_test.py
```

### Java
Run the full Java end-to-end test (generate, copy every fixture with the real
Java `WorldCopier`, validate). The copy runs unattended inside a throwaway Paper
server in Docker: the world-management plugin, when `MONUMENTA_WORLD_COPY_TEST`
is set, copies the fixtures in its `onLoad` hook (see `WorldCopyTestHarness`)
and exits before any world loads. Requires `docker` and a JDK (for the gradle
jar build):
```bash
python3 run_java_test.py
```
Flags: `--no-build` skips rebuilding the plugin jars (reuse the last gradle output);
`--rebuild` rebuilds the Docker image (needed when anything under `docker/` changes);
`--verbose` raises the container log level to TRACE.

### Running individual stages

Generate inputs (idempotent, regenerates from source):
```bash
python3 generate.py inputs/
```

Run the Python reference copy for a single world:
```bash
python3 ../../monumenta-automation/utility_code/copy_world.py inputs/<name> outputs/<name>
```

Validate outputs against inputs:
```bash
python3 validate.py inputs/ outputs/
```

## Modifying the Java `WorldCopier`

Debugging: the container streams the Paper log. The harness prints
`[world-copy-test] PASS/FAIL <fixture>`, and a failing copy prints a full Java stack trace
pinpointing the `WorldCopier` line. `WorldCopier` also emits `MMLog.trace` diagnostics at the
chunk level for deeper inspection; pass `--verbose` to see them.

### Key files

| File | Role |
|---|---|
| `world-management/src/.../paper/WorldCopier.java` | The copier implementation |
| `world-management/src/.../paper/WorldCopyTestHarness.java` | Test entrypoint: copies every fixture in `onLoad` when `MONUMENTA_WORLD_COPY_TEST` is set, then exits |
| `world-management/src/.../paper/WorldManagementPlugin.java` | `onLoad` calls the harness when the env var is set, before normal startup |
| `run_java_test.py` / `_runner.py` | Orchestration: generate -> build jars -> docker run -> validate |
| `docker/` | Throwaway Paper image (pinned Paper / CommandAPI / NBT-API versions) |
| `validate.py` | Asserts copy correctness; shared by the Python and Java entrypoints |

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
behave as of 2026-06-18. They are breadcrumbs for anyone debugging the copiers, *not* a contract:
the validators are written to tolerate either behavior and must **not** start depending on any of
them. If an implementation changes, update these notes rather than tightening the tests.

- **Empty entity chunks**: Java drops entity chunks whose `Entities` list is empty; Python
  rewrites them as present-but-empty chunks. Validators accept a chunk that is absent or
  present-but-empty.
- **Compression type of verbatim chunks**: Java preserves the original compression type for
  chunks it copies without modification; Python rewrites all chunks as type 2 (zlib). The
  W6 validator only asserts compression type for modified (UUID-bearing) chunks.
- **Force-external small chunks**: Both copiers re-serialize chunks and let small ones collapse
  back inline, so a chunk forced external via the `force_external` flag (but small enough to fit
  inline) ends up inline in the output `.mca`. Only genuinely oversized chunks (data > 255
  sectors) stay external. The W7 validator only requires the genuinely oversized chunk to remain
  external; it does not assert anything about small force-external chunks.

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
|           |   |-- SpawnData.entity = { id: zombie }                      # no UUID -- must remain UUID-free
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
