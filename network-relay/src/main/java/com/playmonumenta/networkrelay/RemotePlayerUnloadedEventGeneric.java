package com.playmonumenta.networkrelay;


public class RemotePlayerUnloadedEventGeneric {
	public final RemotePlayerAbstraction mRemotePlayer;
	public final String mServerId;

	public RemotePlayerUnloadedEventGeneric(RemotePlayerAbstraction remotePlayer) {
		mRemotePlayer = remotePlayer;
		mServerId = remotePlayer.mServerId;
	}
}
