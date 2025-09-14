package com.playmonumenta.redissync;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerAdvancementDataSaveEvent;
import com.destroystokyo.paper.event.player.PlayerDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerDataSaveEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.adapters.VersionAdapter.ReturnParams;
import com.playmonumenta.redissync.adapters.VersionAdapter.SaveData;
import com.playmonumenta.redissync.config.BukkitConfig;
import com.playmonumenta.redissync.event.PlayerJoinSetWorldEvent;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import com.playmonumenta.redissync.event.PlayerTransferFailEvent;
import com.playmonumenta.redissync.utils.ScoreboardUtils;
import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
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
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;

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

	private static final Map<UUID, BukkitTask> TRANSFER_UNLOCK_TASKS = new HashMap<>(); // TODO Never queried
	private static final int TRANSFER_UNLOCK_TIMEOUT_TICKS = 10 * 20;
	private static final Component LOAD_ERROR_MSG =
		Component.text("Critical error occurred when loading playerdata! Please notify a moderator.", NamedTextColor.RED);
	@SuppressWarnings("NullAway") // Required to avoid many null checks, this class will always be instantiated if this plugin is loaded
	private static DataEventListener INSTANCE = null;

	private final Gson mGson = new Gson();
	private final Logger mLogger;
	private final VersionAdapter mAdapter;
	private final Set<UUID> mTransferringPlayers = new HashSet<>();
	private final Map<UUID, ReturnParams> mReturnParams = new HashMap<>();
	/* Key = shoulder entity UUID (i.e. parrot), value = player */
	private final Map<UUID, UUID> mTransferringPlayerShoulderEntities = new LinkedHashMap<>();

	private final Map<UUID, List<RedisFuture<?>>> mPendingSaves = new HashMap<>();
	private final Map<UUID, JsonObject> mPluginData = new HashMap<>();
	private final Set<UUID> mLoadingPlayers = new HashSet<>();
	private final Set<UUID> mLoadFailedPlayers = new HashSet<>();

	/*
	 * Cached local copy of shard data to provide to API to get player locations on other worlds
	 * Every player that has fully logged into this shard is guaranteed to have an entry in this map
	 */
	private final Map<UUID, Map<String, String>> mShardData = new HashMap<>();

	protected DataEventListener(Logger logger, VersionAdapter adapter) {
		mLogger = logger;
		mAdapter = adapter;
		INSTANCE = this;

		Bukkit.getServer().getScheduler().runTaskAsynchronously(MonumentaRedisSync.getInstance(), () -> {
			KeyValueStreamingChannel<String, String> uuidToNameChannel = new PlayerUuidToNameStreamingChannel();
			RedisAPI.getInstance().async().hgetall(uuidToNameChannel, "uuid2name");

			KeyValueStreamingChannel<String, String> nameToUuidChannel = new PlayerNameToUuidStreamingChannel();
			RedisAPI.getInstance().async().hgetall(nameToUuidChannel, "name2uuid");
		});
	}

	/* ******************* Protected API ******************* */

	protected static void setPlayerAsTransferring(Player player) throws Exception {
		if (INSTANCE.mTransferringPlayers.contains(player.getUniqueId())) {
			throw new Exception("Player " + player.getName() + " is already transferring");
		}

		INSTANCE.mTransferringPlayers.add(player.getUniqueId());

		/* Record transferring player shoulder entity UUIDs to prevent them from being duplicated into the world by timing exploit */
		Entity shoulder = player.getShoulderEntityLeft();
		if (shoulder != null) {
			INSTANCE.mTransferringPlayerShoulderEntities.put(shoulder.getUniqueId(), player.getUniqueId());
		}
		shoulder = player.getShoulderEntityRight();
		if (shoulder != null) {
			INSTANCE.mTransferringPlayerShoulderEntities.put(shoulder.getUniqueId(), player.getUniqueId());
		}

		/*
		 * Start a task to automatically unlock the player if transfer times out.
		 * This task is cancelled when player leaves the server (PlayerQuitEvent)
		 */
		TRANSFER_UNLOCK_TASKS.put(player.getUniqueId(), Bukkit.getScheduler().runTaskLater(MonumentaRedisSync.getInstance(), () -> {
			if (DataEventListener.isPlayerTransferring(player)) {
				player.sendMessage(Component.text("Transferring timed out and your player has been unlocked", NamedTextColor.RED));
				DataEventListener.setPlayerAsNotTransferring(player);
			}
			TRANSFER_UNLOCK_TASKS.remove(player.getUniqueId());
		}, TRANSFER_UNLOCK_TIMEOUT_TICKS));
	}

	protected static void setPlayerReturnParams(Player player, @Nullable Location returnLoc, @Nullable Float returnYaw, @Nullable Float returnPitch) {
		INSTANCE.mReturnParams.put(player.getUniqueId(), new ReturnParams(returnLoc, returnYaw, returnPitch));
	}

	protected static void setPlayerAsNotTransferring(Player player) {
		boolean wasTransferring = INSTANCE.mTransferringPlayers.remove(player.getUniqueId());
		INSTANCE.mReturnParams.remove(player.getUniqueId());

		/* Remove the shoulder entity spawn block (i.e. parrot) when player is not transferring anymore */
		INSTANCE.mTransferringPlayerShoulderEntities.entrySet().removeIf(entry -> entry.getValue().equals(player.getUniqueId()));

		if (wasTransferring) {
			PlayerTransferFailEvent event = new PlayerTransferFailEvent(player);
			Bukkit.getPluginManager().callEvent(event);
		}
	}

	protected static boolean isPlayerTransferring(Player player) {
		return INSTANCE.mTransferringPlayers.contains(player.getUniqueId());
	}

	protected static void waitForPlayerToSaveThenSync(Player player, Runnable callback) {
		INSTANCE.waitForPlayerToSaveInternal(player, callback, true);
	}

	protected static void waitForPlayerToSaveThenAsync(Player player, Runnable callback) {
		INSTANCE.waitForPlayerToSaveInternal(player, callback, false);
	}

	protected static @Nullable JsonObject getPlayerPluginData(UUID uuid) {
		return INSTANCE.mPluginData.get(uuid);
	}

	protected static @Nullable Map<String, String> getPlayerShardData(UUID uuid) {
		return INSTANCE.mShardData.get(uuid);
	}

	private void waitForPlayerToSaveInternal(Player player, Runnable callback, boolean sync) {
		Plugin plugin = MonumentaRedisSync.getInstance();

		if (!mPendingSaves.containsKey(player.getUniqueId()) && !BukkitConfig.getSavingDisabled()) {
			mLogger.warning("Got request to wait for save commit but no pending save operations found. This might be a bug with the plugin that uses MonumentaRedisSync");
		}

		long startTime = System.currentTimeMillis();

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			blockingWaitForPlayerToSave(player);

			mLogger.fine(() -> "Committing save took " + (System.currentTimeMillis() - startTime) + " milliseconds");

			/* Run the callback after about 150ms have passed to make sure the redis changes commit */
			if (sync) {
				/* Run the sync callback on the main thread */
				Bukkit.getServer().getScheduler().runTaskLater(plugin, callback, 3);
			} else {
				/* Run the async callback */
				Bukkit.getServer().getScheduler().runTaskLaterAsynchronously(plugin, callback, 3);
			}
		});
	}

	private void blockingWaitForPlayerToSave(Player player) {
		List<RedisFuture<?>> futures = mPendingSaves.remove(player.getUniqueId());

		if (futures == null || futures.isEmpty()) {
			return;
		}

		mLogger.fine("Blocking wait for pending save for player=" + player.getName());

		if (!LettuceFutures.awaitAll(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[0]))) {
			mLogger.severe("Got timeout waiting to commit transactions for player '" + player.getName() + "'. This is very bad!");
		}

		mLogger.fine("Pending save completed for player=" + player.getName());
	}

	/* ******************* Data Save/Load Event Handlers ******************* */

	/*
	 * When running /minecraft:reload, this event is triggered before player advancement data is reloaded
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void serverResourcesReloadedEvent(ServerResourcesReloadedEvent event) {
		mLogger.fine("ServerResourcesReloadedEvent caused by " + event.getCause() + ", triggering save for all players...");
		for (Player player : Bukkit.getOnlinePlayers()) {
			mLogger.finer("Saving player " + player.getName() + " due to datapack reload");
			try {
				MonumentaRedisSyncAPI.savePlayer(player);
			} catch (Exception ex) {
				mLogger.severe("Failed to save player '" + player.getName() + "': " + ex.getMessage());
				ex.printStackTrace();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataLoadEvent(PlayerAdvancementDataLoadEvent event) {
		Player player = event.getPlayer();

		if (BukkitConfig.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return;
		}

		long startTime = System.currentTimeMillis();
		mLogger.fine("Started loading advancements data for player=" + player.getName());

		/* Wait until player has finished saving if they just logged out and back in */
		blockingWaitForPlayerToSave(player);

		RedisFuture<String> advanceFuture = RedisAPI.getInstance().async().lindex(MonumentaRedisSyncAPI.getRedisAdvancementsPath(player), 0);

		try {
			/* Advancements */
			final String advanceData = advanceFuture.get();
			mLogger.finer(() -> "Advancements data loaded for player=" + player.getName());
			mLogger.finest(() -> "Advancements data:" + advanceData);
			if (advanceData != null) {
				event.setJsonData(advanceData);
			} else {
				mLogger.warning("No advancements data for player '" + player.getName() + "' - if they are not new, this is a serious error!");
			}

			mLogger.fine(() -> "Processing PlayerAdvancementDataLoadEvent took " + (System.currentTimeMillis() - startTime) + " milliseconds on main thread");
		} catch (InterruptedException | ExecutionException ex) {
			mLogger.severe("Failed to get advancements data for player '" + player.getName() + "'. This is very bad!");
			ex.printStackTrace();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataSaveEvent(PlayerAdvancementDataSaveEvent event) {
		/* Always cancel saving the player file to disk with this plugin present */
		event.setCancelled(true);

		if (BukkitConfig.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return;
		}

		Player player = event.getPlayer();
		if (isPlayerTransferring(player)) {
			mLogger.fine("Ignoring PlayerAdvancementDataSaveEvent for player:" + player.getName());
			return;
		}

		List<RedisFuture<?>> futures = mPendingSaves.remove(player.getUniqueId());
		if (futures == null) {
			futures = new ArrayList<>();
		} else {
			futures.removeIf(Future::isDone);
		}

		/* Execute the advancements as a multi() batch */
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		futures.add(commands.multi()); /* < MULTI */

		/* Advancements */
		mLogger.fine("Saving advancements data for player=" + player.getName());
		mLogger.finest(() -> "Data:" + event.getJsonData());
		String advPath = MonumentaRedisSyncAPI.getRedisAdvancementsPath(player);
		commands.lpush(advPath, event.getJsonData());
		commands.ltrim(advPath, 0, BukkitConfig.getHistoryAmount());

		futures.add(commands.exec()); /* MULTI > */

		/* Don't block - store the pending futures for completion later */
		mPendingSaves.put(player.getUniqueId(), futures);
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
			mLogger.severe("failed to save data to file");
			e.printStackTrace();
			try {
				Files.writeString(path.resolve(name + ".save_error.txt"), exceptionToString(e));
			} catch (Throwable e2) {
				mLogger.severe("I give up!");
				e2.printStackTrace();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerDataLoadEvent(PlayerDataLoadEvent event) {
		Player player = event.getPlayer();

		if (BukkitConfig.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return;
		}

		long startTime = System.currentTimeMillis();
		mLogger.fine("Started loading data for player=" + player.getName());

		/* Wait until player has finished saving if they just logged out and back in */
		blockingWaitForPlayerToSave(player);

		RedisFuture<byte[]> dataFuture = RedisAPI.getInstance().asyncStringBytes().lindex(MonumentaRedisSyncAPI.getRedisDataPath(player), 0);
		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		commands.multi();
		RedisFuture<String> pluginDataFuture = commands.lindex(MonumentaRedisSyncAPI.getRedisPluginDataPath(player), 0);
		RedisFuture<String> scoreFuture = commands.lindex(MonumentaRedisSyncAPI.getRedisScoresPath(player), 0);
		RedisFuture<Map<String, String>> shardDataFuture = commands.hgetall(MonumentaRedisSyncAPI.getRedisPerShardDataPath(player));
		commands.exec();

		try {
			/* Load the primary shared NBT data */
			byte[] data = dataFuture.get();
			if (data == null) {
				mLogger.warning("No data for player '" + player.getName() + "' - if they are not new, this is a serious error!");
				return;
			}
			mLogger.finer("Player data loaded for player=" + player.getName());
			mLogger.finest(() -> "Player data: " + b64encode(data));

			/* Load plugin data */
			String pluginData = pluginDataFuture.get();
			if (pluginData == null) {
				mLogger.fine("Player '" + player.getName() + "' has no plugin data");
			} else {
				mLoadingPlayers.add(player.getUniqueId());
				mPluginData.put(player.getUniqueId(), mGson.fromJson(pluginData, JsonObject.class));
				mLogger.finer("Plugin data loaded for player=" + player.getName());
				mLogger.finest(() -> "Plugin data: " + pluginData);
			}

			/* Load scoreboards */
			final String scoreData = scoreFuture.get();
			mLogger.fine("Scoreboard data loaded for player=" + player.getName());
			mAdapter.resetPlayerScores(player.getName(), Bukkit.getScoreboardManager().getMainScoreboard());
			mLogger.finest(() -> "Score data:" + scoreData);
			if (scoreData != null) {
				JsonObject obj = mGson.fromJson(scoreData, JsonObject.class);
				if (obj != null) {
					ScoreboardUtils.loadFromJsonObject(player, obj);
				} else {
					mLogger.severe("Failed to parse player '" + player.getName() + "' scoreboard data as JSON. This results in data loss!");
				}
			} else {
				mLogger.warning("No scoreboard data for player '" + player.getName() + "' - if they are not new, this is a serious error!");
			}

			/* Get all the shard data for all shards and worlds */
			Map<String, String> shardData = shardDataFuture.get();
			/* Look up in the shard data first the "overall" part - which world this player was on last time they were on this shard */
			World playerWorld = null; // If null at the end of this block, will use default world
			UUID lastSavedWorldUUID = null; // The saved world UUID from shard data. Might be different from the playerWorld if save data indicated one world, but it is not loaded so fell back to the default
			String lastSavedWorldName = null; // The saved world name from shard data. Might be different from the playerWorld if save data indicated one world, but it is not loaded so fell back to the default
			if (shardData == null) {
				/* Maintain a local cache of shard data while the player is logged in here */
				mShardData.put(player.getUniqueId(), new HashMap<>());

				/* This is not an error - this will happen whenever a player first joins the game */
				mLogger.fine("Player '" + player.getName() + "' has never been to any shard before");
			} else {
				/* Maintain a local cache of shard data while the player is logged in here */
				mShardData.put(player.getUniqueId(), shardData);

				mLogger.finer("Shard data loaded for player=" + player.getName());
				mLogger.finest(() -> "Shard data: " + mGson.toJson(shardData));

				/* Figure out what world the player's sharddata indicates they should join
				 * If shard data does not contain this shard name, no info on what world to use - use the default one
				 * If shard data contains this shard name, fetch world parameters from it, preferring UUID, then name. Loaded worlds only, this plugin does not load worlds automatically.
				 */
				String overallShardData = shardData.get(BukkitConfig.getShardName());
				if (overallShardData == null) {
					/* This is not an error - this will happen whenever a player first visits a new shard */
					mLogger.fine("Player '" + player.getName() + "' has never been to this shard before");
				} else {
					JsonObject shardDataJson = mGson.fromJson(overallShardData, JsonObject.class);

					if (shardDataJson.has("WorldUUID")) {
						try {
							lastSavedWorldUUID = UUID.fromString(shardDataJson.get("WorldUUID").getAsString());
							World world = Bukkit.getWorld(lastSavedWorldUUID);
							if (world != null) {
								playerWorld = world;
							}
						} catch (Exception ex) {
							mLogger.severe("Got sharddata WorldUUID='" + shardDataJson.get("WorldUUID").getAsString() + "' which is invalid: " + ex.getMessage());
							ex.printStackTrace();
						}
					}

					if (shardDataJson.has("World")) {
						lastSavedWorldName = shardDataJson.get("World").getAsString();
					}

					if (playerWorld == null && lastSavedWorldName != null) {
						World world = Bukkit.getWorld(lastSavedWorldName);
						if (world != null) {
							playerWorld = world;
						}
					}
				}
			}

			if (playerWorld == null) {
				playerWorld = Bukkit.getWorlds().get(0);
			}

			/* After this point playerWorld is always non-null and a valid loaded world */

			// Throw an event that lets other plugins modify the join world.
			mLogger.finer("Calling PlayerJoinSetWorldEvent for player '" + player.getName() + "' with world={" + playerWorld.getUID() + ": " + playerWorld.getName() + "}, lastSavedWorld={" + lastSavedWorldUUID + ": " + lastSavedWorldName + "}");
			PlayerJoinSetWorldEvent worldEvent = new PlayerJoinSetWorldEvent(player, playerWorld, lastSavedWorldUUID, lastSavedWorldName);
			Bukkit.getPluginManager().callEvent(worldEvent);

			playerWorld = worldEvent.getWorld();
			mLogger.finer("After PlayerJoinSetWorldEvent for player '" + player.getName() + "' got world={" + playerWorld.getUID() + ": " + playerWorld.getName() + "}");

			final JsonObject shardDataJson;
			if (shardData == null || shardData.isEmpty()) {
				mLogger.finer("No shard data for player '" + player.getName() + "'");
				shardDataJson = new JsonObject();
			} else {
				/* Look up in the shard data first the "world" part - data from this world about where the player should be */
				String worldShardData = shardData.get(MonumentaRedisSyncAPI.getRedisPerShardDataWorldKey(playerWorld));
				if (worldShardData == null || worldShardData.isEmpty()) {
					mLogger.finer("No world shard data for player '" + player.getName() + "', using default");
					shardDataJson = new JsonObject();
				} else {
					mLogger.finer("Found world shard data for player '" + player.getName() + "': '" + worldShardData + "'");
					shardDataJson = mGson.fromJson(worldShardData, JsonObject.class);
				}
			}

			/* At this point shardDataJson is a JSON object, possibly empty or containing this world's last saved data elements */

			if (!shardDataJson.has("Pos")) {
				// No position data, put player at world spawn
				Location spawn = playerWorld.getSpawnLocation();

				JsonArray pos = new JsonArray();
				pos.add(spawn.getX());
				pos.add(spawn.getY());
				pos.add(spawn.getZ());
				shardDataJson.add("Pos", pos);

				JsonArray rotation = new JsonArray();
				rotation.add(spawn.getYaw());
				rotation.add(spawn.getPitch());
				shardDataJson.add("Rotation", rotation);
			}

			shardDataJson.addProperty("world", playerWorld.getName());
			shardDataJson.addProperty("WorldUUIDMost", playerWorld.getUID().getMostSignificantBits());
			shardDataJson.addProperty("WorldUUIDLeast", playerWorld.getUID().getLeastSignificantBits());

			/* At this point shardDataJson contains at minimum the world the player should be attached to and the location/rotation */

			Object nbtTagCompound = mAdapter.retrieveSaveData(data, shardDataJson);
			event.setData(nbtTagCompound);

			mLogger.fine(() -> "Processing PlayerDataLoadEvent took " + (System.currentTimeMillis() - startTime) + " milliseconds on main thread");
		} catch (Throwable ex) {
			mLoadFailedPlayers.add(event.getPlayer().getUniqueId());
			mLogger.severe("!!! Failed to load player data !!!");
			ex.printStackTrace();

			final var rootPath = MonumentaRedisSync.getInstance().getDataFolder().toPath()
				.resolve("data-fail-report-%s-%s-%s".formatted(
					DateTimeFormatter.ofPattern("yyyy-MM-dd-HH-mm-ss-SSS").format(LocalDateTime.now(ZoneId.systemDefault())),
					player.getName(),
					player.getUniqueId()
				));

			mLogger.severe("Writing data files to for analysis..." + rootPath);

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
				mLogger.severe("Failed to create directory for saving player info");
				e.printStackTrace();
			}

			mLogger.severe("Bail: kicking player early in order to prevent data loss!");
			Bukkit.getScheduler().runTask(MonumentaRedisSync.getInstance(), () -> event.getPlayer().kick(LOAD_ERROR_MSG));
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerDataSaveEvent(PlayerDataSaveEvent event) {
		event.setCancelled(true);

		if (BukkitConfig.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return;
		}

		if(mLoadFailedPlayers.contains(event.getPlayer().getUniqueId())) {
			mLoadFailedPlayers.remove(event.getPlayer().getUniqueId());
			mLogger.warning("Skipping playerdata save for " + event.getPlayer().getUniqueId() + " because their playerdata failed to load");
			return;
		}

		Player player = event.getPlayer();
		if (isPlayerTransferring(player)) {
			mLogger.fine("Ignoring PlayerDataSaveEvent for player:" + player.getName());
			return;
		}

		mLogger.fine("Saving data for player=" + player.getName());

		List<RedisFuture<?>> futures = mPendingSaves.remove(player.getUniqueId());
		if (futures == null) {
			futures = new ArrayList<>();
		} else {
			futures.removeIf(Future::isDone);
		}

		/* Get the existing plugin data */
		JsonObject pluginData = mPluginData.computeIfAbsent(player.getUniqueId(), k -> new JsonObject());

		/* Call a custom save event that gives other plugins a chance to add data */
		/* This is skipped until the join event finishes to prevent losing data if a save happens while joining */
		if (!mLoadingPlayers.contains(player.getUniqueId())) {
			long startTime = System.currentTimeMillis();
			PlayerSaveEvent newEvent = new PlayerSaveEvent(player);
			Bukkit.getPluginManager().callEvent(newEvent);

			/* Merge any data from the save event to the player's locally cached plugin data */
			Map<String, JsonObject> eventData = newEvent.getPluginData();
			for (Map.Entry<String, JsonObject> ent : eventData.entrySet()) {
				pluginData.add(ent.getKey(), ent.getValue());
			}
			mLogger.fine(() -> "Getting plugindata from other plugins took " + (System.currentTimeMillis() - startTime) + " milliseconds");
		} else {
			mLogger.fine(() -> "Skipped fetching plugindata from other plugins, as the player hasn't finished joining yet");
		}

		try {
			/* Grab the return parameters if they were set when starting transfer. If they are null, that's fine too */
			ReturnParams returnParams = mReturnParams.get(player.getUniqueId());
			SaveData data = mAdapter.extractSaveData(event.getData(), returnParams);

			mLogger.finest(() -> "data: " + b64encode(data.getData()));
			String dataPath = MonumentaRedisSyncAPI.getRedisDataPath(player);
			futures.add(RedisAPI.getInstance().asyncStringBytes().lpush(dataPath, data.getData()));
			futures.add(RedisAPI.getInstance().asyncStringBytes().ltrim(dataPath, 0, BukkitConfig.getHistoryAmount()));

			/* Execute the sharddata, history and plugin data as a multi() batch */
			RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
			futures.add(commands.multi()); /* < MULTI */

			/*
			 * sharddata
			 * This has two parts - an entry for the overall shard, and an entry for the specific world the player is on
			 */
			String shardDataPath = MonumentaRedisSyncAPI.getRedisPerShardDataPath(player);
			// Save the data specifically for the world the player is currently on
			String worldKey = MonumentaRedisSyncAPI.getRedisPerShardDataWorldKey(player.getWorld());
			commands.hset(shardDataPath, worldKey, data.getShardData());
			// Also update the local sharddata cache
			Map<String, String> shardDataMap = mShardData.get(player.getUniqueId());
			if (shardDataMap == null) {
				mLogger.warning("BUG! There was no player entry in the mShardData map for uuid=" + player.getUniqueId() + " name=" + player.getName() + ". This is not a fatal error, but player locations are likely wrong in some corner cases...");
			} else {
				shardDataMap.put(worldKey, data.getShardData());
			}
			mLogger.finest("sharddata (world): " + worldKey + "=" + data.getShardData());

			// Save the data for this shard indicating which world the player is currently on
			JsonObject overallShardData = new JsonObject();
			overallShardData.addProperty("WorldUUID", player.getWorld().getUID().toString());
			overallShardData.addProperty("World", player.getWorld().getName());
			String overallShardDataStr = mGson.toJson(overallShardData);
			commands.hset(shardDataPath, BukkitConfig.getShardName(), overallShardDataStr);
			if (shardDataMap != null) {
				shardDataMap.put(BukkitConfig.getShardName(), overallShardDataStr);
			}
			mLogger.finest("sharddata (overall): " + BukkitConfig.getShardName() + "=" + overallShardDataStr);

			/* history */
			String histPath = MonumentaRedisSyncAPI.getRedisHistoryPath(player);
			String history = BukkitConfig.getShardName() + "|" + System.currentTimeMillis() + "|" + player.getName();
			mLogger.finest(() -> "history: " + history);
			commands.lpush(histPath, history);
			commands.ltrim(histPath, 0, BukkitConfig.getHistoryAmount());

			/* plugindata */
			String pluginDataPath = MonumentaRedisSyncAPI.getRedisPluginDataPath(player);
			mPluginData.put(player.getUniqueId(), pluginData); // Update cache
			String pluginDataStr = mGson.toJson(pluginData);
			mLogger.finest(() -> "plugindata: " + pluginDataStr);
			commands.lpush(pluginDataPath, pluginDataStr);
			commands.ltrim(pluginDataPath, 0, BukkitConfig.getHistoryAmount());

			/* Scoreboards */
			mLogger.fine("Saving scoreboard data for player=" + player.getName());
			long scoreStartTime = System.currentTimeMillis();

			String scoreboardData = mGson.toJson(mAdapter.getPlayerScoresAsJson(player.getName(), Bukkit.getScoreboardManager().getMainScoreboard()));
			mLogger.fine(() -> "Scoreboard saving took " + (System.currentTimeMillis() - scoreStartTime) + " " + "milliseconds on main thread");
			mLogger.finest(() -> "Data:" + scoreboardData);
			String scorePath = MonumentaRedisSyncAPI.getRedisScoresPath(player);
			commands.lpush(scorePath, scoreboardData);
			commands.ltrim(scorePath, 0, BukkitConfig.getHistoryAmount());

			futures.add(commands.exec()); /* MULTI > */
		} catch (IOException ex) {
			mLogger.severe("Failed to save player data: " + ex);
			ex.printStackTrace();
		}

		/* Don't block - store the pending futures for completion later */
		mPendingSaves.put(player.getUniqueId(), futures);
	}

	/* ******************* Transferring Restriction Event Handlers ******************* */

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerLoginEvent(PlayerLoginEvent event) {
		/* NOTE: This runs very early in the login process! Just want to make sure player isn't transferring anymore */
		Player player = event.getPlayer();

		setPlayerAsNotTransferring(player);

		String nameStr = player.getName();
		UUID uuid = player.getUniqueId();
		String uuidStr = uuid.toString();

		Bukkit.getServer().getScheduler().runTaskAsynchronously(MonumentaRedisSync.getInstance(), () -> {
			RedisAPI.getInstance().async().hset("uuid2name", uuidStr, nameStr);
			RedisAPI.getInstance().async().hset("name2uuid", nameStr, uuidStr);
			MonumentaRedisSyncAPI.updateUuidToName(uuid, nameStr);
			MonumentaRedisSyncAPI.updateNameToUuid(nameStr, uuid);
		});
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Bukkit.getScheduler().runTask(MonumentaRedisSync.getInstance(),
			() -> mLoadingPlayers.remove(event.getPlayer().getUniqueId()));
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerQuitEvent(PlayerQuitEvent event) {
		Player player = event.getPlayer();

		UUID playerUUID = player.getUniqueId();

		TRANSFER_UNLOCK_TASKS.remove(playerUUID);

		Bukkit.getScheduler().runTaskLater(MonumentaRedisSync.getInstance(), () -> {
			// Abort if the player started joining again, even if they're not "online" yet
			if (mLoadingPlayers.contains(playerUUID)) {
				return;
			}

			if (Bukkit.getPlayer(playerUUID) == null) {
				mPluginData.remove(playerUUID);
				mShardData.remove(playerUUID);
			}
		}, 50);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerInteractEvent(PlayerInteractEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void blockPlaceEvent(BlockPlaceEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerInteractEntityEvent(PlayerInteractEntityEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerArmorStandManipulateEvent(PlayerArmorStandManipulateEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerDropItemEvent(PlayerDropItemEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerSwapHandItemsEvent(PlayerSwapHandItemsEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerFishEvent(PlayerFishEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerItemConsumeEvent(PlayerItemConsumeEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerItemDamageEvent(PlayerItemDamageEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerBedEnterEvent(PlayerBedEnterEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerGameModeChangeEvent(PlayerGameModeChangeEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void blockBreakEvent(BlockBreakEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entityPickupItemEvent(EntityPickupItemEvent event) {
		cancelEventIfTransferring(event.getEntity(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void inventoryClickEvent(InventoryClickEvent event) {
		cancelEventIfTransferring(event.getWhoClicked(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void inventoryDragEvent(InventoryDragEvent event) {
		cancelEventIfTransferring(event.getWhoClicked(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void inventoryOpenEvent(InventoryOpenEvent event) {
		cancelEventIfTransferring(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void inventoryInteractEvent(InventoryInteractEvent event) {
		cancelEventIfTransferring(event.getWhoClicked(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entityCombustByEntityEvent(EntityCombustByEntityEvent event) {
		cancelEventIfTransferring(event.getEntity(), event);
		cancelEventIfTransferring(event.getCombuster(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entityDamageByEntityEvent(EntityDamageByEntityEvent event) {
		cancelEventIfTransferring(event.getEntity(), event);
		cancelEventIfTransferring(event.getDamager(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entityDamageEvent(EntityDamageEvent event) {
		cancelEventIfTransferring(event.getEntity(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void hangingBreakByEntityEvent(HangingBreakByEntityEvent event) {
		cancelEventIfTransferring(event.getRemover(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void projectileLaunchEvent(ProjectileLaunchEvent event) {
		ProjectileSource shooter = event.getEntity().getShooter();
		if (shooter instanceof Player) {
			cancelEventIfTransferring((Player)shooter, event);
		}
	}

	/* Prevent shoulder entities of transferring players (i.e. parrots) from being duplicated into the world via timing exploit */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entitySpawnEvent(EntitySpawnEvent event) {
		if (mTransferringPlayerShoulderEntities.containsKey(event.getEntity().getUniqueId())) {
			mLogger.fine(() -> "Refused to spawn shoulder entity id: " + event.getEntity().getType() + " uuid: " + event.getEntity().getUniqueId());
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void potionSplashEvent(PotionSplashEvent event) {
		event.getAffectedEntities().removeIf(entity -> (entity instanceof Player && isPlayerTransferring((Player)entity)));
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void areaEffectCloudApplyEvent(AreaEffectCloudApplyEvent event) {
		event.getAffectedEntities().removeIf(entity -> (entity instanceof Player && isPlayerTransferring((Player)entity)));
	}

	/*
	 * This event fires very early in the chain, it also isn't safe to do database lookups
	 * Ideally this should also be done on the proxy but this is a failsafe in case a shard still has player data loaded
	 * Login events are fired in this order:
	 * - AsyncPlayerPreLoginEvent
	 * - PlayerJoinEvent
	 * However, PlayerJoinEvent will sometimes not get called if the player disconnects while logging in
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void kickPlayerIfDuplicateLogin(AsyncPlayerPreLoginEvent event) {
		if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			return;
		}
		PlayerProfile profile = event.getPlayerProfile();
		UUID uuid = profile.getId();
		if (uuid == null) {
			mLogger.warning(() -> "A player uuid=null" + " name=" + profile.getName() + " tried to login without a UUID! Preventing duplicate uuid stupidity");
			// Probably the most accurate kick message? Can't verify a username without a UUID, however we got into this state.
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.translatable("multiplayer.disconnect.unverified_username"));
			return;
		}
		// if player is loaded, but they are also trying to connect, disconnect the one trying to connect to prevent duplicate uuid stupidity
		if (Bukkit.getPlayer(uuid) != null || mLoadingPlayers.contains(uuid) || mShardData.containsKey(uuid)) {
			mLogger.warning(() -> "A player uuid=" + uuid + " name=" + profile.getName() + " tried to login while loading/online! Preventing duplicate uuid stupidity");
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.translatable("multiplayer.disconnect.duplicate_login"));
		}
	}

	/* ******************* Private Utility Methods ******************* */

	private void cancelEventIfTransferring(Entity entity, Cancellable event) {
		if (entity instanceof Player && isPlayerTransferring((Player) entity)) {
			event.setCancelled(true);
		}
	}

	private static String b64encode(byte[] data) {
		return Base64.getEncoder().encodeToString(data);
	}
}
