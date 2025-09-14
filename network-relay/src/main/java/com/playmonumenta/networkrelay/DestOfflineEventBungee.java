package com.playmonumenta.networkrelay;

import net.md_5.bungee.api.plugin.Event;

public class DestOfflineEventBungee extends Event {
	private final String mDest;

	public DestOfflineEventBungee(String dest) {
		mDest = dest;
	}

	public String getDest() {
		return mDest;
	}
}
