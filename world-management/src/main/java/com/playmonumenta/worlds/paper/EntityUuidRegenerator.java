package com.playmonumenta.worlds.paper;

import de.tr7zw.nbtapi.NBTType;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * Regenerates every entity UUID reachable from an entity or block entity so a world copy can be
 * loaded alongside its template without UUID collisions.
 *
 * Entity UUIDs live in two places: the flat entities/ region files (top-level Entities), and nested
 * inside block entities and items in the main region/ files (spawners, beehives, containers, ...).
 * Both are walked via a typed recursion whose edge tables mirror monumenta-automation's
 * _init_multipaths frozensets. A UUID is only regenerated when one is already present, so
 * spawn-template entities that legitimately lack a UUID are left untouched.
 */
public final class EntityUuidRegenerator {
	// Node types in the typed UUID-recursion graph. Only ENTITY nodes carry a regennable UUID;
	// BLOCK_ENTITY and ITEM nodes only route to children.
	private enum NodeType {
		ENTITY,
		BLOCK_ENTITY,
		ITEM
	}

	// Typed recursion edges. Path grammar: '.'-separated segments; a plain name resolves a compound
	// child, a 'name[]' suffix iterates every element of a compound list.
	private static final String[] ENTITY_TO_ENTITY = {
		"Passengers[]",
		"SpawnData", "SpawnData.entity",
		"SpawnPotentials[].Entity", "SpawnPotentials[].data.entity",
	};
	private static final String[] ENTITY_TO_ITEM = {
		"ArmorItems[]", "HandItems[]",
		"ArmorItem", "SaddleItem", "DecorItem",
		"Offers.Recipes[].buy", "Offers.Recipes[].buyB", "Offers.Recipes[].sell",
		"Item", "Items[]", "Inventory[]", "item", "FireworksItem",
	};
	private static final String[] BLOCK_ENTITY_TO_ENTITY = {
		// 1.20.4 beehive: each bees[] element wraps its stored bee entity in an entity_data compound.
		"bees[].entity_data",
		"SpawnData", "SpawnData.entity",
		"SpawnPotentials[].Entity", "SpawnPotentials[].data.entity",
	};
	private static final String[] BLOCK_ENTITY_TO_ITEM = {
		"Book", "item", "Items[]", "RecordItem",
	};
	private static final String[] ITEM_TO_ENTITY = {
		"tag.EntityTag",
	};
	private static final String[] ITEM_TO_BLOCK_ENTITY = {
		"tag.BlockEntityTag",
	};
	private static final String[] ITEM_TO_ITEM = {
		"tag.ChargedProjectiles[]", "tag.Items[]", "tag.Monumenta.PlayerModified.Items[]",
	};
	// Monumenta scoreboard data stored on an entity, under the BukkitValues compound.
	private static final String ENTITY_SCORES_KEY = "monumenta:entity_scores";

	/** Regenerates UUIDs in a top-level entity (entities/ region) and everything it carries. */
	public static boolean regenEntity(ReadWriteNBT entity) {
		return regenNode(entity, NodeType.ENTITY);
	}

	/**
	 * Regenerates UUIDs nested inside a block entity (region/ block_entities).
	 * Returns true if any UUID changed; callers use this to decide whether to re-serialize the chunk.
	 */
	public static boolean regenBlockEntity(ReadWriteNBT blockEntity) {
		return regenNode(blockEntity, NodeType.BLOCK_ENTITY);
	}

	private static boolean regenNode(ReadWriteNBT nbt, NodeType type) {
		boolean changed = false;
		switch (type) {
			case ENTITY:
				changed |= regenUuidIfPresent(nbt);
				changed |= clearWorldUuid(nbt);
				changed |= clearEntityScores(nbt);
				changed |= recurse(nbt, ENTITY_TO_ENTITY, NodeType.ENTITY);
				changed |= recurse(nbt, ENTITY_TO_ITEM, NodeType.ITEM);
				break;
			case BLOCK_ENTITY:
				changed |= clearWorldUuid(nbt);
				changed |= recurse(nbt, BLOCK_ENTITY_TO_ENTITY, NodeType.ENTITY);
				changed |= recurse(nbt, BLOCK_ENTITY_TO_ITEM, NodeType.ITEM);
				break;
			case ITEM:
				changed |= recurse(nbt, ITEM_TO_ENTITY, NodeType.ENTITY);
				changed |= recurse(nbt, ITEM_TO_BLOCK_ENTITY, NodeType.BLOCK_ENTITY);
				changed |= recurse(nbt, ITEM_TO_ITEM, NodeType.ITEM);
				break;
			default:
				break;
		}
		return changed;
	}

	private static boolean recurse(ReadWriteNBT nbt, String[] paths, NodeType childType) {
		boolean changed = false;
		for (String path : paths) {
			for (ReadWriteNBT child : resolvePath(nbt, path)) {
				changed |= regenNode(child, childType);
			}
		}
		return changed;
	}

	// Resolves a dotted path ('name[]' iterates a compound list) to the leaf compounds it reaches.
	// Missing or wrong-typed keys are silently skipped; resolving a nonexistent path leaves NBT untouched.
	private static List<ReadWriteNBT> resolvePath(ReadWriteNBT root, String path) {
		List<ReadWriteNBT> current = new ArrayList<>();
		current.add(root);
		for (String segment : path.split("\\.")) {
			boolean list = segment.endsWith("[]");
			String key = list ? segment.substring(0, segment.length() - 2) : segment;
			List<ReadWriteNBT> next = new ArrayList<>();
			for (ReadWriteNBT nbt : current) {
				if (!nbt.hasTag(key)) {
					continue;
				}
				if (list) {
					if (nbt.getType(key) != NBTType.NBTTagList || nbt.getListType(key) != NBTType.NBTTagCompound) {
						continue;
					}
					for (ReadWriteNBT child : nbt.getCompoundList(key)) {
						next.add(child);
					}
				} else if (nbt.getType(key) == NBTType.NBTTagCompound) {
					ReadWriteNBT child = nbt.getCompound(key);
					if (child != null) {
						next.add(child);
					}
				}
			}
			current = next;
			if (current.isEmpty()) {
				break;
			}
		}
		return current;
	}

	// Replaces an entity's UUID with a fresh random one, but only if it already has one. Spawn-template
	// entities (e.g. inside a spawner's SpawnData) often carry no UUID and must not be given one.
	private static boolean regenUuidIfPresent(ReadWriteNBT entity) {
		if (!entity.hasTag("UUID") && !entity.hasTag("UUIDMost") && !entity.hasTag("UUIDLeast")) {
			return false;
		}
		entity.removeKey("UUIDMost");
		entity.removeKey("UUIDLeast");
		entity.setIntArray("UUID", uuidToIntArray(UUID.randomUUID()));
		return true;
	}

	// Drops the Bukkit world-identity UUID so the copy is not tied to its template's world. Cleared
	// unconditionally (not gated on an entity UUID being present), matching the Python reference tool.
	private static boolean clearWorldUuid(ReadWriteNBT nbt) {
		boolean changed = false;
		if (nbt.hasTag("WorldUUIDMost")) {
			nbt.removeKey("WorldUUIDMost");
			changed = true;
		}
		if (nbt.hasTag("WorldUUIDLeast")) {
			nbt.removeKey("WorldUUIDLeast");
			changed = true;
		}
		return changed;
	}

	// Strips Monumenta entity scoreboard data (BukkitValues."monumenta:entity_scores") so the copy
	// starts with no stale scores, matching the Python reference tool's clear_score_data behavior.
	private static boolean clearEntityScores(ReadWriteNBT entity) {
		if (!entity.hasTag("BukkitValues") || entity.getType("BukkitValues") != NBTType.NBTTagCompound) {
			return false;
		}
		ReadWriteNBT bukkitValues = entity.getCompound("BukkitValues");
		if (bukkitValues == null || !bukkitValues.hasTag(ENTITY_SCORES_KEY)) {
			return false;
		}
		bukkitValues.removeKey(ENTITY_SCORES_KEY);
		// Drop the now-empty BukkitValues compound so the copy is byte-identical to one that never had scores.
		if (bukkitValues.getKeys().isEmpty()) {
			entity.removeKey("BukkitValues");
		}
		return true;
	}

	// Encodes a UUID as Minecraft's big-endian 4-int array (matches net.minecraft UUIDUtil).
	private static int[] uuidToIntArray(UUID uuid) {
		long msb = uuid.getMostSignificantBits();
		long lsb = uuid.getLeastSignificantBits();
		return new int[] {
			(int) (msb >> 32),
			(int) msb,
			(int) (lsb >> 32),
			(int) lsb,
		};
	}
}
