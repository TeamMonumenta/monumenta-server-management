package com.playmonumenta.networkrelay;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class DestOnlineEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final String mDest;

	public DestOnlineEvent(String dest) {
		mDest = dest;
	}

	public String getDest() {
		return mDest;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
