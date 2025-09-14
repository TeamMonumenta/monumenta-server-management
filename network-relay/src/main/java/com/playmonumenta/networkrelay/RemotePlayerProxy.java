package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.util.MMLog;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class RemotePlayerProxy extends RemotePlayerAbstraction {
	protected static final String SERVER_TYPE = "proxy";

	/**
	 * The shard the proxy wishes the player to be on
	 * This will be blank if the target shard could not be determined
	 */
	protected final String mTargetShard;

	protected RemotePlayerProxy(String serverId, UUID uuid, String name, boolean isOnline, @Nullable Boolean isHidden, String targetShard) {
		super(serverId, uuid, name, isOnline, isHidden);
		mTargetShard = targetShard;

		MMLog.fine(() -> "Created RemotePlayerProxy for " + mName + " from " + mServerId + ": " + (mIsOnline ? "online" : "offline"));
	}

	protected RemotePlayerProxy(JsonObject remoteData) {
		super(remoteData);
		mTargetShard = remoteData.get("targetShard").getAsString();

		MMLog.fine(() -> "Received RemotePlayerProxy for " + mName + " from " + mServerId + ": " + (mIsOnline ? "online" : "offline"));
	}

	@Override
	public RemotePlayerAbstraction asOffline() {
		RemotePlayerAbstraction offlineCopy = new RemotePlayerProxy(
			mServerId,
			mUuid,
			mName,
			false,
			mIsHidden,
			mTargetShard
		);
		offlineCopy.mPluginData.putAll(mPluginData);
		return offlineCopy;
	}

	@Override
	public JsonObject toJson() {
		JsonObject playerData = super.toJson();
		playerData.addProperty("targetShard", mTargetShard);
		return playerData;
	}

	@Override
	public String getServerType() {
		return SERVER_TYPE;
	}

	public String targetShard() {
		return mTargetShard;
	}

	@Override
	public boolean isSimilar(@Nullable RemotePlayerAbstraction other) {
		if (!super.isSimilar(other)) {
			return false;
		}
		if (other instanceof RemotePlayerProxy otherB) {
			return this.mTargetShard.equals(otherB.mTargetShard);
		}
		return true;
	}
}
