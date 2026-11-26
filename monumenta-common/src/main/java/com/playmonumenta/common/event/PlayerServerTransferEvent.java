package com.playmonumenta.common.event;

import org.bukkit.Warning;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Scheduled for removal: restored only so plugins built against older versions still load.
 * Fired when {@code MonumentaRedisSyncAPI.sendPlayer} is called; cancelling it still cancels the
 * transfer. Nothing is frozen afterwards and the player plays on until they leave, so state set
 * here is unreliable. Use {@code MonumentaRedisSyncAPI.getDeparture} in PlayerQuitEvent, and
 * {@code PlayerSessionEndEvent} for cleanup.
 */
@Deprecated(forRemoval = true)
@Warning(value = true, reason = "Scheduled for removal; transfers no longer freeze the player. Use MonumentaRedisSyncAPI.getDeparture and PlayerSessionEndEvent")
public class PlayerServerTransferEvent extends PlayerEvent implements Cancellable {

	private static final HandlerList handlers = new HandlerList();

	private boolean mIsCancelled;
	private final String mTarget;

	public PlayerServerTransferEvent(Player player, String target) {
		super(player);
		mTarget = target;
		mIsCancelled = false;
	}

	@Override
	public boolean isCancelled() {
		return mIsCancelled;
	}

	@Override
	public void setCancelled(boolean arg0) {
		mIsCancelled = arg0;
	}

	public String getTarget() {
		return mTarget;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
