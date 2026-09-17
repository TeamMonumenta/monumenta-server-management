package com.playmonumenta.common.event;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/** Called when a time warp is applied. Used to notify SQ and main plugin that the time needs to be rechecked. */
public class RefreshTimeEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	public RefreshTimeEvent() {

	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
