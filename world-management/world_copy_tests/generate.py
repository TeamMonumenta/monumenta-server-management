#!/usr/bin/env python3
"""Generate committed fixture worlds for WorldCopier testing."""
import gzip
import json
import os
import shutil
import sys

_AUTO = os.path.join(os.path.dirname(__file__), "../../monumenta-automation/utility_code")
sys.path.insert(0, _AUTO)
sys.path.append(os.path.join(os.path.dirname(__file__), "../../monumenta-automation/quarry"))

from quarry.types import nbt
from _helpers import (
    make_entity, make_block_entity,
    make_region_chunk, make_entities_chunk, save_chunk, save_chunk_external,
    make_big_padding_tag,
)


def _make_level_dat(name: str) -> nbt.TagCompound:
    return nbt.TagCompound({
        "Data": nbt.TagCompound({
            "DataVersion": nbt.TagInt(3700),
            "LevelName": nbt.TagString(name),
            "version": nbt.TagInt(19133),
            "Version": nbt.TagCompound({
                "Id": nbt.TagInt(3700),
                "Name": nbt.TagString("1.20.4"),
                "Series": nbt.TagString("main"),
                "Snapshot": nbt.TagByte(0),
            }),
            "SpawnX": nbt.TagInt(0),
            "SpawnY": nbt.TagInt(64),
            "SpawnZ": nbt.TagInt(0),
        })
    })


def _save_level_dat(world_dir: str, name: str) -> None:
    level_nbt = _make_level_dat(name)
    nbt.NBTFile(nbt.TagRoot.from_body(level_nbt)).save(
        os.path.join(world_dir, "level.dat")
    )


def _create_region_file(path: str) -> nbt.RegionFile:
    """Create a new empty .mca file (8 KiB header) and return an open RegionFile."""
    with open(path, "wb") as f:
        f.write(b"\x00" * 8192)
    return nbt.RegionFile(path)


def _make_spawner(x: int, y: int, z: int, with_spawn_potential_uuid: bool = True) -> nbt.TagCompound:
    """Spawner block entity with a no-UUID SpawnData entity and an optionally UUID-bearing SpawnPotentials entity."""
    spawn_potentials_entity = make_entity("minecraft:skeleton", [x + 0.5, float(y), z + 0.5],
                                         with_uuid=with_spawn_potential_uuid)
    return make_block_entity("minecraft:spawner", x, y, z,
        SpawnData=nbt.TagCompound({
            "entity": nbt.TagCompound({
                "id": nbt.TagString("minecraft:zombie"),
            })
        }),
        SpawnPotentials=nbt.TagList([
            nbt.TagCompound({
                "weight": nbt.TagInt(1),
                "data": nbt.TagCompound({
                    "entity": spawn_potentials_entity,
                }),
            }),
        ]),
        MaxNearbyEntities=nbt.TagShort(6),
        RequiredPlayerRange=nbt.TagShort(16),
        SpawnCount=nbt.TagShort(4),
        SpawnRange=nbt.TagShort(4),
        Delay=nbt.TagShort(0),
        MinSpawnDelay=nbt.TagShort(200),
        MaxSpawnDelay=nbt.TagShort(800),
    )


# ---------------------------------------------------------------------------
# W1: real_world - optional, user-supplied real world (empty placeholder otherwise)
# ---------------------------------------------------------------------------

def generate_01_real_world(base_dir: str) -> None:
    d = os.path.join(base_dir, "01_real_world")
    # Never overwrite: if a real world was dropped here (e.g. `cp -a ~/NO_SNAPSHOT/white`),
    # preserve it so the copier processes real data and the validator checks UUID regen.
    if os.path.isdir(d) and os.listdir(d):
        print(f"  preserving existing {d}")
        return
    os.makedirs(d, exist_ok=True)
    print(f"  generated empty {d} (place a real world here to exercise W1)")


# ---------------------------------------------------------------------------
# W2: baseline - terrain-only, all excluded files present
# ---------------------------------------------------------------------------

def generate_02_baseline(base_dir: str) -> None:
    d = os.path.join(base_dir, "02_baseline")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)

    _save_level_dat(d, "02_baseline")

    # Excluded files that Java must not copy
    shutil.copy(os.path.join(d, "level.dat"), os.path.join(d, "level.dat_old"))
    with open(os.path.join(d, "session.lock"), "wb") as f:
        f.write(b"\x00" * 8)
    with open(os.path.join(d, "uid.dat"), "wb"):
        pass

    os.makedirs(os.path.join(d, "data"))
    with gzip.open(os.path.join(d, "data", "raids.dat"), "wb") as f:
        f.write(nbt.TagRoot.from_body(nbt.TagCompound({})).to_bytes())

    os.makedirs(os.path.join(d, "poi"))

    # Unknown dir that should be verbatim-copied
    os.makedirs(os.path.join(d, "datapacks", "vanilla"))
    with open(os.path.join(d, "datapacks", "vanilla", "pack.mcmeta"), "w", encoding="utf-8") as f:
        json.dump({"pack": {"description": "", "pack_format": 15}}, f)

    # The monumenta/ subdirectory is copied verbatim (no files need rewriting)
    os.makedirs(os.path.join(d, "monumenta"))
    with open(os.path.join(d, "monumenta", "marker.txt"), "w", encoding="utf-8") as f:
        f.write("monumenta subdir verbatim copy marker\n")

    os.makedirs(os.path.join(d, "region"))
    rf = _create_region_file(os.path.join(d, "region", "r.0.0.mca"))
    for cx in range(3):
        save_chunk(rf, make_region_chunk(cx, 0))
    rf.close()

    print(f"  generated {d}")


# ---------------------------------------------------------------------------
# W3: entities_basic - UUID regen + empty entity chunk drop
# ---------------------------------------------------------------------------

def generate_03_entities_basic(base_dir: str) -> None:
    d = os.path.join(base_dir, "03_entities_basic")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    _save_level_dat(d, "03_entities_basic")

    os.makedirs(os.path.join(d, "entities"))
    rf = _create_region_file(os.path.join(d, "entities", "r.0.0.mca"))

    zombie = make_entity("minecraft:zombie", [0.5, 64.0, 0.5])

    skeleton = make_entity("minecraft:skeleton", [0.5, 64.0, 0.5])
    creeper = make_entity("minecraft:creeper", [0.5, 64.0, 0.5],
                          Passengers=nbt.TagList([skeleton]))

    inner_entity = make_entity("minecraft:zombie", [0.0, 0.0, 0.0])
    item_with_entity_tag = nbt.TagCompound({
        "id": nbt.TagString("minecraft:zombie_spawn_egg"),
        "Count": nbt.TagByte(1),
        "tag": nbt.TagCompound({"EntityTag": inner_entity}),
    })
    armor_stand = make_entity("minecraft:armor_stand", [1.5, 64.0, 0.5],
                              HandItems=nbt.TagList([
                                  nbt.TagCompound({}),
                                  item_with_entity_tag,
                              ]))

    save_chunk(rf, make_entities_chunk(0, 0, [zombie, creeper, armor_stand]))
    save_chunk(rf, make_entities_chunk(1, 0, []))  # empty chunk
    rf.close()

    print(f"  generated {d}")


# ---------------------------------------------------------------------------
# W4: block_entities - BlockEntity edges, conditional UUID regen
# ---------------------------------------------------------------------------

def generate_04_block_entities(base_dir: str) -> None:
    d = os.path.join(base_dir, "04_block_entities")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    _save_level_dat(d, "04_block_entities")

    os.makedirs(os.path.join(d, "region"))
    rf = _create_region_file(os.path.join(d, "region", "r.0.0.mca"))

    spawner = _make_spawner(0, 64, 0)

    bee_entity = make_entity("minecraft:bee", [1.5, 64.0, 0.5])
    beehive = make_block_entity("minecraft:beehive", 1, 64, 0,
        bees=nbt.TagList([
            nbt.TagCompound({
                "entity_data": bee_entity,
                "ticks_in_hive": nbt.TagInt(0),
                "min_ticks_in_hive": nbt.TagInt(0),
            }),
        ]),
        FlowerPos=nbt.TagCompound({
            "X": nbt.TagInt(0), "Y": nbt.TagInt(65), "Z": nbt.TagInt(0)
        }),
    )

    chest = make_block_entity("minecraft:chest", 2, 64, 0,
        Items=nbt.TagList([
            nbt.TagCompound({"id": nbt.TagString("minecraft:stone"),
                             "Count": nbt.TagByte(1), "Slot": nbt.TagByte(0)}),
            nbt.TagCompound({"id": nbt.TagString("minecraft:written_book"),
                             "Count": nbt.TagByte(1), "Slot": nbt.TagByte(1),
                             "tag": nbt.TagCompound({
                                 "pages": nbt.TagList([nbt.TagString("hello")]),
                                 "title": nbt.TagString("Test"),
                                 "author": nbt.TagString("tester"),
                             })}),
            nbt.TagCompound({"id": nbt.TagString("minecraft:music_disc_13"),
                             "Count": nbt.TagByte(1), "Slot": nbt.TagByte(2)}),
        ]),
    )

    save_chunk(rf, make_region_chunk(0, 0, [spawner, beehive, chest]))
    rf.close()

    print(f"  generated {d}")


# ---------------------------------------------------------------------------
# W5: item_recursion - Item-origin edges (Item->BlockEntity->Entity, etc.)
# ---------------------------------------------------------------------------

def generate_05_item_recursion(base_dir: str) -> None:
    d = os.path.join(base_dir, "05_item_recursion")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    _save_level_dat(d, "05_item_recursion")

    os.makedirs(os.path.join(d, "region"))
    rf = _create_region_file(os.path.join(d, "region", "r.0.0.mca"))

    # Item->BlockEntity->Entity
    item_be_tag = nbt.TagCompound({
        "id": nbt.TagString("minecraft:spawner"), "Count": nbt.TagByte(1),
        "Slot": nbt.TagByte(0),
        "tag": nbt.TagCompound({
            "BlockEntityTag": make_block_entity("minecraft:spawner", 0, 0, 0,
                SpawnData=nbt.TagCompound({
                    "entity": make_entity("minecraft:zombie", [0.0, 0.0, 0.0]),
                }),
            ),
        }),
    })

    # Item->Entity
    item_entity_tag = nbt.TagCompound({
        "id": nbt.TagString("minecraft:zombie_spawn_egg"), "Count": nbt.TagByte(1),
        "Slot": nbt.TagByte(1),
        "tag": nbt.TagCompound({
            "EntityTag": make_entity("minecraft:zombie", [0.0, 0.0, 0.0]),
        }),
    })

    # Item->Item->Entity (bundle)
    inner_item = nbt.TagCompound({
        "id": nbt.TagString("minecraft:zombie_spawn_egg"), "Count": nbt.TagByte(1),
        "tag": nbt.TagCompound({
            "EntityTag": make_entity("minecraft:zombie", [0.0, 0.0, 0.0]),
        }),
    })
    item_bundle = nbt.TagCompound({
        "id": nbt.TagString("minecraft:bundle"), "Count": nbt.TagByte(1),
        "Slot": nbt.TagByte(2),
        "tag": nbt.TagCompound({
            "Items": nbt.TagList([inner_item]),
        }),
    })

    chest = make_block_entity("minecraft:chest", 0, 64, 0,
        Items=nbt.TagList([item_be_tag, item_entity_tag, item_bundle]),
    )

    save_chunk(rf, make_region_chunk(0, 0, [chest]))
    rf.close()

    print(f"  generated {d}")


# ---------------------------------------------------------------------------
# W6: compression_variants - type 1/2/3 in region/, modified + unmodified
# ---------------------------------------------------------------------------

def generate_06_compression_variants(base_dir: str) -> None:
    d = os.path.join(base_dir, "06_compression_variants")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    _save_level_dat(d, "06_compression_variants")

    os.makedirs(os.path.join(d, "region"))
    rf = _create_region_file(os.path.join(d, "region", "r.0.0.mca"))

    # Chunks 0-2: spawner with UUID (will be "modified" by copier), each compression type
    for cx, ctype in [(0, 1), (1, 2), (2, 3)]:
        spawner = _make_spawner(cx * 16, 64, 0)
        save_chunk(rf, make_region_chunk(cx, 0, [spawner]), compression_type=ctype)

    # Chunks 3-5: terrain-only (no block entities), each type
    for cx, ctype in [(3, 1), (4, 2), (5, 3)]:
        save_chunk(rf, make_region_chunk(cx, 0), compression_type=ctype)

    rf.close()

    print(f"  generated {d}")


# ---------------------------------------------------------------------------
# W7: external_mcc - oversized .mcc chunks in region/ and entities/
# ---------------------------------------------------------------------------

def generate_07_external_mcc(base_dir: str) -> None:
    d = os.path.join(base_dir, "07_external_mcc")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    _save_level_dat(d, "07_external_mcc")

    os.makedirs(os.path.join(d, "region"))

    # r.0.0.mca:
    #   (0,0) terrain-only, force_external (small chunk, just testing the flag)
    #   (1,0) spawner+UUID, force_external
    #   (2,0) genuinely oversized terrain: padded with ~1.1MB random data, auto-spills to .mcc
    rf = _create_region_file(os.path.join(d, "region", "r.0.0.mca"))
    save_chunk_external(rf, make_region_chunk(0, 0))
    save_chunk_external(rf, make_region_chunk(1, 0, [_make_spawner(1, 64, 0)]))
    big_chunk = make_region_chunk(2, 0)
    big_chunk.value["_test_padding"] = make_big_padding_tag()
    save_chunk(rf, big_chunk)  # no force_external; auto-spills because size > 255 sectors
    rf.close()

    # r.-1.-1.mca: (-32,-32) spawner+UUID external; global coords -> local (0,0)
    rf = _create_region_file(os.path.join(d, "region", "r.-1.-1.mca"))
    save_chunk_external(rf, make_region_chunk(-32, -32, [_make_spawner(-32 * 16, 64, -32 * 16)]))
    rf.close()

    os.makedirs(os.path.join(d, "entities"))

    # entities/r.0.0.mca: (0,0) entity+UUID external
    rf = _create_region_file(os.path.join(d, "entities", "r.0.0.mca"))
    entity = make_entity("minecraft:zombie", [0.5, 64.0, 0.5])
    save_chunk_external(rf, make_entities_chunk(0, 0, [entity]))
    rf.close()

    print(f"  generated {d}")


# ---------------------------------------------------------------------------
# W8: scores_world_uuid - entity scores + Bukkit world UUID stripping
# ---------------------------------------------------------------------------

def generate_08_scores_world_uuid(base_dir: str) -> None:
    d = os.path.join(base_dir, "08_scores_world_uuid")
    shutil.rmtree(d, ignore_errors=True)
    os.makedirs(d)
    _save_level_dat(d, "08_scores_world_uuid")

    # entities/: a top-level entity and a passenger, both carrying a Bukkit world UUID and
    # Monumenta entity scores. The top-level entity also carries a non-score BukkitValues entry
    # that must survive (only the scores key is stripped).
    os.makedirs(os.path.join(d, "entities"))
    rf = _create_region_file(os.path.join(d, "entities", "r.0.0.mca"))
    passenger = make_entity("minecraft:skeleton", [0.5, 64.0, 0.5],
        WorldUUIDMost=nbt.TagLong(111), WorldUUIDLeast=nbt.TagLong(222),
        BukkitValues=nbt.TagCompound({
            "monumenta:entity_scores": nbt.TagString('{"Inner":1}'),
        }),
    )
    zombie = make_entity("minecraft:zombie", [0.5, 64.0, 0.5],
        WorldUUIDMost=nbt.TagLong(333), WorldUUIDLeast=nbt.TagLong(444),
        BukkitValues=nbt.TagCompound({
            "monumenta:entity_scores": nbt.TagString('{"Foo":3,"Bar":7}'),
            "monumenta:other": nbt.TagString("keep-me"),
        }),
        Passengers=nbt.TagList([passenger]),
    )
    save_chunk(rf, make_entities_chunk(0, 0, [zombie]))
    rf.close()

    # region/: a block entity carrying a Bukkit world UUID directly, plus a UUID-bearing
    # SpawnPotentials entity that also carries a world UUID and scores (nested entity stripping).
    os.makedirs(os.path.join(d, "region"))
    rf = _create_region_file(os.path.join(d, "region", "r.0.0.mca"))
    spawner = _make_spawner(0, 64, 0)
    spawner.value["WorldUUIDMost"] = nbt.TagLong(555)
    spawner.value["WorldUUIDLeast"] = nbt.TagLong(666)
    potential_entity = spawner.value["SpawnPotentials"].value[0].value["data"].value["entity"]
    potential_entity.value["WorldUUIDMost"] = nbt.TagLong(777)
    potential_entity.value["WorldUUIDLeast"] = nbt.TagLong(888)
    potential_entity.value["BukkitValues"] = nbt.TagCompound({
        "monumenta:entity_scores": nbt.TagString('{"Nested":9}'),
    })
    save_chunk(rf, make_region_chunk(0, 0, [spawner]))
    rf.close()

    print(f"  generated {d}")


# ---------------------------------------------------------------------------

def main() -> None:
    if len(sys.argv) < 2:
        print(f"Usage: {sys.argv[0]} <inputs_dir>")
        sys.exit(1)
    base_dir = sys.argv[1]
    os.makedirs(base_dir, exist_ok=True)
    generate_01_real_world(base_dir)
    generate_02_baseline(base_dir)
    generate_03_entities_basic(base_dir)
    generate_04_block_entities(base_dir)
    generate_05_item_recursion(base_dir)
    generate_06_compression_variants(base_dir)
    generate_07_external_mcc(base_dir)
    generate_08_scores_world_uuid(base_dir)
    print("Done.")


if __name__ == "__main__":
    main()
