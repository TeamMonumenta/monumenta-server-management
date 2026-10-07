package com.playmonumenta.redissync;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerAdvancementDataSaveEvent;
import com.destroystokyo.paper.event.player.PlayerDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerDataSaveEvent;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.adapters.VersionAdapter.SaveData;
import com.playmonumenta.redissync.data.ContentData;
import com.playmonumenta.redissync.event.PlayerJoinSetWorldEvent;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import com.playmonumenta.redissync.event.UpdateAvailableContentIdsEvent;
import com.playmonumenta.redissync.utils.MMLog;
import com.playmonumenta.redissync.utils.ScoreboardUtils;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.output.KeyValueStreamingChannel;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;

public class DataEventListener implements Listener {
	private static class PlayerUuidToNameStreamingChannel implements KeyValueStreamingChannel<String, String> {
		@Override
		public void onKeyValue(String key /*UUID*/, String value /*name*/) {
			UUID uuid;
			try {
				uuid = UUID.fromString(key);
			} catch (Exception e) {
				return;
			}
			MonumentaRedisSyncAPI.updateUuidToName(uuid, value);
		}
	}

	private static class PlayerNameToUuidStreamingChannel implements KeyValueStreamingChannel<String, String> {
		@Override
		public void onKeyValue(String key /*name*/, String value /*UUID*/) {
			UUID uuid;
			try {
				uuid = UUID.fromString(value);
			} catch (Exception e) {
				return;
			}
			MonumentaRedisSyncAPI.updateNameToUuid(key, uuid);
		}
	}

	@SuppressWarnings("NullAway") // Required to avoid many null checks, this class will always be instantiated if this plugin is loaded
	private static DataEventListener INSTANCE = null;

	private final Gson mGson = new Gson();
	private final Plugin mPlugin;
	private final VersionAdapter mAdapter;
	private final PlayerSessions mSessions;
	private Set<String> mAvailableContentIds = Set.of();

	protected DataEventListener(Plugin plugin, VersionAdapter adapter, PlayerSessions sessions) {
		mPlugin = plugin;
		mAdapter = adapter;
		mSessions = sessions;
		INSTANCE = this;

		KeyValueStreamingChannel<String, String> uuidToNameChannel = new PlayerUuidToNameStreamingChannel();
		KeyValueStreamingChannel<String, String> nameToUuidChannel = new PlayerNameToUuidStreamingChannel();
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.hgetall(uuidToNameChannel, "uuid2name");
			conn.hgetall(nameToUuidChannel, "name2uuid");
		}
	}

	protected static Plugin getPlugin() {
		return INSTANCE.mPlugin;
	}

	protected static VersionAdapter getAdapter() {
		return INSTANCE.mAdapter;
	}

	protected static Set<String> getAvailableContentIds() {
		return INSTANCE.mAvailableContentIds;
	}

	protected static void updateAvailableContentEvent(UpdateAvailableContentIdsEvent event) {
		INSTANCE.mAvailableContentIds = event.getContentIds();
	}

	/* ******************* Data Save/Load Event Handlers ******************* */

	/*
	 * Fires at the end of a datapack reload. The server has already saved each player's advancements
	 * on their own and reloaded them; this full save gives that advancements entry the rest of the
	 * player's data to go with it.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void serverResourcesReloadedEvent(ServerResourcesReloadedEvent event) {
		MMLog.debug("ServerResourcesReloadedEvent caused by " + event.getCause() + ", triggering save for all players...");
		for (Player player : Bukkit.getOnlinePlayers()) {
			MMLog.trace("Saving player " + player.getName() + " due to datapack reload");
			try {
				MonumentaRedisSyncAPI.savePlayer(player);
			} catch (Exception ex) {
				MMLog.severe("Failed to save player '" + player.getName() + "'", ex);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataLoadEvent(PlayerAdvancementDataLoadEvent event) {
		Player player = event.getPlayer();
		String playerName = player.getName();
		/* Even with saving disabled, the session is what caches content and plugin data while online */
		PlayerSession session = mSessions.sessionForLoad(player);

		if (BukkitConfigAPI.getSavingDisabled()) {
			return;
		}

		if (!session.isLive()) {
			MMLog.warning("Skipping advancements load for player=" + playerName + " because their session is " + session.getState());
			return;
		}

		long startTime = System.currentTimeMillis();
		MMLog.debug("Started loading advancements data for player=" + playerName);

		RedisFuture<String> advanceFuture;
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			advanceFuture = conn.lindex(MonumentaRedisSyncAPI.getRedisAdvancementsPath(player), 0);
		}

		try {
			/* Advancements */
			final String advanceData = advanceFuture.get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
			MMLog.trace(() -> "Advancements data loaded for player=" + playerName);
			MMLog.trace(() -> "Advancements data:" + advanceData);
			if (advanceData != null) {
				event.setJsonData(advanceData);
			} else {
				MMLog.warning("No advancements data for player '" + playerName + "' - if they are not new, this is a serious error!");
			}

			MMLog.debug(() -> "Processing PlayerAdvancementDataLoadEvent took " + (System.currentTimeMillis() - startTime) + " milliseconds on main thread");
		} catch (CancellationException | InterruptedException | ExecutionException | TimeoutException ex) {
			MMLog.severe("!!! Failed to load player advancements data !!!", ex);
			mSessions.abortLoad(session, "advancements failed to load");
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataSaveEvent(PlayerAdvancementDataSaveEvent event) {
		/* Always cancel saving the player file to disk with this plugin present */
		event.setCancelled(true);

		if (BukkitConfigAPI.getSavingDisabled()) {
			return;
		}

		Player player = event.getPlayer();
		String playerName = player.getName();
		PlayerSession session = mSessions.sessionForSave(player, "advancements");
		if (session == null) {
			return;
		}

		MMLog.debug("Saving advancements data for player=" + playerName);
		MMLog.trace(() -> "Data:" + event.getJsonData());
		String advPath = MonumentaRedisSyncAPI.getRedisAdvancementsPath(player);
		String advJsonData = event.getJsonData();
		/*
		 * An advancements save outside a full save would push an entry with no matching playerdata,
		 * scores, etc, and every index into the history after it would pair the wrong entries. So it
		 * pushes once, and replaces that entry until a full save's advancements take its place.
		 */
		boolean replaceHead = session.beginAdvancementsSave();
		session.trackSave(RedisAPI.multi(commands -> {
			if (replaceHead) {
				commands.lset(advPath, 0, advJsonData);
			} else {
				commands.lpush(advPath, advJsonData);
				commands.ltrim(advPath, 0, BukkitConfigAPI.getHistoryAmount());
			}
		}), () -> "Advancements saving for player=" + playerName + " failed");
	}

	private interface Callable {
		void run(Path dest) throws Exception;
	}

	private static String exceptionToString(Throwable ex) {
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		ex.printStackTrace(pw);
		return sw.toString();
	}

	private void trySave(Path path, String name, Callable callable) {
		try {
			callable.run(path.resolve(name));
		} catch (Throwable e) {
			MMLog.severe("failed to save data to file", e);
			try {
				Files.writeString(path.resolve(name + ".save_error.txt"), exceptionToString(e));
			} catch (Throwable e2) {
				MMLog.severe("I give up!", e2);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerDataLoadEvent(PlayerDataLoadEvent event) {
		Player player = event.getPlayer();
		UUID playerId = player.getUniqueId();
		String playerName = player.getName();
		/* Even with saving disabled, the session is what caches content and plugin data while online */
		PlayerSession session = mSessions.sessionForLoad(player);

		if (BukkitConfigAPI.getSavingDisabled()) {
			return;
		}

		if (!session.isLive()) {
			MMLog.warning("Skipping playerdata load for player=" + playerName + " because their session is " + session.getState());
			return;
		}

		long startTime = System.currentTimeMillis();
		MMLog.debug("Started loading data for player=" + playerName);

		//TODO: Rework to using something like MonumentaRedisSyncAPI.transformPlayerData()
		RedisFuture<byte[]> dataFuture;
		try (RedisAPI.BorrowedCommands<String, byte[]> byteConn = RedisAPI.borrowStringBytes()) {
			dataFuture = byteConn.lindex(MonumentaRedisSyncAPI.getRedisDataPath(player), 0);
		}
		RedisFuture<String> contentFuture;
		RedisFuture<String> pluginDataFuture;
		RedisFuture<String> scoreFuture;
		RedisFuture<Map<String, String>> shardDataFuture;
		try (RedisAPI.BorrowedCommands<String, String> commands = RedisAPI.borrow()) {
			contentFuture = commands.lindex(MonumentaRedisSyncAPI.getRedisContentPath(player), 0);
			pluginDataFuture = commands.lindex(MonumentaRedisSyncAPI.getRedisPluginDataPath(player), 0);
			scoreFuture = commands.lindex(MonumentaRedisSyncAPI.getRedisScoresPath(player), 0);
			shardDataFuture = commands.hgetall(MonumentaRedisSyncAPI.getRedisPerShardDataPath(player));
		}

		try {
			/* Load the primary shared NBT data */
			byte[] data = dataFuture.get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (data == null) {
				/* A new player, whose other data is empty too: the session keeps its empty defaults */
				MMLog.warning("No data for player '" + playerName + "' - if they are not new, this is a serious error!");
				return;
			}
			MMLog.trace("Player data loaded for player=" + playerName);
			MMLog.trace(() -> "Player data: " + b64encode(data));

			/* Load content data */
			String contentData = contentFuture.get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (contentData == null) {
				MMLog.debug("Player '" + player.getName() + "' has no content data");
				session.setContentData(new ContentData(""));
			} else {
				JsonObject obj;
				try {
					obj = mGson.fromJson(contentData, JsonObject.class);
				} catch (JsonSyntaxException ignored) {
					obj = null;
				}
				if (obj == null) {
					MMLog.warning("Failed to parse player '" + player.getName() + "' content as JSON. Player may be misplaced.");
					session.setContentData(new ContentData(""));
				} else {
					session.setContentData(new ContentData(obj));
					MMLog.trace(() -> "Content data loaded for player=" + player.getName());
					MMLog.trace(() -> "Content data: " + contentData);
				}
			}

			/* Load plugin data */
			String pluginData = pluginDataFuture.get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
			if (pluginData == null) {
				MMLog.debug("Player '" + playerName + "' has no plugin data");
			} else {
				session.setPluginData(mGson.fromJson(pluginData, JsonObject.class));
				MMLog.trace("Plugin data loaded for player=" + playerName);
				MMLog.trace(() -> "Plugin data: " + pluginData);
			}

			/* Load scoreboards */
			final String scoreData = scoreFuture.get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
			MMLog.debug("Scoreboard data loaded for player=" + playerName);
			mAdapter.resetPlayerScores(playerName, Bukkit.getScoreboardManager().getMainScoreboard());
			MMLog.trace(() -> "Score data:" + scoreData);
			if (scoreData != null) {
				JsonObject obj = mGson.fromJson(scoreData, JsonObject.class);
				if (obj != null) {
					ScoreboardUtils.loadFromJsonObject(player, obj);
				} else {
					MMLog.severe("Failed to parse player '" + playerName + "' scoreboard data as JSON. This results in data loss!");
				}
			} else {
				MMLog.warning("No scoreboard data for player '" + playerName + "' - if they are not new, this is a serious error!");
			}

			/* Get all the shard data for all shards and worlds */
			Map<String, String> shardData = shardDataFuture.get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
			/* Look up in the shard data first the "overall" part - which world this player was on last time they were on this shard */
			World playerWorld = null; // If null at the end of this block, will use default world
			String lastSavedWorldName = null; // The saved world name from shard data. Might be different from the playerWorld if save data indicated one world, but it is not loaded so fell back to the default
			if (shardData == null) {
				session.setShardData(new HashMap<>());

				/* This is not an error - this will happen whenever a player first joins the game */
				MMLog.debug("Player '" + playerName + "' has never been to any shard before");
			} else {
				session.setShardData(shardData);

				MMLog.trace("Shard data loaded for player=" + playerName);
				MMLog.trace(() -> "Shard data: " + mGson.toJson(shardData));

				/* Figure out what world the player's sharddata indicates they should join
				 * If shard data does not contain this shard name, no info on what world to use - use the default one
				 * If shard data contains this shard name, fetch world parameters from it, preferring UUID, then name. Loaded worlds only, this plugin does not load worlds automatically.
				 */
				String overallShardData = shardData.get(BukkitConfigAPI.getShardName());
				if (overallShardData == null) {
					/* This is not an error - this will happen whenever a player first visits a new shard */
					MMLog.debug("Player '" + playerName + "' has never been to this shard before");
				} else {
					JsonObject shardDataJson = mGson.fromJson(overallShardData, JsonObject.class);

					if (shardDataJson.has("World")) {
						lastSavedWorldName = shardDataJson.get("World").getAsString();

						if (lastSavedWorldName != null) {
							World world = Bukkit.getWorld(lastSavedWorldName);
							if (world != null) {
								playerWorld = world;
							}
						}
					}
				}
			}

			if (playerWorld == null) {
				playerWorld = Bukkit.getWorlds().getFirst();
			}

			/* After this point playerWorld is always non-null and a valid loaded world */

			// Throw an event that lets other plugins modify the join world.
			MMLog.trace("Calling PlayerJoinSetWorldEvent for player '" + playerName + "' with world={" + playerWorld.getUID() + ": " + playerWorld.getName() + "}, lastSavedWorld={" + lastSavedWorldName + "}");
			PlayerJoinSetWorldEvent worldEvent = new PlayerJoinSetWorldEvent(player, playerWorld, lastSavedWorldName);
			Bukkit.getPluginManager().callEvent(worldEvent);

			playerWorld = worldEvent.getWorld();
			MMLog.trace("After PlayerJoinSetWorldEvent for player '" + playerName + "' got world={" + playerWorld.getUID() + ": " + playerWorld.getName() + "}");

			final JsonObject worldShardDataJson;
			if (shardData == null || shardData.isEmpty()) {
				MMLog.trace("No shard data for player '" + playerName + "'");
				worldShardDataJson = new JsonObject();
			} else {
				/* Look up in the shard data first the "world" part - data from this world about where the player should be */
				String worldShardData = shardData.get(MonumentaRedisSyncAPI.getRedisPerShardDataWorldKey(playerWorld));
				if (worldShardData == null || worldShardData.isEmpty()) {
					MMLog.trace("No world shard data for player '" + playerName + "', using default");
					worldShardDataJson = new JsonObject();
				} else {
					MMLog.trace("Found world shard data for player '" + playerName + "': '" + worldShardData + "'");
					worldShardDataJson = mGson.fromJson(worldShardData, JsonObject.class);
				}
			}

			/* At this point shardDataJson is a JSON object, possibly empty or containing this world's last saved data elements */

			if (!worldShardDataJson.has("Pos")) {
				// No position data, put player at world spawn
				Location spawn = playerWorld.getSpawnLocation();

				JsonArray pos = new JsonArray();
				pos.add(spawn.getX());
				pos.add(spawn.getY());
				pos.add(spawn.getZ());
				worldShardDataJson.add("Pos", pos);

				JsonArray rotation = new JsonArray();
				rotation.add(spawn.getYaw());
				rotation.add(spawn.getPitch());
				worldShardDataJson.add("Rotation", rotation);
			}

			worldShardDataJson.addProperty("world", playerWorld.getName());

			/* At this point shardDataJson contains at minimum the world the player should be attached to and the location/rotation */

			Object nbtTagCompound = mAdapter.retrieveSaveData(data, worldShardDataJson);
			event.setData(nbtTagCompound);

			MMLog.debug(() -> "Processing PlayerDataLoadEvent took " + (System.currentTimeMillis() - startTime) + " milliseconds on main thread");
		} catch (Throwable ex) {
			MMLog.severe("!!! Failed to load player data !!!", ex);
			mSessions.abortLoad(session, "playerdata failed to load");

			final var rootPath = mPlugin.getDataFolder().toPath()
				.resolve("data-fail-report-%s-%s-%s".formatted(
					DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS").format(LocalDateTime.now(ZoneId.systemDefault())),
					playerName,
					playerId
				));

			MMLog.severe("Writing data files to for analysis..." + rootPath);

			try {
				Files.createDirectories(rootPath);
				trySave(rootPath, "error.txt", dest -> Files.writeString(dest, exceptionToString(ex)));
				trySave(rootPath, "data.nbt", dest -> Files.write(dest, dataFuture.get()));
				trySave(rootPath, "plugin_data.json", dest -> Files.writeString(dest, pluginDataFuture.get()));
				trySave(rootPath, "score.json", dest -> Files.writeString(dest, scoreFuture.get()));
				trySave(rootPath, "shard", dest -> {
					Files.createDirectories(dest);
					for (final var ent : shardDataFuture.get().entrySet()) {
						Files.writeString(dest.resolve(ent.getKey() + ".json"), ent.getValue());
					}
				});
			} catch (IOException e) {
				// there's nothing we can do here if we can't create the directory...
				MMLog.severe("Failed to create directory for saving player info", e);
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerDataSaveEvent(PlayerDataSaveEvent event) {
		event.setCancelled(true);

		if (BukkitConfigAPI.getSavingDisabled()) {
			return;
		}

		Player player = event.getPlayer();
		String playerName = player.getName();
		PlayerSession session = mSessions.sessionForSave(player, "playerdata");
		if (session == null) {
			return;
		}

		MMLog.debug("Saving data for player=" + playerName);

		JsonObject pluginData = session.getPluginData();

		/* Gives other plugins a chance to add their data */
		long startTime = System.currentTimeMillis();
		PlayerSaveEvent newEvent = new PlayerSaveEvent(player);
		Bukkit.getPluginManager().callEvent(newEvent);

		Map<String, JsonObject> eventData = newEvent.getPluginData();
		for (Map.Entry<String, JsonObject> ent : eventData.entrySet()) {
			pluginData.add(ent.getKey(), ent.getValue());
		}
		MMLog.debug(() -> "Getting plugindata from other plugins took " + (System.currentTimeMillis() - startTime) + " milliseconds");

		try {
			SaveData data = mAdapter.extractSaveData(event.getData(), session.getReturnParams());

			MMLog.trace(() -> "data: " + b64encode(data.getData()));
			String dataPath = MonumentaRedisSyncAPI.getRedisDataPath(player);
			/* Counted before either transaction runs: the advancements save that follows, and a lock's savePushedEntry, rely on it */
			session.beginPlayerdataSave();
			session.trackSave(RedisAPI.multiStringBytes(byteConn -> {
				byteConn.lpush(dataPath, data.getData());
				byteConn.ltrim(dataPath, 0, BukkitConfigAPI.getHistoryAmount());
			}), () -> "Failed to save player nbt data for player=" + playerName);

			/*
			 * sharddata
			 * This has two parts - an entry for the overall shard, and an entry for the specific world the player is on
			 */
			String shardDataPath = MonumentaRedisSyncAPI.getRedisPerShardDataPath(player);
			// Save the data specifically for the world the player is currently on
			String worldKey = MonumentaRedisSyncAPI.getRedisPerShardDataWorldKey(player.getWorld());
			// Also update the local sharddata cache
			Map<String, String> shardDataMap = session.getShardData();
			shardDataMap.put(worldKey, data.getShardData());
			MMLog.trace("sharddata (world): " + worldKey + "=" + data.getShardData());

			// Save the data for this shard indicating which world the player is currently on
			JsonObject overallShardData = new JsonObject();
			overallShardData.addProperty("World", player.getWorld().getName());
			String overallShardDataStr = mGson.toJson(overallShardData);
			shardDataMap.put(BukkitConfigAPI.getShardName(), overallShardDataStr);
			MMLog.trace("sharddata (overall): " + BukkitConfigAPI.getShardName() + "=" + overallShardDataStr);

			/* history */
			String histPath = MonumentaRedisSyncAPI.getRedisHistoryPath(player);
			String history = BukkitConfigAPI.getShardName() + "|" + System.currentTimeMillis() + "|" + playerName;
			MMLog.trace(() -> "history: " + history);

			/* content */
			String contentPath = MonumentaRedisSyncAPI.getRedisContentPath(player);
			String contentData = mGson.toJson(session.getContentData().toJson());
			MMLog.trace(() -> "content: " + contentData);

			/* plugindata */
			String pluginDataPath = MonumentaRedisSyncAPI.getRedisPluginDataPath(player);
			String pluginDataStr = mGson.toJson(pluginData);
			MMLog.trace(() -> "plugindata: " + pluginDataStr);

			/* Scoreboards */
			MMLog.debug("Saving scoreboard data for player=" + playerName);
			long scoreStartTime = System.currentTimeMillis();
			String scoreboardData = mGson.toJson(mAdapter.getPlayerScoresAsJson(playerName, Bukkit.getScoreboardManager().getMainScoreboard()));
			MMLog.debug(() -> "Scoreboard saving took " + (System.currentTimeMillis() - scoreStartTime) + " " + "milliseconds on main thread");
			MMLog.trace(() -> "Data:" + scoreboardData);
			String scorePath = MonumentaRedisSyncAPI.getRedisScoresPath(player);

			session.trackSave(RedisAPI.multi(commands -> {
				commands.hset(shardDataPath, worldKey, data.getShardData());
				commands.hset(shardDataPath, BukkitConfigAPI.getShardName(), overallShardDataStr);
				commands.lpush(histPath, history);
				commands.ltrim(histPath, 0, BukkitConfigAPI.getHistoryAmount());
				commands.lpush(contentPath, contentData);
				commands.ltrim(contentPath, 0, BukkitConfigAPI.getHistoryAmount());
				commands.lpush(pluginDataPath, pluginDataStr);
				commands.ltrim(pluginDataPath, 0, BukkitConfigAPI.getHistoryAmount());
				commands.lpush(scorePath, scoreboardData);
				commands.ltrim(scorePath, 0, BukkitConfigAPI.getHistoryAmount());
			}), () -> "Failed to save player data for player=" + playerName);
		} catch (IOException ex) {
			MMLog.severe("Failed to save player data for player=" + playerName, ex);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerLoginEvent(PlayerLoginEvent event) {
		Player player = event.getPlayer();
		String nameStr = player.getName();
		UUID uuid = player.getUniqueId();
		String uuidStr = uuid.toString();

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.hset("uuid2name", uuidStr, nameStr);
			conn.hset("name2uuid", nameStr, uuidStr);
		}
		MonumentaRedisSyncAPI.updateUuidToName(uuid, nameStr);
		MonumentaRedisSyncAPI.updateNameToUuid(nameStr, uuid);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void playerJoinEvent(PlayerJoinEvent event) {
		notifyContentChange(event.getPlayer().getUniqueId());
	}

	protected static void notifyContentChange(UUID playerUUID) {
		String permission = BukkitConfigAPI.getNotifyContentPermission();
		if (permission == null) {
			return;
		}

		ContentData contentData = PlayerSessions.getContentData(playerUUID);
		String contentId = contentData.getId().isEmpty() ? "<not set>" : contentData.getId();

		Bukkit.getScheduler().runTaskLater(INSTANCE.mPlugin, () -> {
			Player player = Bukkit.getPlayer(playerUUID);
			if (player != null && player.hasPermission(permission)) {
				player.sendMessage(Component.text("Joined content " + contentId, NamedTextColor.YELLOW));
			}
		}, 1L);
	}

	private static String b64encode(byte[] data) {
		return Base64.getEncoder().encodeToString(data);
	}
}
