package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.velocitypowered.api.event.annotation.AwaitingEvent;
import java.util.HashMap;
import java.util.Map;

@AwaitingEvent
public class GatherRemotePlayerDataEventVelocity {
	private final Map<String, JsonObject> mPluginData = new HashMap<>();
	/**
	 * Player the plugin data is associated with
	 */
	public final RemotePlayerAbstraction mRemotePlayer;

	/**
	 * GatherPluginDataEventVelocity
	 * @param remotePlayer - remotePlayer
	 */
	public GatherRemotePlayerDataEventVelocity(RemotePlayerAbstraction remotePlayer) {
		mRemotePlayer = remotePlayer;
	}

	/**
	 * Include data in the player's plugin data (that should be retrievable for this shard).
	 *
	 * @param pluginId A unique string key identifying this plugin.
	 * @param key      The key of this piece of data.
	 * @param data     The data to be included.
	 */
	public void addPluginData(String pluginId, String key, JsonObject data) {
		if (mPluginData.containsKey(pluginId)) {
			JsonObject root = mPluginData.get(pluginId);
			root.add(key, data);
			return;
		}
		JsonObject root = new JsonObject();
		root.add(key, data);
		mPluginData.put(pluginId, root);
	}

	/**
	 * Sets the plugin data that should be retrievable for this shard
	 *
	 * @param pluginId A unique string key identifying this plugin.
	 * @param data     The data to save.
	 */
	public void setPluginData(String pluginId, JsonObject data) {
		mPluginData.remove(pluginId);
		mPluginData.put(pluginId, data);
	}

	/**
	 * Gets the already registered plugin data
	 * @return The plugin data associated with this player
	 */
	public Map<String, JsonObject> getPluginData() {
		return mPluginData;
	}
}
