package com.playmonumenta.worlds.adapters;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.NBTContainer;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFile;
import org.jetbrains.annotations.Nullable;

/*
 * World storage access for 1.20.4, implemented directly on the server's own Anvil classes.
 *
 * RegionFile is the same class the server uses to load and save chunks, so compression, sector
 * allocation and external .mcc handling are byte-for-byte what a running server would produce.
 * NBT-API wraps the native CompoundTag in place rather than copying it, so a chunk is decompressed
 * once on read and compressed once on write with no intermediate re-encoding.
 */
public class WorldStorageAdapter_v1_20_R3 implements WorldStorageAdapter {
	@Override
	public RegionAccess openRegion(Path regionFile) throws IOException {
		return new RegionAccessImpl(regionFile);
	}

	@Override
	public ReadWriteNBT readNbtFile(Path file) throws IOException {
		return NBT.wrapNMSTag(NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()));
	}

	@Override
	public void writeNbtFile(Path file, ReadWriteNBT nbt) throws IOException {
		NbtIo.writeCompressed(unwrap(nbt), file);
	}

	// NBT-API hands out a live view of the native tag; recovering it avoids a serialization round trip.
	private static CompoundTag unwrap(ReadWriteNBT nbt) {
		if (!(nbt instanceof NBTContainer container)) {
			throw new IllegalArgumentException("Expected a root NBT compound, got " + nbt.getClass().getName());
		}
		return (CompoundTag) container.getCompound();
	}

	private static final class RegionAccessImpl implements RegionAccess {
		private final RegionFile mRegionFile;

		private RegionAccessImpl(Path path) throws IOException {
			// The containing directory is where oversized chunks spill to c.<x>.<z>.mcc.
			mRegionFile = new RegionFile(path, path.getParent(), false);
		}

		@Override
		public @Nullable ReadWriteNBT readChunk(int chunkX, int chunkZ) throws IOException {
			try (DataInputStream in = mRegionFile.getChunkDataInputStream(new ChunkPos(chunkX, chunkZ))) {
				return in == null ? null : NBT.wrapNMSTag(NbtIo.read(in));
			}
		}

		@Override
		public void writeChunk(int chunkX, int chunkZ, ReadWriteNBT chunk) throws IOException {
			// Closing the stream is what commits the chunk to the region file.
			try (DataOutputStream out = mRegionFile.getChunkDataOutputStream(new ChunkPos(chunkX, chunkZ))) {
				NbtIo.write(unwrap(chunk), out);
			}
		}

		@Override
		public void removeChunk(int chunkX, int chunkZ) throws IOException {
			mRegionFile.clear(new ChunkPos(chunkX, chunkZ));
		}

		@Override
		public void close() throws IOException {
			mRegionFile.close();
		}
	}
}
