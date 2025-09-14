package com.playmonumenta.networkrelay.shardhealth;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * This event fires up to once per restart to indicate that a shard is low on memory, and may need to restart.
 * Use it to send logs or alerts, create heap dumps, schedule a restart, etc.
 */
public class LowMemoryEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
