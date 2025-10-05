package com.playmonumenta.redissync.player;

import com.google.gson.JsonObject;
import org.jetbrains.annotations.Nullable;

/**
 * @param blob
 * @param pos
 */
public record WorldData(@Nullable JsonObject blob, @Nullable PlayerPos pos) {
	public WorldData withPos(PlayerPos pos) {
		return new WorldData(blob, pos);
	}

	@Override
	public @Nullable JsonObject blob() {
		return blob == null ? null : blob.deepCopy();
	}
}
