package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class GatherRemotePlayerDataEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Map<String, JsonObject> mPluginData = new HashMap<>();

	public final RemotePlayerAbstraction mRemotePlayer;

	public GatherRemotePlayerDataEvent(RemotePlayerAbstraction remotePlayer) {
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
	 */
	public Map<String, JsonObject> getPluginData() {
		return mPluginData;
	}

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
