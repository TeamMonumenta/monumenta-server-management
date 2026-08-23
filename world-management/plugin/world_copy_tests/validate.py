#!/usr/bin/env python3
"""Validate (input, output) world pairs produced by WorldCopier."""
import glob
import os
import sys
from typing import Any

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "../../monumenta-automation/utility_code"))
sys.path.append(os.path.join(os.path.dirname(__file__), "../../monumenta-automation/quarry"))

from quarry.types import nbt
from _helpers import (
    assert_uuid_regenerated, assert_no_uuid, strip_uuid_family,
    nbt_structural_equal, collect_all_uuids, read_chunk_compression_type,
    is_external_chunk, BIG_CHUNK_PADDING_SIZE,
)


class SkipFixture(Exception):
    """Raised by a validator when its fixture is absent/empty and should be skipped, not failed."""


def _open_region(path: str) -> nbt.RegionFile:
    return nbt.RegionFile(path, read_only=True)


def _load_chunk(rf: nbt.RegionFile, cx: int, cz: int) -> nbt.TagCompound | None:
    c = rf.load_chunk(cx, cz)
    return c.body if c is not None else None


def _load_level_dat_data(world_path: str) -> nbt.TagCompound:
    level_path = os.path.join(world_path, "level.dat")
    nbtf = nbt.NBTFile.load(level_path)
    return nbtf.root_tag.body.value["Data"]


def _load_level_dat_name(world_path: str) -> str:
    return _load_level_dat_data(world_path).value["LevelName"].value


# ---------------------------------------------------------------------------
# UUID-walker helpers for NBT-level validation
# ---------------------------------------------------------------------------

def _get_int_array(tag: nbt.TagCompound, key: str) -> tuple[int, ...] | None:
    if key not in tag.value:
        return None
    v = tag.value[key]
    if isinstance(v, nbt.TagIntArray):
        return tuple(v.value)
    return None


def _entity_uuid(tag: nbt.TagCompound) -> tuple[int, ...] | None:
    return _get_int_array(tag, "UUID")


def _require_present(in_val: object, out_val: object, path: str) -> None:
    """If a value exists on the input, the same path must exist on the output.

    A copier that silently drops data would otherwise sail past every UUID check.
    """
    if in_val is not None:
        assert out_val is not None, f"{path}: present in input, missing from output"


def _require_same_len(in_list: nbt.TagList, out_list: nbt.TagList, path: str) -> None:
    assert len(out_list.value) == len(in_list.value), \
        f"{path}: list length changed ({len(in_list.value)} in, {len(out_list.value)} out)"


def _walk_spawner_block_entity(in_be: nbt.TagCompound, out_be: nbt.TagCompound, path: str) -> None:
    """Check SpawnData entity (conditional UUID) and SpawnPotentials entity (has UUID)."""
    if "SpawnData" in in_be.value:
        in_sd = in_be.value["SpawnData"].value.get("entity")
        if in_sd is not None:
            out_sd = out_be.value.get("SpawnData")
            _require_present(in_sd, out_sd, f"{path}.SpawnData")
            out_sd_e = out_sd.value.get("entity")
            _require_present(in_sd, out_sd_e, f"{path}.SpawnData.entity")
            if _entity_uuid(in_sd) is not None:
                assert_uuid_regenerated(in_sd, out_sd_e, f"{path}.SpawnData.entity")
            else:
                assert_no_uuid(out_sd_e, f"{path}.SpawnData.entity")

    if "SpawnPotentials" in in_be.value:
        in_pots = in_be.value["SpawnPotentials"].value
        out_pots = out_be.value.get("SpawnPotentials")
        _require_present(in_be.value["SpawnPotentials"], out_pots, f"{path}.SpawnPotentials")
        _require_same_len(in_be.value["SpawnPotentials"], out_pots, f"{path}.SpawnPotentials")
        for i, (in_pot, out_pot) in enumerate(zip(in_pots, out_pots.value)):
            in_e = in_pot.value.get("data", nbt.TagCompound({})).value.get("entity")
            out_e = out_pot.value.get("data", nbt.TagCompound({})).value.get("entity")
            _require_present(in_e, out_e, f"{path}.SpawnPotentials[{i}].data.entity")
            if in_e is not None and _entity_uuid(in_e) is not None:
                assert_uuid_regenerated(in_e, out_e, f"{path}.SpawnPotentials[{i}].data.entity")


def _walk_entity_uuid_chain(in_e: nbt.TagCompound, out_e: nbt.TagCompound, path: str) -> None:
    """Recursively assert UUIDs are regenerated in an entity and its nested entities/items."""
    if _entity_uuid(in_e) is not None:
        assert_uuid_regenerated(in_e, out_e, path)

    # Entity -> Entity: Passengers[]
    in_pass = in_e.value.get("Passengers")
    if in_pass is not None:
        out_pass = out_e.value.get("Passengers")
        _require_present(in_pass, out_pass, f"{path}.Passengers")
        _require_same_len(in_pass, out_pass, f"{path}.Passengers")
        for i, (ip, op) in enumerate(zip(in_pass.value, out_pass.value)):
            _walk_entity_uuid_chain(ip, op, f"{path}.Passengers[{i}]")

    # Entity -> Item -> Entity: HandItems[], ArmorItems[], etc.
    for item_key in ("HandItems", "ArmorItems", "Items", "Inventory"):
        in_items = in_e.value.get(item_key)
        if in_items is not None:
            out_items = out_e.value.get(item_key)
            _require_present(in_items, out_items, f"{path}.{item_key}")
            _require_same_len(in_items, out_items, f"{path}.{item_key}")
            for i, (ii, oi) in enumerate(zip(in_items.value, out_items.value)):
                _walk_item_uuid_chain(ii, oi, f"{path}.{item_key}[{i}]")


def _walk_item_uuid_chain(in_i: nbt.TagCompound, out_i: nbt.TagCompound, path: str) -> None:
    """Recursively check UUID regen in items and their nested entities/block-entities."""
    if not in_i.value:
        return  # empty item slot

    in_tag = in_i.value.get("tag")
    if in_tag is None:
        return
    out_tag = out_i.value.get("tag")
    _require_present(in_tag, out_tag, f"{path}.tag")

    # Item -> Entity
    in_et = in_tag.value.get("EntityTag")
    if in_et is not None:
        out_et = out_tag.value.get("EntityTag")
        _require_present(in_et, out_et, f"{path}.tag.EntityTag")
        _walk_entity_uuid_chain(in_et, out_et, f"{path}.tag.EntityTag")

    # Item -> BlockEntity -> Entity (spawner SpawnData)
    in_bet = in_tag.value.get("BlockEntityTag")
    if in_bet is not None:
        out_bet = out_tag.value.get("BlockEntityTag")
        _require_present(in_bet, out_bet, f"{path}.tag.BlockEntityTag")
        _walk_spawner_block_entity(in_bet, out_bet, f"{path}.tag.BlockEntityTag")

    # Item -> Item[]
    in_nested = in_tag.value.get("Items")
    if in_nested is not None:
        out_nested = out_tag.value.get("Items")
        _require_present(in_nested, out_nested, f"{path}.tag.Items")
        _require_same_len(in_nested, out_nested, f"{path}.tag.Items")
        for i, (ii, oi) in enumerate(zip(in_nested.value, out_nested.value)):
            _walk_item_uuid_chain(ii, oi, f"{path}.tag.Items[{i}]")


# ---------------------------------------------------------------------------
# W1: real_world (optional)
# ---------------------------------------------------------------------------

def _assert_chunks_uuid_only_diff(in_path: str, out_path: str, sub: str) -> None:
    """Every input chunk in sub/ must exist in output and be identical after stripping UUIDs.

    Tolerates the one documented copier difference: Java may drop empty entity chunks.
    """
    in_dir = os.path.join(in_path, sub)
    if not os.path.isdir(in_dir):
        return
    for fname in sorted(os.listdir(in_dir)):
        if not fname.endswith(".mca"):
            continue
        out_file = os.path.join(out_path, sub, fname)
        assert os.path.exists(out_file), f"W1: {sub}/{fname} missing from output"
        rf_in = _open_region(os.path.join(in_dir, fname))
        rf_out = _open_region(out_file)
        try:
            for cx, cz in rf_in.list_chunks():
                in_body = _load_chunk(rf_in, cx, cz)
                out_body = _load_chunk(rf_out, cx, cz)
                if out_body is None:
                    in_ents = in_body.value.get("Entities") if in_body is not None else None
                    assert in_ents is not None and len(in_ents.value) == 0, \
                        f"W1: {sub}/{fname} chunk ({cx},{cz}) missing from output"
                    continue
                assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
                    f"W1: {sub}/{fname} chunk ({cx},{cz}) differs beyond UUIDs"
        finally:
            rf_in.close()
            rf_out.close()


def validate_01_real_world(in_path: str, out_path: str) -> None:
    # Optional fixture: an empty folder unless a real world was placed here. Skip cleanly if empty.
    if not os.path.isfile(os.path.join(in_path, "level.dat")):
        raise SkipFixture("no world placed; copy ~/NO_SNAPSHOT/white here to exercise")

    in_uuids = collect_all_uuids(in_path)
    assert in_uuids, "W1: placed world has no entity UUIDs to regenerate"
    out_uuids = collect_all_uuids(out_path)
    # Every entity UUID regenerated: none survive, and none are dropped (count preserved).
    assert in_uuids.isdisjoint(out_uuids), \
        f"W1: {len(in_uuids & out_uuids)} input UUID(s) survived unchanged into output"
    assert len(out_uuids) == len(in_uuids), \
        f"W1: UUID count changed ({len(in_uuids)} in, {len(out_uuids)} out)"
    # Everything else byte-for-byte structurally unchanged (only UUIDs differ).
    _assert_chunks_uuid_only_diff(in_path, out_path, "region")
    _assert_chunks_uuid_only_diff(in_path, out_path, "entities")


# ---------------------------------------------------------------------------
# W2: baseline
# ---------------------------------------------------------------------------

def validate_02_baseline(in_path: str, out_path: str) -> None:
    # Non-whitelisted entries are dropped by both copiers: server-managed scratch state, per-world
    # runtime data, and datapacks/ (a sub-world is not expected to carry its own datapacks).
    for name in ("data", "poi", "level.dat_old", "session.lock", "uid.dat", "datapacks"):
        assert not os.path.exists(os.path.join(out_path, name)), \
            f"W2: non-whitelisted entry '{name}' must not appear in output"

    # monumenta/ is on the copy whitelist and must be copied verbatim by both copiers.
    rel = ("monumenta", "marker.txt")
    f_in = os.path.join(in_path, *rel)
    f_out = os.path.join(out_path, *rel)
    assert os.path.exists(f_out), "W2: monumenta/ must be copied to output"
    with open(f_in, "rb") as fi, open(f_out, "rb") as fo:
        assert fi.read() == fo.read(), f"W2: {os.path.join(*rel)} not byte-identical"

    assert _load_level_dat_name(out_path) == "02_baseline", \
        "W2: output LevelName must be '02_baseline'"

    # All other level.dat Data must be unchanged (only LevelName is rewritten)
    in_data = {k: v for k, v in _load_level_dat_data(in_path).value.items() if k != "LevelName"}
    out_data = {k: v for k, v in _load_level_dat_data(out_path).value.items() if k != "LevelName"}
    assert nbt_structural_equal(nbt.TagCompound(in_data), nbt.TagCompound(out_data)), \
        "W2: level.dat Data changed beyond LevelName"

    rf_in = _open_region(os.path.join(in_path, "region", "r.0.0.mca"))
    rf_out = _open_region(os.path.join(out_path, "region", "r.0.0.mca"))
    for cx, cz in rf_in.list_chunks():
        in_body = _load_chunk(rf_in, cx, cz)
        out_body = _load_chunk(rf_out, cx, cz)
        assert out_body is not None, f"W2: chunk ({cx},{cz}) missing from output"
        assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
            f"W2: chunk ({cx},{cz}) NBT differs"
    rf_in.close()
    rf_out.close()

    assert collect_all_uuids(in_path) == set(), "W2: input should have no UUIDs"
    assert collect_all_uuids(out_path) == set(), "W2: output should have no UUIDs"


# ---------------------------------------------------------------------------
# W3: entities_basic
# ---------------------------------------------------------------------------

def validate_03_entities_basic(in_path: str, out_path: str) -> None:
    rf_out = _open_region(os.path.join(out_path, "entities", "r.0.0.mca"))

    # Java drops empty entity chunks; Python keeps them. Accept both.
    out_empty = _load_chunk(rf_out, 1, 0)
    if out_empty is not None:
        empty_ents = out_empty.value.get("Entities")
        assert empty_ents is None or len(empty_ents.value) == 0, \
            "W3: chunk (1,0) is present but has non-empty Entities list"

    # Load paired chunks
    rf_in = _open_region(os.path.join(in_path, "entities", "r.0.0.mca"))
    in_body = _load_chunk(rf_in, 0, 0)
    out_body = _load_chunk(rf_out, 0, 0)
    rf_in.close()
    rf_out.close()

    assert in_body is not None and out_body is not None
    in_ents = in_body.value["Entities"].value
    out_ents = out_body.value["Entities"].value
    assert len(in_ents) == len(out_ents), "W3: entity count mismatch"

    for i, (in_e, out_e) in enumerate(zip(in_ents, out_ents)):
        _walk_entity_uuid_chain(in_e, out_e, f"Entities[{i}]")

    # Structural equality after stripping UUIDs
    assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
        "W3: chunk body differs after stripping UUIDs"


# ---------------------------------------------------------------------------
# W4: block_entities
# ---------------------------------------------------------------------------

def validate_04_block_entities(in_path: str, out_path: str) -> None:
    rf_in = _open_region(os.path.join(in_path, "region", "r.0.0.mca"))
    rf_out = _open_region(os.path.join(out_path, "region", "r.0.0.mca"))
    in_body = _load_chunk(rf_in, 0, 0)
    out_body = _load_chunk(rf_out, 0, 0)
    rf_in.close()
    rf_out.close()

    assert in_body is not None and out_body is not None, "W4: chunk (0,0) missing"

    in_bes = in_body.value["block_entities"].value
    out_bes = out_body.value["block_entities"].value
    assert len(in_bes) == len(out_bes) == 3, f"W4: expected 3 block entities, got {len(out_bes)}"

    in_spawner, out_spawner = in_bes[0], out_bes[0]
    _walk_spawner_block_entity(in_spawner, out_spawner, "block_entities[0](spawner)")

    # Beehive: bees[0].EntityData UUID
    in_beehive, out_beehive = in_bes[1], out_bes[1]
    in_bees = in_beehive.value.get("bees")
    if in_bees is not None and in_bees.value:
        out_bees = out_beehive.value.get("bees")
        _require_present(in_bees, out_bees, "block_entities[1](beehive).bees")
        _require_same_len(in_bees, out_bees, "block_entities[1](beehive).bees")
        in_bee_e = in_bees.value[0].value.get("entity_data")
        if in_bee_e is not None:
            out_bee_e = out_bees.value[0].value.get("entity_data")
            _require_present(in_bee_e, out_bee_e, "block_entities[1](beehive).bees[0].entity_data")
            if _entity_uuid(in_bee_e) is not None:
                assert_uuid_regenerated(in_bee_e, out_bee_e,
                                            "block_entities[1](beehive).bees[0].entity_data")

    # Chest items: no UUIDs, structural equality
    in_chest, out_chest = in_bes[2], out_bes[2]
    assert nbt_structural_equal(strip_uuid_family(in_chest), strip_uuid_family(out_chest)), \
        "W4: chest NBT differs"

    # Whole-chunk structural equality: spawner/beehive non-UUID fields, FlowerPos, ticks, etc.
    assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
        "W4: chunk NBT differs after stripping UUIDs"


# ---------------------------------------------------------------------------
# W5: item_recursion
# ---------------------------------------------------------------------------

def validate_05_item_recursion(in_path: str, out_path: str) -> None:
    rf_in = _open_region(os.path.join(in_path, "region", "r.0.0.mca"))
    rf_out = _open_region(os.path.join(out_path, "region", "r.0.0.mca"))
    in_body = _load_chunk(rf_in, 0, 0)
    out_body = _load_chunk(rf_out, 0, 0)
    rf_in.close()
    rf_out.close()

    assert in_body is not None and out_body is not None

    in_bes = in_body.value["block_entities"].value
    out_bes = out_body.value["block_entities"].value
    assert len(in_bes) == len(out_bes) == 1

    in_chest = in_bes[0]
    out_chest = out_bes[0]

    in_items = in_chest.value["Items"].value
    out_items = out_chest.value["Items"].value
    assert len(in_items) == len(out_items) == 3

    # Item 0: Item->BlockEntity->Entity (spawner SpawnData entity with UUID)
    _walk_item_uuid_chain(in_items[0], out_items[0], "Items[0]")
    # Item 1: Item->Entity
    _walk_item_uuid_chain(in_items[1], out_items[1], "Items[1]")
    # Item 2: Item->Item->Entity (bundle Items[])
    _walk_item_uuid_chain(in_items[2], out_items[2], "Items[2]")

    assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
        "W5: chunk NBT differs after stripping UUIDs"


# ---------------------------------------------------------------------------
# W6: compression_variants
# ---------------------------------------------------------------------------

def validate_06_compression_variants(in_path: str, out_path: str) -> None:
    in_region = os.path.join(in_path, "region", "r.0.0.mca")
    out_region = os.path.join(out_path, "region", "r.0.0.mca")

    # Verify input has the expected compression types
    for cx, expected in [(0, 1), (1, 2), (2, 3), (3, 1), (4, 2), (5, 3)]:
        actual = read_chunk_compression_type(in_region, cx, 0)
        assert actual == expected, \
            f"W6: input chunk ({cx},0) has compression type {actual}, expected {expected}"

    rf_in = _open_region(in_region)
    rf_out = _open_region(out_region)

    # Modified chunks (0-2, have spawner UUID): output must be type 2, UUID changed
    for cx in range(3):
        out_type = read_chunk_compression_type(out_region, cx, 0)
        assert out_type == 2, \
            f"W6: modified chunk ({cx},0) output compression type {out_type}, expected 2"
        in_body = _load_chunk(rf_in, cx, 0)
        out_body = _load_chunk(rf_out, cx, 0)
        assert in_body is not None and out_body is not None
        in_be = in_body.value["block_entities"].value[0]
        out_be = out_body.value["block_entities"].value[0]
        _walk_spawner_block_entity(in_be, out_be, f"chunk({cx},0).block_entities[0]")

    # Unmodified chunks (3-5, terrain-only): NBT content must be preserved.
    # Python copy_world rewrites all chunks as type 2; Java preserves original type.
    # Only NBT equality is asserted here, not compression type.
    for cx in [3, 4, 5]:
        in_body = _load_chunk(rf_in, cx, 0)
        out_body = _load_chunk(rf_out, cx, 0)
        assert in_body is not None and out_body is not None
        assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
            f"W6: unmodified chunk ({cx},0) NBT changed"

    rf_in.close()
    rf_out.close()


# ---------------------------------------------------------------------------
# W7: external_mcc
# ---------------------------------------------------------------------------

def validate_07_external_mcc(in_path: str, out_path: str) -> None:
    # r.0.0.mca chunk (0,0): force-external terrain -> may be inline after Python copy; check content
    in_r00 = os.path.join(in_path, "region", "r.0.0.mca")
    out_r00 = os.path.join(out_path, "region", "r.0.0.mca")
    rf_in = _open_region(in_r00)
    rf_out = _open_region(out_r00)
    in_body = _load_chunk(rf_in, 0, 0)
    out_body = _load_chunk(rf_out, 0, 0)
    assert in_body is not None and out_body is not None, "W7: r.0.0 chunk(0,0) missing"
    assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
        "W7: r.0.0 chunk(0,0) terrain NBT changed"

    # r.0.0.mca chunk (1,0): force-external spawner+UUID -> UUID changed regardless of inline/external
    in_body = _load_chunk(rf_in, 1, 0)
    out_body = _load_chunk(rf_out, 1, 0)
    assert in_body is not None and out_body is not None, "W7: r.0.0 chunk(1,0) missing"
    in_be = in_body.value["block_entities"].value[0]
    out_be = out_body.value["block_entities"].value[0]
    _walk_spawner_block_entity(in_be, out_be, "r.0.0.chunk(1,0).block_entities[0]")
    assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
        "W7: r.0.0 chunk(1,0) NBT differs after stripping UUIDs"

    # r.0.0.mca chunk (2,0): genuinely oversized (padded ~1.1MB) -> stays external; padding preserved
    assert is_external_chunk(out_r00, 2, 0), \
        "W7: r.0.0 chunk(2,0) (genuinely oversized) must be stored externally"
    in_body = _load_chunk(rf_in, 2, 0)
    out_body = _load_chunk(rf_out, 2, 0)
    rf_in.close()
    rf_out.close()
    assert in_body is not None and out_body is not None, "W7: r.0.0 chunk(2,0) missing"
    assert "_test_padding" in out_body.value, "W7: chunk(2,0) missing _test_padding"
    assert len(out_body.value["_test_padding"].value) == BIG_CHUNK_PADDING_SIZE, \
        "W7: chunk(2,0) _test_padding has wrong length"
    # Full content check: the ~1.1MB payload must survive the compress -> external write ->
    # read -> decompress round-trip byte-for-byte (not just keep its length).
    assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
        "W7: chunk(2,0) NBT differs after round-trip (padding bytes or terrain changed)"

    # r.-1.-1.mca chunk (0,0) [global -32,-32]: spawner+UUID; UUID changed
    in_rm1 = os.path.join(in_path, "region", "r.-1.-1.mca")
    out_rm1 = os.path.join(out_path, "region", "r.-1.-1.mca")
    rf_in = _open_region(in_rm1)
    rf_out = _open_region(out_rm1)
    in_body = _load_chunk(rf_in, 0, 0)
    out_body = _load_chunk(rf_out, 0, 0)
    rf_in.close()
    rf_out.close()
    assert in_body is not None and out_body is not None, "W7: r.-1.-1 chunk(0,0) missing"
    in_be = in_body.value["block_entities"].value[0]
    out_be = out_body.value["block_entities"].value[0]
    _walk_spawner_block_entity(in_be, out_be, "r.-1.-1.chunk(0,0).block_entities[0]")
    assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
        "W7: r.-1.-1 chunk(0,0) NBT differs after stripping UUIDs"

    # entities/r.0.0.mca chunk (0,0): entity+UUID; UUID changed
    in_e00 = os.path.join(in_path, "entities", "r.0.0.mca")
    out_e00 = os.path.join(out_path, "entities", "r.0.0.mca")
    rf_in = _open_region(in_e00)
    rf_out = _open_region(out_e00)
    in_body = _load_chunk(rf_in, 0, 0)
    out_body = _load_chunk(rf_out, 0, 0)
    rf_in.close()
    rf_out.close()
    assert in_body is not None and out_body is not None, "W7: entities/r.0.0 chunk(0,0) missing"
    in_ents = in_body.value["Entities"].value
    out_ents = out_body.value["Entities"].value
    assert len(in_ents) == len(out_ents) == 1
    _walk_entity_uuid_chain(in_ents[0], out_ents[0], "entities/r.0.0.Entities[0]")
    assert nbt_structural_equal(strip_uuid_family(in_body), strip_uuid_family(out_body)), \
        "W7: entities/r.0.0 chunk(0,0) NBT differs after stripping UUIDs"

    # Only the genuinely oversized chunk should remain as .mcc; force-external small chunks become inline
    expected_mccs = {os.path.join(out_path, "region", "c.2.0.mcc")}
    actual_mccs = set(glob.glob(os.path.join(out_path, "region", "*.mcc")))
    assert actual_mccs == expected_mccs, \
        f"W7: unexpected .mcc files in region/: {actual_mccs ^ expected_mccs}"


# ---------------------------------------------------------------------------
# W8: scores_world_uuid
# ---------------------------------------------------------------------------

# Keys both copiers must strip from every entity/block entity during a copy.
_STRIPPED_KEYS = frozenset({"WorldUUIDMost", "WorldUUIDLeast", "monumenta:entity_scores"})


def _assert_keys_absent_recursive(tag: Any, keys: frozenset[str], path: str) -> None:
    """Assert none of `keys` appears anywhere in the tag tree (compound keys at any depth)."""
    if isinstance(tag, nbt.TagCompound):
        for k in keys:
            assert k not in tag.value, f"{path}: '{k}' should have been stripped by the copier"
        for k, v in tag.value.items():
            _assert_keys_absent_recursive(v, keys, f"{path}.{k}")
    elif isinstance(tag, nbt.TagList):
        for i, e in enumerate(tag.value):
            _assert_keys_absent_recursive(e, keys, f"{path}[{i}]")


def validate_08_scores_world_uuid(in_path: str, out_path: str) -> None:
    # entities/: top-level entity + passenger, both stripped of world UUID and scores
    rf_in = _open_region(os.path.join(in_path, "entities", "r.0.0.mca"))
    rf_out = _open_region(os.path.join(out_path, "entities", "r.0.0.mca"))
    in_body = _load_chunk(rf_in, 0, 0)
    out_body = _load_chunk(rf_out, 0, 0)
    rf_in.close()
    rf_out.close()
    assert in_body is not None and out_body is not None, "W8: entities chunk(0,0) missing"

    in_ents = in_body.value["Entities"].value
    out_ents = out_body.value["Entities"].value
    assert len(in_ents) == len(out_ents) == 1, "W8: entity count mismatch"
    _walk_entity_uuid_chain(in_ents[0], out_ents[0], "W8.entities[0]")
    _assert_keys_absent_recursive(out_body, _STRIPPED_KEYS, "W8.entities")

    # A non-score BukkitValues entry must survive; only the scores key is stripped.
    zombie = out_ents[0]
    assert "BukkitValues" in zombie.value, "W8: zombie BukkitValues dropped despite a surviving entry"
    assert "monumenta:other" in zombie.value["BukkitValues"].value, \
        "W8: non-score BukkitValues entry was incorrectly removed"
    # The passenger's only BukkitValues entry was scores, so the now-empty compound must be gone.
    passenger = zombie.value["Passengers"].value[0]
    assert "BukkitValues" not in passenger.value, \
        "W8: empty BukkitValues should have been removed from passenger"

    # region/: block entity carrying a world UUID, plus a nested SpawnPotentials entity
    rf_in = _open_region(os.path.join(in_path, "region", "r.0.0.mca"))
    rf_out = _open_region(os.path.join(out_path, "region", "r.0.0.mca"))
    in_body = _load_chunk(rf_in, 0, 0)
    out_body = _load_chunk(rf_out, 0, 0)
    rf_in.close()
    rf_out.close()
    assert in_body is not None and out_body is not None, "W8: region chunk(0,0) missing"

    in_be = in_body.value["block_entities"].value[0]
    out_be = out_body.value["block_entities"].value[0]
    _walk_spawner_block_entity(in_be, out_be, "W8.region.spawner")
    _assert_keys_absent_recursive(out_body, _STRIPPED_KEYS, "W8.region")


# ---------------------------------------------------------------------------
# Harness
# ---------------------------------------------------------------------------

FIXTURES = [
    ("01_real_world",              validate_01_real_world),
    ("02_baseline",                validate_02_baseline),
    ("03_entities_basic",          validate_03_entities_basic),
    ("04_block_entities",          validate_04_block_entities),
    ("05_item_recursion",          validate_05_item_recursion),
    ("06_compression_variants",    validate_06_compression_variants),
    ("07_external_mcc",            validate_07_external_mcc),
    ("08_scores_world_uuid",       validate_08_scores_world_uuid),
]


def main() -> None:
    if len(sys.argv) < 3:
        print(f"Usage: {sys.argv[0]} <inputs_dir> <outputs_dir>")
        sys.exit(1)
    inputs_dir, outputs_dir = sys.argv[1], sys.argv[2]
    passed = failed = skipped = 0
    for name, fn in FIXTURES:
        in_path = os.path.join(inputs_dir, name)
        out_path = os.path.join(outputs_dir, name)
        try:
            fn(in_path, out_path)
            # Core contract for every fixture: no output UUID equals any input UUID,
            # so a copy can load alongside its template.
            in_uuids = collect_all_uuids(in_path)
            out_uuids = collect_all_uuids(out_path)
            assert in_uuids.isdisjoint(out_uuids), \
                f"{name}: UUID collision between input and output: {in_uuids & out_uuids}"
            print(f"  PASS  {name}")
            passed += 1
        except SkipFixture as e:
            print(f"  SKIP  {name}: {e}")
            skipped += 1
        except Exception as e:  # pylint: disable=broad-exception-caught
            print(f"  FAIL  {name}: {type(e).__name__}: {e}")
            failed += 1
    print(f"\n{passed} passed, {failed} failed, {skipped} skipped")
    sys.exit(0 if failed == 0 else 1)


if __name__ == "__main__":
    main()
