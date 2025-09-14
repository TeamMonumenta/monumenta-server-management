package com.playmonumenta.networkrelay;

public class RemotePlayerLoadedEventGeneric {

	public final RemotePlayerAbstraction mRemotePlayer;
	public final String mServerId;

	public RemotePlayerLoadedEventGeneric(RemotePlayerAbstraction remotePlayer) {
		mRemotePlayer = remotePlayer;
		mServerId = remotePlayer.mServerId;
	}
}
