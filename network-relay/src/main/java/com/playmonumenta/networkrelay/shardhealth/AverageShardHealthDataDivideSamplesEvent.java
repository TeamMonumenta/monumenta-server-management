package com.playmonumenta.networkrelay.shardhealth;

import com.google.gson.JsonObject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Allows plugins to include their data in average health data.
 * Only data from the provided samples may be included, and must support sync and async operation.
 * A modifiable JsonObject for the number of samples for your plugin's various types of data is provided
 */
public class AverageShardHealthDataDivideSamplesEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final String mPluginIdentifier;
	private final JsonObject mPluginDataTotal;
	private final JsonObject mPluginDataSampleCounts;
	private JsonObject mAveragePluginData;

	public AverageShardHealthDataDivideSamplesEvent(
		String pluginIdentifier,
		JsonObject pluginDataTotal,
		JsonObject pluginDataSampleCounts
	) {
		mPluginIdentifier = pluginIdentifier;
		mPluginDataTotal = pluginDataTotal;
		mPluginDataSampleCounts = pluginDataSampleCounts;
		mAveragePluginData = pluginDataTotal.deepCopy();
	}

	/**
	 * Returns the identifier for the plugin that provided this health information
	 *
	 * @return The identifier for the plugin that provided this health information
	 */
	public String pluginIdentifier() {
		return mPluginIdentifier;
	}

	/**
	 * This provides the final total for your plugin's data.
	 *
	 * @return the final total of plugin data for a given pluginIdentifier
	 */
	public JsonObject pluginDataTotal() {
		return mPluginDataTotal;
	}

	/**
	 * This provides the sample counts for your pluginIdentifier in the format you specified.
	 *
	 * @return the final sample counts for your pluginIdentifier
	 */
	public JsonObject pluginDataSampleCounts() {
		return mPluginDataSampleCounts;
	}

	/**
	 * Provides the calculated averagePluginData for this pluginIdentifier, or the total if the average was not calculated

	 * @return the average plugin data to be used when awaiting shard health, and/or sharing with other shards
	 */
	public JsonObject averagePluginData() {
		return mAveragePluginData;
	}

	/**
	 * Once you have divided all of your plugin data totals by your sample counts, use this method to store the final averages.
	 * This is what will be used when waiting for shard health to improve, and to share with other shards.
	 *
	 * @param averagePluginData the average of your plugin's health data
	 */
	public void averagePluginData(JsonObject averagePluginData) {
		mAveragePluginData = averagePluginData;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
