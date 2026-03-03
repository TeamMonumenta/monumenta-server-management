package com.playmonumenta.redissync.example;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.playmonumenta.redissync.RedisAPI;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.proxy.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class ExampleVelocityListener {
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
	private final MonumentaRedisSyncExampleVelocity mPlugin;

	public ExampleVelocityListener(final MonumentaRedisSyncExampleVelocity plugin) {
		mPlugin = plugin;
	}

	private static String redisKey(UUID uuid) {
		return "proxy:plugin-data:" + uuid + ":" + IDENTIFIER;
	}

	/*
	 * When player connects, load their data from Redis and store it locally in a map.
	 *
	 * Unlike the Paper side, Velocity reads from Redis directly rather than through
	 * a cached copy. Use a short timeout to avoid blocking the event thread too long.
	 */
	@Subscribe
	public void postLoginEvent(PostLoginEvent event) {
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();

		try {
			String raw = RedisAPI.getInstance().async().get(redisKey(uuid)).get(5, TimeUnit.SECONDS);
			if (raw == null) {
				mPlugin.mLogger.info("No data for player {}", player.getUsername());
			} else {
				JsonObject obj = JsonParser.parseString(raw).getAsJsonObject();
				mAllPlayerData.put(uuid, CustomData.fromJsonObject(obj));
				mPlugin.mLogger.info("Loaded data for player {}", player.getUsername());
			}
		} catch (Exception ex) {
			mPlugin.mLogger.warn("Failed to load data for player {}: {}", player.getUsername(), ex.getMessage());
		}
	}

	/* When player disconnects, save their data back to Redis and remove from local storage */
	@Subscribe
	public void disconnectEvent(DisconnectEvent event) {
		Player player = event.getPlayer();
		UUID uuid = player.getUniqueId();

		CustomData data = mAllPlayerData.remove(uuid);
		if (data != null) {
			RedisAPI.getInstance().async().set(redisKey(uuid), data.toJsonObject().toString());
		}
	}

	/* Get the player's custom data for use by other parts of your plugin */
	public CustomData getCustomData(final Player player) {
		return mAllPlayerData.get(player.getUniqueId());
	}
}
