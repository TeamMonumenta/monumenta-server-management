package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.util.MMLog;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/*
 * Server types other than Minecraft and Minecraft Proxies must provide an overridden version of this class
 * if they wish to broadcast player info. At a minimum, getServerType() must be overridden.
 */
public class RemotePlayerGeneric extends RemotePlayerAbstraction {
	protected final String mServerType;

	protected RemotePlayerGeneric(String serverType, String serverId, UUID uuid, String name, boolean isOnline, @Nullable Boolean isHidden) {
		super(serverId, uuid, name, isOnline, isHidden);
		mServerType = serverType;

		MMLog.fine(() -> "Created RemotePlayerGeneric (" + mServerType + ") for " + mName + " from " + mServerId + ": " + (mIsOnline ? "online" : "offline"));
	}

	protected RemotePlayerGeneric(JsonObject remoteData) {
		super(remoteData);
		mServerType = remoteData.get("serverType").getAsString();

		MMLog.fine(() -> "Received RemotePlayerGeneric (" + mServerType + ") for " + mName + " from " + mServerId + ": " + (mIsOnline ? "online" : "offline"));
	}

	@Override
	public RemotePlayerAbstraction asOffline() {
		RemotePlayerGeneric offlineCopy = new RemotePlayerGeneric(
			mServerType,
			mServerId,
			mUuid,
			mName,
			false,
			mIsHidden
		);
		offlineCopy.mPluginData.putAll(mPluginData);
		return offlineCopy;
	}

	@Override
	public String getServerType() {
		return mServerType;
	}
}
