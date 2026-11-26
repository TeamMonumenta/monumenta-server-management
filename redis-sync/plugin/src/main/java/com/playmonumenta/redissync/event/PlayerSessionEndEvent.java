package com.playmonumenta.redissync.event;

import com.playmonumenta.redissync.Departure;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * A player's connection to this shard is over: their PlayerQuitEvent and final save are done.
 * This is the place to drop anything kept for them. Fired on the main thread, once per
 * connection that got past login, before this player can join any shard again (including this
 * one). The player is no longer in the game. Not fired at shutdown, when plugins are disabled
 * before players are removed.
 */
public class PlayerSessionEndEvent extends PlayerEvent {
	private static final HandlerList handlers = new HandlerList();

	private final @Nullable Departure mDeparture;

	public PlayerSessionEndEvent(Player player, @Nullable Departure departure) {
		super(player);
		mDeparture = departure;
	}

	/** Why they left, as {@code MonumentaRedisSyncAPI.getDeparture} answered during their quit */
	public @Nullable Departure getDeparture() {
		return mDeparture;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
