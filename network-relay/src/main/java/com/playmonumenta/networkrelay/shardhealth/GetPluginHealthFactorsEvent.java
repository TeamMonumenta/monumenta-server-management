package com.playmonumenta.networkrelay.shardhealth;

import com.google.gson.JsonObject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Gathers the factors for a shard's total health from current or average plugin data.
 * May be called sync (using current/average data) or async (using average data).
 * Should only use data already stored in the event's provided plugin data, not additional data.
 */
public class GetPluginHealthFactorsEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final String mPluginIdentifier;
	private final JsonObject mPluginData;
	private double mPluginHealth = 1.0;

	public GetPluginHealthFactorsEvent(String pluginIdentifier, JsonObject pluginData) {
		mPluginIdentifier = pluginIdentifier;
		mPluginData = pluginData;
	}

	/**
	 * Returns the identifier for the plugin that provided this health information
	 * @return The identifier for the plugin that provided this health information
	 */
	public String pluginIdentifier() {
		return mPluginIdentifier;
	}

	/**
	 * Returns the stored plugin data, whether it be current (sync) or average (sync or async).
	 * @return Plugin data for a given pluginIdentifier
	 */
	public JsonObject pluginData() {
		return mPluginData;
	}

	/**
	 * Includes a given health factor in this shard's latest total health sample.
	 * May be called multiple times to include multiple factors of a shard's health.
	 * <p>
	 * The method of combining factors may change in future versions, but will always
	 * use the same range to indicate a health or unhealthy shard.
	 * <p>
	 * Ignoring this event method allows you to track aspects of a shard's
	 * health over time without using it to sort players into different shards.
	 *
	 * @param healthFactor A single health factor, where 0.0 is considered dead/impossibly unhealthy,
	 *                        and 1.0 is considered impossibly perfect health. Must not exceed that range.
	 */
	public void includePluginHealthFactor(double healthFactor) {
		mPluginHealth *= Double.max(0.0, Double.min(1.0, healthFactor));
	}

	/**
	 * Returns this plugin identifier's health factor
	 * @return The health factor for the current pluginIdentifier
	 */
	public double getPluginHealthFactor() {
		return mPluginHealth;
	}


	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
