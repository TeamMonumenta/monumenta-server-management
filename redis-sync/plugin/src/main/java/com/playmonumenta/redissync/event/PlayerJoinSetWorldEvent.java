package com.playmonumenta.redissync.event;

import java.util.UUID;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * This event fires when a player joins and their data is loaded, but before they are loaded into a world.
 * <p>
 * This is useful to change the player's world seamlessly without them ever seeing a flash of a different world on join.
 * <p>
 * Note that at the time this event is called, the player's scores are available but all player data is blank, including inventory and tags.
 * The API MonumentaRedisSyncAPI.getPlayerWorldData() is also available at this point
 */
public class PlayerJoinSetWorldEvent extends PlayerEvent {

	private static final HandlerList handlers = new HandlerList();

	private @Nonnull World mWorld;
	private final @Nullable UUID mLastSavedWorldUUID;
	private final @Nullable String mLastSavedWorldName;

	public PlayerJoinSetWorldEvent(Player player, @Nonnull World world, @Nullable UUID lastSavedWorldUUID, @Nullable String lastSavedWorldName) {
		super(player);
		mWorld = world;
		mLastSavedWorldUUID = lastSavedWorldUUID;
		mLastSavedWorldName = lastSavedWorldName;
	}

	/*
	 * Get the last saved world UUID attached to the player. This is the world they were on most recently when data saving was triggered.
	 * This might be different than getWorld() either if the last saved world was unloaded when they logged in or if the world has been changed by another plugin.
	 * May be useful for another plugin to dynamically load this world or handle some kind of world removal cleanup logic
	 */
	public @Nullable UUID getLastSavedWorldUUID() {
		return mLastSavedWorldUUID;
	}

	/*
	 * Get the last saved world name attached to the player. This is the world they were on most recently when data saving was triggered.
	 * This might be different than getWorld() either if the last saved world was unloaded when they logged in or if the world has been changed by another plugin.
	 * May be useful for another plugin to dynamically load this world or handle some kind of world removal cleanup logic
	 */
	public @Nullable String getLastSavedWorldName() {
		return mLastSavedWorldName;
	}

	/*
	 * Get the world the player is currently set to join on login
	 */
	public @Nonnull World getWorld() {
		return mWorld;
	}

	/* Causes a player to be logged into this world instead
	 * This takes a world object that should be currently loaded
	 */
	public void setWorld(@Nonnull World world) {
		mWorld = world;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
