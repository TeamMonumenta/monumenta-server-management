package com.playmonumenta.networkrelay;

public class RemotePlayerUpdatedEventGeneric {

	public final RemotePlayerAbstraction mRemotePlayer;
	public final String mServerId;

	public RemotePlayerUpdatedEventGeneric(RemotePlayerAbstraction remotePlayer) {
		mRemotePlayer = remotePlayer;
		mServerId = remotePlayer.mServerId;
	}
}
