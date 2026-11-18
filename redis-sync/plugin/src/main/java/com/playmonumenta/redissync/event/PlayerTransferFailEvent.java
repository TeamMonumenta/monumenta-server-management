package com.playmonumenta.redissync.event;

import org.bukkit.Warning;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Scheduled for removal: restored only so plugins built against older versions still load.
 * Fired when a transfer request is dropped because the proxy never acted on it. A transfer the
 * proxy accepts but cannot complete returns the player with a fresh login instead, without this
 * event. Use {@code PlayerSessionEndEvent} for cleanup.
 */
@Deprecated(forRemoval = true)
@Warning(value = true, reason = "Scheduled for removal; not fired for every failed transfer. Use MonumentaRedisSyncAPI.getDeparture and PlayerSessionEndEvent")
public class PlayerTransferFailEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Player mPlayer;

	public PlayerTransferFailEvent(Player player) {
		mPlayer = player;
	}

	public Player getPlayer() {
		return mPlayer;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
