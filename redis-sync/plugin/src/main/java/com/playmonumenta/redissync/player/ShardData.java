package com.playmonumenta.redissync.player;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.playmonumenta.redissync.utils.Util;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.jetbrains.annotations.Nullable;

public record ShardData(
	@Nullable UUID worldUuid, @Nullable String worldName,
	ImmutableMap<UUID, WorldData> worldData
) {

	public ShardData {
		Preconditions.checkNotNull(worldData);
	}

	public ShardData withWorld(World world, WorldData newWorldData) {
		return new ShardData(world.getUID(), world.getName(), Util.extend(worldData, world.getUID(), newWorldData));
	}

	public World findWorld() {
		if (worldUuid() != null) {
			final var world = Bukkit.getWorld(worldUuid());
			if (world != null) {
				return world;
			}
		}

		if (worldName() != null) {
			final var world = Bukkit.getWorld(worldName());
			if (world != null) {
				return world;
			}
		}

		return Bukkit.getWorlds().get(0);
	}
}
