package com.playmonumenta.worlds.adapters;

import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import java.io.IOException;
import org.jetbrains.annotations.Nullable;

/*
 * An open Anvil region file, addressed by world-absolute chunk coordinates.
 *
 * Chunks not belonging to this file's 32x32 block are not addressable; passing such coordinates is a
 * programming error. Reading a chunk decompresses it exactly once and writing recompresses it exactly
 * once, so chunks that do not need changing should simply never be written.
 */
public interface RegionAccess extends AutoCloseable {
	/** Reads a chunk's NBT, or returns null if that chunk is not present in this file. */
	@Nullable ReadWriteNBT readChunk(int chunkX, int chunkZ) throws IOException;

	/** Writes a chunk's NBT back, replacing whatever was stored at those coordinates. */
	void writeChunk(int chunkX, int chunkZ, ReadWriteNBT chunk) throws IOException;

	/** Removes a chunk entirely, so the region file no longer reports it as present. */
	void removeChunk(int chunkX, int chunkZ) throws IOException;

	@Override
	void close() throws IOException;
}
