package com.playmonumenta.networkrelay;

import net.md_5.bungee.api.plugin.Event;

public class RemotePlayerUpdatedEventBungee extends Event {

	public final RemotePlayerAbstraction mRemotePlayer;
	public final String mServerId;

	public RemotePlayerUpdatedEventBungee(RemotePlayerAbstraction remotePlayer) {
		mRemotePlayer = remotePlayer;
		mServerId = remotePlayer.mServerId;
	}
}
