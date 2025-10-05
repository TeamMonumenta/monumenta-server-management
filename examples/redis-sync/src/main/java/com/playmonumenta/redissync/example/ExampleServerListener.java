package com.playmonumenta.redissync.example;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

public class ExampleServerListener implements Listener {
	/*################################################################################
	 * Edit at least this section!
	 * You can change the rest too, but you only need to adapt this section to make something usable
	 */

	/* Change this to something that uniquely identifies the data you want to save for this plugin */
	private static final String IDENTIFIER = "ExampleRedisDataPlugin";

	/* You probably want to change the name of this data class, or make your own */
	public static class CustomData {
		private final Map<String, Integer> mData = new HashMap<>();

		/* Some example functions to work with.
		 * You should replace these with something you actually want to store/manipulate
		 */
		public void setPoints(final String key, final int value) {
			mData.put(key, value);
		}

		public Integer getPoints(final String key) {
			return mData.get(key);
		}

		/*
		 * In this example, read the database string first to JSON, then unpack the JSON to the data structure
		 * You can store anything in here, as long as you can pack it to a String and unpack it again
		 */
		private static CustomData fromJsonObject(JsonObject obj) {
			CustomData newObject = new CustomData();

			for (Map.Entry<String, JsonElement> entry : obj.entrySet()) {
				newObject.mData.put(entry.getKey(), entry.getValue().getAsInt());
			}

			return newObject;
		}

		/*
		 * Store this data structure to a string suitable for storing in the database.
		 * Unicode characters or even arbitrary bytes can be stored in this string
		 */
		private JsonObject toJsonObject() {
			final JsonObject obj = new JsonObject();
			for (Map.Entry<String, Integer> entry : mData.entrySet()) {
				obj.addProperty(entry.getKey(), entry.getValue());
			}
			return obj;
		}
	}

	/*
	 * Edit at least this section!
	 *################################################################################*/

	private final Map<UUID, CustomData> mAllPlayerData = new HashMap<>();
	private final Plugin mPlugin;

	public ExampleServerListener(final Plugin plugin) {
		mPlugin = plugin;
	}

	/*
	 * When player joins, load their data and store it locally in a map
	 *
	 * This data is retrieved from a cached copy in the redis plugin, it does not
	 * result in a slow database lookup
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();

		JsonObject data = MonumentaRedisSyncAPI.getPlayerPluginData(player.getUniqueId(), IDENTIFIER);
		if (data == null) {
			mPlugin.getLogger().info("No data for for player " + player.getName());
		} else {
			mAllPlayerData.put(player.getUniqueId(), CustomData.fromJsonObject(data));
			mPlugin.getLogger().info("Loaded data for player " + player.getName());
		}
	}

	/* Whenever player data is saved, also save the local data */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void playerSaveEvent(PlayerSaveEvent event) {
		Player player = event.getPlayer();

		final CustomData playerData = mAllPlayerData.get(player.getUniqueId());
		if (playerData != null) {
			event.setPluginData(IDENTIFIER, playerData.toJsonObject());
		}
	}

	/* When player leaves, remove it from the local storage a short bit later */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void playerQuitEvent(PlayerQuitEvent event) {
		Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
			if (!event.getPlayer().isOnline()) {
				mAllPlayerData.remove(event.getPlayer().getUniqueId());
			}
		}, 100);
	}

	/* Get the player's custom data for use by other parts of your plugin */
	public CustomData getCustomData(final Player player) {
		return mAllPlayerData.get(player.getUniqueId());
	}
}
