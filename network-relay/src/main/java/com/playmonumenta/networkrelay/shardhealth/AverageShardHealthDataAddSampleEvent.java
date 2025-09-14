package com.playmonumenta.networkrelay.shardhealth;

import com.google.gson.JsonObject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Allows plugins to include their data in average health data.
 * Only data from the provided samples may be included, and must support sync and async operation.
 * A modifiable JsonObject for the number of samples for your plugin's various types of data is provided
 */
public class AverageShardHealthDataAddSampleEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final String mPluginIdentifier;
	private @Nullable JsonObject mRunningPluginDataTotal;
	private final JsonObject mPluginDataSample;
	private final JsonObject mPluginDataSampleCounts;

	public AverageShardHealthDataAddSampleEvent(
		String pluginIdentifier,
		@Nullable JsonObject runningPluginDataTotal,
		JsonObject pluginDataSample,
		JsonObject pluginDataSampleCounts
	) {
		mPluginIdentifier = pluginIdentifier;
		mRunningPluginDataTotal = runningPluginDataTotal;
		mPluginDataSample = pluginDataSample;
		mPluginDataSampleCounts = pluginDataSampleCounts;
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
	 * This provides the current running total for your plugin's data. It will initially be null,
	 * and must be set to the new total with runningPluginDataTotal(@Nullable JsonObject) after
	 * each occurrence of this event, as well as updating pluginDataSampleCounts() as needed.
	 *
	 * @return a nullable running total of plugin data for a given pluginIdentifier
	 */
	public @Nullable JsonObject runningPluginDataTotal() {
		return mRunningPluginDataTotal;
	}

	/**
	 * Updates the running total for your plugin's data so that
	 * the new total may be used for the next sample in the average.
	 *
	 * @param runningPluginDataTotal Your plugin's updated running total
	 */
	public void runningPluginDataTotal(@Nullable JsonObject runningPluginDataTotal) {
		mRunningPluginDataTotal = runningPluginDataTotal;
	}

	/**
	 * Returns the sample of your plugin's data to be included in the running total for this event.
	 * If included, remember to update pluginDataSampleCounts() with the number of samples included for each data type.
	 *
	 * @return The next sample to be included in the current average's running total
	 */
	public JsonObject pluginDataSample() {
		return mPluginDataSample;
	}

	/**
	 * The running sample count for your plugin. Update this in place if new samples are added.
	 * You are free to format this in whatever way you like, so long as you track the number of
	 * samples in your runningPluginDataTotal for later use in the AverageShardHealthDataDivideSamplesEvent.
	 *
	 * @return the running sample count for this average
	 */
	public JsonObject pluginDataSampleCounts() {
		return mPluginDataSampleCounts;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
