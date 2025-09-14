package com.playmonumenta.networkrelay;

import net.md_5.bungee.api.plugin.Event;

public class DestOnlineEventBungee extends Event {
	private final String mDest;

	public DestOnlineEventBungee(String dest) {
		mDest = dest;
	}

	public String getDest() {
		return mDest;
	}
}
