package com.playmonumenta.networkrelay.shardhealth;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Gathers current shard health data once per tick on the main thread,
 * which is later averaged over time and broadcast to other shards,
 * and to slow down long-running tasks to avoid overwhelming a shard.
 * <p>
 * Note that in order to be broadcast to other shards, the data
 * collected here must be averaged together. Also note that this data
 * is not included in the shard's total health without handling the
 * GetPluginHealthFactorsEvent.
 */
public class GatherShardHealthDataEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final Map<String, JsonObject> mPluginData = new HashMap<>();

	/**
	 * Sets the plugin data that should be retrievable for this shard
	 *
	 * @param pluginIdentifier  A unique string key identifying this plugin data
	 * @param pluginData        The data to save.
	 */
	public void setPluginData(String pluginIdentifier, JsonObject pluginData) {
		mPluginData.put(pluginIdentifier, pluginData);
	}

	/**
	 * Gets the plugin data that has been set by other plugins
	 */
	public Map<String, JsonObject> getPluginData() {
		return mPluginData;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
