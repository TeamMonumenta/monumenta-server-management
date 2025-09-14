package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;

public class GatherHeartbeatDataEventGeneric {
	private final JsonObject mPluginData = new JsonObject();

	/**
	 * Sets the plugin data that should be retrievable for this shard
	 *
	 * @param pluginIdentifier  A unique string key identifying this plugin data
	 * @param pluginData        The data to save.
	 */
	public void setPluginData(String pluginIdentifier, JsonObject pluginData) {
		mPluginData.add(pluginIdentifier, pluginData);
	}

	/**
	 * Gets the plugin data that has been set by other plugins
	 */
	public JsonObject getPluginData() {
		return mPluginData;
	}
}
