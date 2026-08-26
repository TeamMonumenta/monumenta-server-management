package com.playmonumenta.worlds.adapters;

import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import java.io.IOException;
import java.nio.file.Path;

/*
 * Version-specific access to Minecraft's on-disk world storage.
 *
 * Everything about the Anvil container format - sector allocation, per-chunk compression types,
 * oversized chunks spilling to sibling .mcc files, header and timestamp layout - lives behind this
 * interface, implemented per Minecraft version on top of that version's own storage classes. Callers
 * see only chunk coordinates and NBT, so they never need updating when the format changes.
 *
 * Implementations are loaded reflectively by CraftBukkit version (see WorldStorageAdapters) and must
 * be named WorldStorageAdapter_<version> in this package with a public no-argument constructor.
 */
public interface WorldStorageAdapter {
	/**
	 * Opens an existing region file (region/ or entities/) for in-place chunk rewriting.
	 *
	 * <p>The file is opened read-write and its chunks are edited in place, so this must be pointed at
	 * a copy, never at a template that has to stay untouched.
	 */
	RegionAccess openRegion(Path regionFile) throws IOException;

	/** Reads a gzip-compressed standalone NBT file, such as level.dat. */
	ReadWriteNBT readNbtFile(Path file) throws IOException;

	/** Writes a gzip-compressed standalone NBT file, such as level.dat. */
	void writeNbtFile(Path file, ReadWriteNBT nbt) throws IOException;
}
