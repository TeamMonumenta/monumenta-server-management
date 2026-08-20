"""Shared NBT construction and validation helpers for world copy tests."""
import os
import random
import struct
import sys
import uuid
from typing import Any

# Constants for the genuinely-oversized chunk in W6
_BIG_CHUNK_SEED = 42
BIG_CHUNK_PADDING_SIZE = 1_100_000  # bytes; enough to exceed 255 sectors when zlib-compressed

_AUTO = os.path.join(os.path.dirname(__file__), "../../monumenta-automation/utility_code")
if _AUTO not in sys.path:
    sys.path.insert(0, _AUTO)

from quarry.types import nbt  # type: ignore[import]  # pylint: disable=import-error
from quarry.types.chunk import PackedArray  # type: ignore[import]  # pylint: disable=import-error


def _int_array(values: list[int]) -> nbt.TagIntArray:
    """Create a TagIntArray from a plain Python list of ints."""
    return nbt.TagIntArray(PackedArray.from_int_list(values, 32))


def make_big_padding_tag() -> nbt.TagByteArray:
    """Generate a reproducible ~1.1MB TagByteArray for oversized-chunk testing.

    The chunk carries this as a synthetic `_test_padding` tag - not a real Minecraft tag,
    purely scaffolding to force the chunk past 255 sectors so it spills to an external .mcc.
    """
    rng = random.Random(_BIG_CHUNK_SEED)
    data = rng.randbytes(BIG_CHUNK_PADDING_SIZE)
    return nbt.TagByteArray(PackedArray.from_bytes(data, BIG_CHUNK_PADDING_SIZE, 8, 8))


# ---------------------------------------------------------------------------
# NBT factory helpers (used by generate.py)
# ---------------------------------------------------------------------------

def fresh_uuid_int_array() -> nbt.TagIntArray:
    """Returns a random UUID as a TagIntArray of 4 signed 32-bit ints."""
    u = uuid.uuid4()
    raw = u.int
    ints = [
        (raw >> 96) & 0xFFFFFFFF,
        (raw >> 64) & 0xFFFFFFFF,
        (raw >> 32) & 0xFFFFFFFF,
        raw & 0xFFFFFFFF,
    ]
    signed = [i - 0x100000000 if i >= 0x80000000 else i for i in ints]
    return _int_array(signed)


def make_entity(id_: str, pos: list[float], with_uuid: bool = True,
                **extra: Any) -> nbt.TagCompound:
    """Minimal entity compound. extra overrides/extends the defaults."""
    d: dict[str, Any] = {
        "id": nbt.TagString(id_),
        "Pos": nbt.TagList([nbt.TagDouble(x) for x in pos]),
        "Motion": nbt.TagList([nbt.TagDouble(0.0)] * 3),
        "Rotation": nbt.TagList([nbt.TagFloat(0.0), nbt.TagFloat(0.0)]),
        "FallDistance": nbt.TagFloat(0.0),
        "Fire": nbt.TagShort(0),
        "Air": nbt.TagShort(300),
        "OnGround": nbt.TagByte(0),
        "Invulnerable": nbt.TagByte(0),
        "PortalCooldown": nbt.TagInt(0),
    }
    if with_uuid:
        d["UUID"] = fresh_uuid_int_array()
    d.update(extra)
    return nbt.TagCompound(d)


def make_block_entity(id_: str, x: int, y: int, z: int,
                      **extra: Any) -> nbt.TagCompound:
    d: dict[str, Any] = {
        "id": nbt.TagString(id_),
        "x": nbt.TagInt(x),
        "y": nbt.TagInt(y),
        "z": nbt.TagInt(z),
        "keepPacked": nbt.TagByte(0),
    }
    d.update(extra)
    return nbt.TagCompound(d)


def make_region_chunk(cx: int, cz: int,
                      block_entities: list[nbt.TagCompound] | None = None) -> nbt.TagCompound:
    """Minimal valid 1.20.4 region chunk."""
    return nbt.TagCompound({
        "DataVersion": nbt.TagInt(3700),
        "xPos": nbt.TagInt(cx),
        "zPos": nbt.TagInt(cz),
        "yPos": nbt.TagInt(-4),
        "Status": nbt.TagString("minecraft:full"),
        "LastUpdate": nbt.TagLong(0),
        "sections": nbt.TagList([]),
        "block_entities": nbt.TagList(block_entities or []),
        "Heightmaps": nbt.TagCompound({}),
        "blending_data": nbt.TagCompound({
            "min_section": nbt.TagInt(-4),
            "max_section": nbt.TagInt(20),
        }),
    })


def make_entities_chunk(cx: int, cz: int,
                        entities: list[nbt.TagCompound] | None = None) -> nbt.TagCompound:
    """Minimal valid 1.20.4 entities chunk."""
    return nbt.TagCompound({
        "DataVersion": nbt.TagInt(3700),
        "Position": _int_array([cx, cz]),
        "Entities": nbt.TagList(entities or []),
    })


def save_chunk(region_file: nbt.RegionFile, body: nbt.TagCompound,
               compression_type: int = 2) -> None:
    """Save a chunk to a region file. Coordinates come from the body's xPos/zPos
    (region chunk) or Position (entities chunk)."""
    region_file.save_chunk(nbt.TagRoot.from_body(body), compression_type=compression_type)


def save_chunk_external(region_file: nbt.RegionFile, body: nbt.TagCompound) -> None:
    """Save a chunk forced into an external .mcc file, regardless of its size."""
    region_file.save_chunk(nbt.TagRoot.from_body(body), force_external=True)


# ---------------------------------------------------------------------------
# Validation helpers (used by validate.py)
# ---------------------------------------------------------------------------

UUID_FAMILY = frozenset({"UUID", "UUIDMost", "UUIDLeast", "WorldUUIDMost", "WorldUUIDLeast"})


def has_uuid(tag: nbt.TagCompound) -> bool:
    return any(k in tag.value for k in UUID_FAMILY)


def get_uuid_ints(tag: nbt.TagCompound) -> tuple[int, ...] | None:
    """Return UUID as a tuple of 4 ints, or None if absent."""
    if "UUID" in tag.value:
        return tuple(tag.value["UUID"].value)
    return None


def assert_uuid_regenerated(in_tag: nbt.TagCompound, out_tag: nbt.TagCompound,
                             path: str) -> None:
    in_uuid = get_uuid_ints(in_tag)
    out_uuid = get_uuid_ints(out_tag)
    assert in_uuid is not None, f"{path}: input has no UUID (expected one)"
    assert out_uuid is not None, f"{path}: output missing UUID"
    assert in_uuid != out_uuid, f"{path}: UUID not regenerated (still {in_uuid})"


def assert_no_uuid(tag: nbt.TagCompound, path: str) -> None:
    for k in UUID_FAMILY:
        assert k not in tag.value, f"{path}: unexpected UUID key '{k}'"


def strip_uuid_family(tag: Any) -> Any:
    """Deep-copy an NBT tag tree with all UUID-family keys removed."""
    if isinstance(tag, nbt.TagCompound):
        return nbt.TagCompound({
            k: strip_uuid_family(v)
            for k, v in tag.value.items()
            if k not in UUID_FAMILY
        })
    if isinstance(tag, nbt.TagList):
        return nbt.TagList([strip_uuid_family(e) for e in tag.value])
    return tag  # scalars are immutable


def nbt_structural_equal(a: object, b: object) -> bool:
    return a == b  # quarry tags implement __eq__


def collect_all_uuids(world_path: str) -> set[tuple[int, ...]]:
    """Walk all region and entities files; return every entity-identity UUID int-array found.

    Only UUIDs on a compound that also carries an `id` (i.e. an entity) are collected. This
    excludes UUIDs the copier intentionally leaves stable, such as item `AttributeModifiers[].UUID`,
    which are not entity identities and do not cause cross-world collisions.
    """
    result: set[tuple[int, ...]] = set()
    # Only region/ and entities/ hold entity-identity UUIDs and are the only dirs the copier
    # rewrites. Other subtrees (poi/, data/) use different chunk formats that the chunk loader
    # cannot parse and are excluded from the copy anyway, so they are skipped entirely.
    for sub in ("region", "entities"):
        sub_dir = os.path.join(world_path, sub)
        if not os.path.isdir(sub_dir):
            continue
        for fname in sorted(os.listdir(sub_dir)):
            if not fname.endswith(".mca"):
                continue
            fpath = os.path.join(sub_dir, fname)
            try:
                rf = nbt.RegionFile(fpath, read_only=True)
                for cx, cz in rf.list_chunks():
                    chunk = rf.load_chunk(cx, cz)
                    if chunk is not None:
                        _collect_uuids_tag(chunk.body, result)
                rf.close()
            except Exception:  # pylint: disable=broad-exception-caught
                pass  # unreadable region files are skipped; this is a best-effort scan
    return result


def _collect_uuids_tag(tag: Any, result: set[tuple[int, ...]]) -> None:
    if isinstance(tag, nbt.TagCompound):
        if "UUID" in tag.value and "id" in tag.value:
            v = tag.value["UUID"]
            if isinstance(v, nbt.TagIntArray):
                result.add(tuple(v.value))
        for v in tag.value.values():
            _collect_uuids_tag(v, result)
    elif isinstance(tag, nbt.TagList):
        for e in tag.value:
            _collect_uuids_tag(e, result)


def read_chunk_compression_type(region_path: str, local_cx: int, local_cz: int) -> int:
    """Read the compression type byte from a raw region file for a local chunk coordinate."""
    with open(region_path, "rb") as f:
        f.seek(4 * (32 * local_cz + local_cx))
        entry = struct.unpack(">I", f.read(4))[0]
        offset = entry >> 8
        if offset == 0:
            raise KeyError(f"No chunk at local ({local_cx}, {local_cz})")
        f.seek(4096 * offset + 4)  # skip 4-byte length
        return struct.unpack("B", f.read(1))[0]


def is_external_chunk(region_path: str, local_cx: int, local_cz: int) -> bool:
    with open(region_path, "rb") as f:
        f.seek(4 * (32 * local_cz + local_cx))
        entry = struct.unpack(">I", f.read(4))[0]
        offset = entry >> 8
        if offset == 0:
            return False
        f.seek(4096 * offset)
        size_and_type = struct.unpack(">IB", f.read(5))
        return size_and_type == (1, 130)
