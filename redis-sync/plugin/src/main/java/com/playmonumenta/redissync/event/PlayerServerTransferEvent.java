package com.playmonumenta.redissync.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

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
