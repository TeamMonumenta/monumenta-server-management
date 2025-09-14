package com.playmonumenta.networkrelay;

import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class RemotePlayerUnloadedEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	public final RemotePlayerAbstraction mRemotePlayer;
	public final String mServerId;

	public RemotePlayerUnloadedEvent(RemotePlayerAbstraction remotePlayer) {
		mRemotePlayer = remotePlayer;
		mServerId = remotePlayer.mServerId;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
