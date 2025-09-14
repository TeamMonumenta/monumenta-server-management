package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import javax.annotation.Nullable;
import org.jetbrains.annotations.NotNull;

public class RemotePlayerData implements Comparable<RemotePlayerData> {
	public final UUID mUuid;
	public final String mName;
	private final ConcurrentMap<String, RemotePlayerAbstraction> mPlayerData;

	// A cache for the player's info from multiple server types
	public RemotePlayerData(UUID uuid, String name) {
		mUuid = uuid;
		mName = name;
		mPlayerData = new ConcurrentHashMap<>();
	}

	// Register data about the player for a given server type, returning the old data
	public @Nullable RemotePlayerAbstraction register(RemotePlayerAbstraction playerData) {
		String serverType = playerData.getServerType();
		if (playerData.mIsOnline) {
			return mPlayerData.put(serverType, playerData);
		} else {
			return unregister(serverType);
		}
	}

	// Unregister the player's data for a given server type, returning the old data
	public @Nullable RemotePlayerAbstraction unregister(String serverType) {
		return mPlayerData.remove(serverType);
	}

	// Get set of server types the player is currently on
	public Set<String> getServerTypes() {
		return new TreeSet<>(mPlayerData.keySet());
	}

	// Get info about the player from a given server type
	public @Nullable RemotePlayerAbstraction get(String serverType) {
		return mPlayerData.get(serverType);
	}

	// Check if the player is online on any server type
	public boolean isOnline() {
		return !mPlayerData.isEmpty();
	}

	// Check if the player is hidden on any server type
	public boolean isHidden() {
		boolean isNull = true;
		for (RemotePlayerAbstraction playerData : mPlayerData.values()) {
			if (playerData.mIsHidden != null) {
				if (playerData.mIsHidden) {
					return true;
				}
				isNull = false;
			}
		}
		return isNull;
	}

	public JsonObject toJson() {
		JsonObject playerData = new JsonObject();
		playerData.addProperty("uuid", mUuid.toString());
		playerData.addProperty("name", mName);
		for (RemotePlayerAbstraction data : mPlayerData.values()) {
			playerData.add(data.getServerType(), data.toJson());
		}
		return playerData;
	}

	@Override
	public int compareTo(@NotNull RemotePlayerData o) {
		return mUuid.compareTo(o.mUuid);
	}

	@Override
	public String toString() {
		return this.toJson().toString();
	}

	public String friendlyString() {
		StringBuilder builder = new StringBuilder();

		builder.append(mName);
		builder.append(" (");
		builder.append(mUuid);
		builder.append(") is ");
		builder.append(isHidden() ? "hidden" : "not hidden");

		if (mPlayerData.get("proxy") instanceof RemotePlayerProxy remotePlayerProxy) {
			builder.append("\nConnected to proxy ");
			builder.append(remotePlayerProxy.getServerId());
			if (remotePlayerProxy.mTargetShard == null) {
				builder.append(" which cannot identify their shard");
			} else {
				builder.append(" which believes they are on shard ");
				builder.append(remotePlayerProxy.mTargetShard);
			}
		} else {
			builder.append("\nNot connected to a proxy");
		}

		if (mPlayerData.get("minecraft") instanceof RemotePlayerMinecraft remotePlayerMinecraft) {
			builder.append("\nOn shard ");
			builder.append(remotePlayerMinecraft.getServerId());
			if (remotePlayerMinecraft.mWorld == null) {
				builder.append("\nCurrent world cannot be determined");
			} else {
				builder.append("\nCurrent world is ");
				builder.append(remotePlayerMinecraft.mWorld);
			}
		} else {
			builder.append("\nNot connected to a shard/Minecraft instance");
		}

		return builder.toString();
	}
}
