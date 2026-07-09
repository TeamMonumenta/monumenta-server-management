package com.playmonumenta.common;

import io.papermc.paper.entity.TeleportFlag;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;

public final class TpUtil {
	private static final TeleportFlag[] EMPTY_FLAGS = new TeleportFlag[0];

	private TpUtil() {
	}

	/**
	 * Teleports this entity to the given location.
	 * @param entity Entity being teleported
	 * @param location New location to teleport this entity to
	 * @return <code>true</code> if the teleport was successful
	 * <br>This method adds opinionated defaults such as {@link TeleportFlag.EntityState#RETAIN_PASSENGERS} and {@link TeleportFlag.EntityState#RETAIN_VEHICLE}
	 */
	public static boolean tp(Entity entity, Location location) {
		return tp(entity, location, TeleportCause.PLUGIN);
	}

	/**
	 * Teleports this entity to the given location.
	 * @param entity Entity being teleported
	 * @param location New location to teleport this entity to
	 * @param cause The cause of this teleportation
	 * @return <code>true</code> if the teleport was successful
	 * <br>This method adds opinionated defaults such as {@link TeleportFlag.EntityState#RETAIN_PASSENGERS} and {@link TeleportFlag.EntityState#RETAIN_VEHICLE}
	 */
	public static boolean tp(Entity entity, Location location, TeleportCause cause) {
		return tp(entity, location, cause, EMPTY_FLAGS);
	}

	/**
	 * Teleports this entity to the given location.
	 * @param entity Entity being teleported
	 * @param location New location to teleport this entity to
	 * @param cause The cause of this teleportation
	 * @param flags Flags to be used in this teleportation
	 * @return <code>true</code> if the teleport was successful
	 * <br>This method adds opinionated defaults such as {@link TeleportFlag.EntityState#RETAIN_PASSENGERS} and {@link TeleportFlag.EntityState#RETAIN_VEHICLE}
	 */
	public static boolean tp(Entity entity, Location location, TeleportCause cause, TeleportFlag... flags) {
		final var newFlags = addOpinionatedDefaults(flags);
		return entity.teleport(location, cause, newFlags);
	}

	/**
	 * Loads/Generates(in 1.13+) the Chunk asynchronously, and then teleports the entity when the chunk is ready.
	 * @param entity Entity being teleported
	 * @param location Location to teleport to
	 * @return A future that will be completed with the result of the teleport
	 * <br>This method adds opinionated defaults such as {@link TeleportFlag.EntityState#RETAIN_PASSENGERS} and {@link TeleportFlag.EntityState#RETAIN_VEHICLE}
	 */
	public static CompletableFuture<Boolean> tpAsync(Entity entity, Location location) {
		return tpAsync(entity, location, TeleportCause.PLUGIN);
	}

	/**
	 * Loads/Generates(in 1.13+) the Chunk asynchronously, and then teleports the entity when the chunk is ready.
	 * @param entity Entity being teleported
	 * @param location Location to teleport to
	 * @param cause Reason for teleport
	 * @return A future that will be completed with the result of the teleport
	 * <br>This method adds opinionated defaults such as {@link TeleportFlag.EntityState#RETAIN_PASSENGERS} and {@link TeleportFlag.EntityState#RETAIN_VEHICLE}
	 */
	public static CompletableFuture<Boolean> tpAsync(Entity entity, Location location, TeleportCause cause) {
		return tpAsync(entity, location, cause, EMPTY_FLAGS);
	}

	/**
	 * Loads/Generates(in 1.13+) the Chunk asynchronously, and then teleports the entity when the chunk is ready.
	 * @param entity Entity being teleported
	 * @param location Location to teleport to
	 * @param cause Reason for teleport
	 * @param flags Flags to be used in this teleportation
	 * @return A future that will be completed with the result of the teleport
	 * <br>This method adds opinionated defaults such as {@link TeleportFlag.EntityState#RETAIN_PASSENGERS} and {@link TeleportFlag.EntityState#RETAIN_VEHICLE}
	 */
	public static CompletableFuture<Boolean> tpAsync(Entity entity, Location location, TeleportCause cause, TeleportFlag... flags) {
		final var newFlags = addOpinionatedDefaults(flags);
		return entity.teleportAsync(location, cause, newFlags);
	}

	private static TeleportFlag[] addOpinionatedDefaults(TeleportFlag[] flags) {
		final var newFlags = new TeleportFlag[flags.length + 2];
		int i = 0;
		for(; i < flags.length; ++i) {
  		newFlags[i] = flags[i];
		}
		// opinionated defaults
		newFlags[i] = TeleportFlag.EntityState.RETAIN_PASSENGERS;
		newFlags[i + 1] = TeleportFlag.EntityState.RETAIN_VEHICLE;
		return newFlags;
	}
}
