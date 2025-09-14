package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.util.MMLog;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class RemotePlayerMinecraft extends RemotePlayerAbstraction {
	protected static final String SERVER_TYPE = "minecraft";

	// The world the player is on for this Minecraft server
	protected final String mWorld;

	protected RemotePlayerMinecraft(String serverId, UUID uuid, String name, boolean isOnline, @Nullable Boolean isHidden, String world) {
		super(serverId, uuid, name, isOnline, isHidden);
		mWorld = world;

		MMLog.fine(() -> "Created RemotePlayerMinecraft for " + mName + " from " + mServerId + ": " + (mIsOnline ? "online" : "offline"));
	}

	protected RemotePlayerMinecraft(JsonObject remoteData) {
		super(remoteData);
		mWorld = remoteData.get("world").getAsString();

		MMLog.fine(() -> "Received RemotePlayerMinecraft for " + mName + " from " + mServerId + ": " + (mIsOnline ? "online" : "offline"));
	}

	@Override
	public RemotePlayerAbstraction asOffline() {
		RemotePlayerMinecraft offlineCopy = new RemotePlayerMinecraft(
			mServerId,
			mUuid,
			mName,
			false,
			mIsHidden,
			mWorld
		);
		offlineCopy.mPluginData.putAll(mPluginData);
		return offlineCopy;
	}

	@Override
	public JsonObject toJson() {
		JsonObject playerData = super.toJson();
		playerData.addProperty("world", mWorld);
		return playerData;
	}

	@Override
	public String getServerType() {
		return SERVER_TYPE;
	}

	public String world() {
		return mWorld;
	}

	@Override
	public boolean isSimilar(@Nullable RemotePlayerAbstraction other) {
		if (!super.isSimilar(other)) {
			return false;
		}
		if (other instanceof RemotePlayerMinecraft otherP) {
			return this.mWorld.equals(otherP.mWorld);
		}
		return true;
	}
}
