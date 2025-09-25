package com.playmonumenta.redissync.playerdata;

import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public record ShardData(@Nullable UUID worldUuid, @Nullable String worldName, Map<String, WorldData> worldData) {
	public record WorldData() {
	}
}
