package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkrelay.util.MMLog;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public abstract class RemotePlayerAbstraction {
	/**
	 * The ID of the server ID reporting this information for a given server type.
	 * For example "proxy" could report "bungee-13", or "minecraft" could report "valley-2".
	 * This is not used for proxies to report which Minecraft instance is most relevant.
	 */
	protected final String mServerId;
	/** The player's Minecraft UUID */
	protected final UUID mUuid;
	/** The player's name */
	protected final String mName;
	/** Whether the player is online; this is broadcast as offline to remove remote players from local caches */
	protected final boolean mIsOnline;
	/** Whether the player is visible to most players or not. Is null if unset */
	protected final @Nullable Boolean mIsHidden;
	/** Data provided by other plugins */
	protected final Map<String, JsonObject> mPluginData;

	/**
	 * Data from this (local) server
	 *
	 * @param serverId - server id (shard name)
	 * @param uuid - player's uuid
	 * @param name - player's name
	 * @param isOnline - player's online status
	 * @param isHidden - player's visibility status
	 */
	protected RemotePlayerAbstraction(String serverId, UUID uuid, String name, boolean isOnline, @Nullable Boolean isHidden) {
		mServerId = serverId;
		mUuid = uuid;
		mName = name;
		mIsOnline = isOnline;
		mIsHidden = isHidden;
		mPluginData = new HashMap<>();
	}

	/** Data from a remote server of any type (subclasses provide extra information) */
	public RemotePlayerAbstraction(JsonObject remoteData) {
		mServerId = remoteData.get("serverId").getAsString();
		mUuid = UUID.fromString(remoteData.get("playerUuid").getAsString());
		mName = remoteData.get("playerName").getAsString();
		mIsOnline = remoteData.get("isOnline").getAsBoolean();
		JsonPrimitive isHiddenJson = remoteData.getAsJsonPrimitive("isHidden");
		if (isHiddenJson == null || !isHiddenJson.isBoolean()) {
			mIsHidden = null;
		} else {
			mIsHidden = isHiddenJson.getAsBoolean();
		}
		mPluginData = new HashMap<>();
		JsonObject pluginData = remoteData.getAsJsonObject("pluginData");
		for (String key : pluginData.keySet()) {
			mPluginData.put(key, pluginData.getAsJsonObject(key));
		}
	}

	/** Determine the appropriate remote player data type to use */
	public static RemotePlayerAbstraction from(JsonObject remoteData) {
		String serverType = remoteData.get("serverType").getAsString();
		return switch (serverType) {
			case RemotePlayerMinecraft.SERVER_TYPE -> new RemotePlayerMinecraft(remoteData);
			case RemotePlayerProxy.SERVER_TYPE -> new RemotePlayerProxy(remoteData);
			default -> new RemotePlayerGeneric(remoteData);
		};
	}

	public abstract RemotePlayerAbstraction asOffline();

	/** Serializes player data to be broadcast to remote servers */
	public JsonObject toJson() {
		JsonObject playerData = new JsonObject();
		playerData.addProperty("serverType", getServerType());
		playerData.addProperty("serverId", mServerId);
		playerData.addProperty("playerUuid", mUuid.toString());
		playerData.addProperty("playerName", mName);
		if (mIsHidden != null) {
			playerData.addProperty("isHidden", mIsHidden);
		}
		playerData.addProperty("isOnline", mIsOnline);
		playerData.add("pluginData", serializePluginData());
		return playerData;
	}

	/** Get a given plugin's data, if available */
	@Nullable
	public JsonObject getPluginData(String pluginId) {
		return mPluginData.get(pluginId);
	}

	protected JsonObject serializePluginData() {
		JsonObject pluginData = new JsonObject();
		for (Map.Entry<String, JsonObject> entry : mPluginData.entrySet()) {
			pluginData.add(entry.getKey(), entry.getValue());
		}
		return pluginData;
	}

	public abstract String getServerType();

	public String getServerId() {
		return mServerId;
	}

	public UUID getUuid() {
		return mUuid;
	}

	public String getName() {
		return mName;
	}

	public boolean isOnline() {
		return mIsOnline;
	}

	public @Nullable Boolean isHidden() {
		return mIsHidden;
	}

	/** Broadcast this player's data to other servers */
	protected void broadcast() {
		JsonObject playerData = toJson();

		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(RemotePlayerManagerAbstraction.REMOTE_PLAYER_UPDATE_CHANNEL,
				playerData,
				RemotePlayerManagerAbstraction.REMOTE_PLAYER_MESSAGE_TTL);
		} catch (Exception e) {
			MMLog.severe(() -> "Failed to broadcast " + RemotePlayerManagerAbstraction.REMOTE_PLAYER_UPDATE_CHANNEL);
		}
	}

	@Override
	public String toString() {
		return toJson().toString();
	}

	public boolean isSimilar(@Nullable RemotePlayerAbstraction other) {
		if (other == null) {
			return false;
		}
		return this.mName.equals(other.mName) &&
			this.mUuid.equals(other.mUuid) &&
			this.mServerId.equals(other.mServerId) &&
			this.mIsOnline == other.mIsOnline &&
			this.getServerType().equals(other.getServerType()) &&
			Objects.equals(this.mIsHidden, other.mIsHidden) &&
			Objects.equals(this.mPluginData, other.mPluginData);
	}
}
