package com.playmonumenta.worlds.adapters;

import de.tr7zw.changeme.nbtapi.NBTContainer;
import java.io.IOException;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.chunk.ChunkAccess;

public class VersionAdapter_v1_20_R3 implements VersionAdapter {
	/**
	 * Creates the version adapter.
	 *
	 * @param logger The logger to use
	 */
	@SuppressWarnings("unused")
	public VersionAdapter_v1_20_R3(org.slf4j.Logger logger) {
	}

	// NBTContainer deprecation is suppressed because we need the raw minecraft CompoundTag from it
	@SuppressWarnings("deprecation")
	public NBTContainer readCompressed(Path meow) {
		try {
			final var nbt = NbtIo.readCompressed(meow, NbtAccounter.unlimitedHeap());
			return new NBTContainer(nbt);
		} catch (IOException ex) {
			return null;
		}
	}

	@SuppressWarnings("deprecation")
	public boolean writeCompressed(Path meow, NBTContainer nbt) {
		try {
			final var rawNbt = nbt.getCompound();
			if (rawNbt instanceof final CompoundTag tag) {
				NbtIo.writeCompressed(tag, meow);
				return true;
			}
			return false;
		} catch (IOException ex) {
			return false;
		}
	}


}
