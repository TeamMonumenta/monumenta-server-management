package com.playmonumenta.redissync.event;

import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

public class PlayerSaveEvent extends PlayerEvent {

	private static final HandlerList handlers = new HandlerList();

	private final Map<String, JsonObject> mPluginData = new LinkedHashMap<>();

	public PlayerSaveEvent(Player player) {
		super(player);
	}

	/**
	 * Sets the plugin data that should be saved for this player
	 *
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
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
