package com.playmonumenta.redissync;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.event.PlayerServerTransferEvent;
import com.playmonumenta.redissync.utils.Trie;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.wrappers.Rotation;
import io.lettuce.core.LettuceFutures;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.jetbrains.annotations.Nullable;

public class MonumentaRedisSyncAPI {
	public static final int TIMEOUT_SECONDS = 10;
	public static final ArgumentSuggestions<CommandSender> SUGGESTIONS_ALL_CACHED_PLAYER_NAMES = ArgumentSuggestions.strings((info) ->
		getAllCachedPlayerNames().toArray(String[]::new));

	private static final Trie<UUID> mNameToUuidTrie = new Trie<>();
	private static final Map<String, UUID> mNameToUuid = new ConcurrentHashMap<>();
	private static final Map<UUID, String> mUuidToName = new ConcurrentHashMap<>();

	protected static void updateUuidToName(UUID uuid, String name) {
		mUuidToName.put(uuid, name);
	}

	protected static void updateNameToUuid(String name, UUID uuid) {
		mNameToUuid.put(name, uuid);
		mNameToUuidTrie.put(name, uuid);
	}

	public static CompletableFuture<String> uuidToName(UUID uuid) {
		return MonumentaRedisSync.redisApi().async().hget("uuid2name", uuid.toString()).toCompletableFuture();
	}

	public static CompletableFuture<UUID> nameToUUID(String name) {
		return MonumentaRedisSync.redisApi().async().hget("name2uuid", name).thenApply((uuid) -> (uuid == null || uuid.isEmpty()) ? null : UUID.fromString(uuid)).toCompletableFuture();
	}

	public static CompletableFuture<Set<String>> getAllPlayerNames() {
		RedisFuture<Map<String, String>> future = MonumentaRedisSync.redisApi().async().hgetall("name2uuid");
		return future.thenApply(Map::keySet).toCompletableFuture();
	}

	public static CompletableFuture<Set<UUID>> getAllPlayerUUIDs() {
		RedisFuture<Map<String, String>> future = MonumentaRedisSync.redisApi().async().hgetall("uuid2name");
		return future.thenApply((data) -> data.keySet().stream().map(UUID::fromString).collect(Collectors.toSet())).toCompletableFuture();
	}

	public static @Nullable String cachedUuidToName(UUID uuid) {
		return mUuidToName.get(uuid);
	}

	public static @Nullable UUID cachedNameToUuid(String name) {
		return mNameToUuid.get(name);
	}

	public static Set<String> getAllCachedPlayerNames() {
		return new ConcurrentSkipListSet<>(mNameToUuid.keySet());
	}

	public static Set<UUID> getAllCachedPlayerUuids() {
		return new ConcurrentSkipListSet<>(mUuidToName.keySet());
	}

	public static @Nullable String getCachedCurrentName(String oldName) {
		UUID uuid = cachedNameToUuid(oldName);
		if (uuid == null) {
			return null;
		}
		return cachedUuidToName(uuid);
	}

	public static String getClosestPlayerName(String longestPossibleName) {
		@Nullable String result = mNameToUuidTrie.closestKey(longestPossibleName);
		if (result == null) {
			return "";
		}
		return result;
	}

	public static List<String> getSuggestedPlayerNames(String currentInput, int maxSuggestions) {
		return mNameToUuidTrie.suggestions(currentInput, maxSuggestions);
	}

	public static void sendPlayer(Player player, String target) throws Exception {
		sendPlayer(player, target, null);
	}

	public static void sendPlayer(Player player, String target, @Nullable Location returnLoc) throws Exception {
		sendPlayer(player, target, returnLoc, null, null);
	}

	public static void sendPlayer(Player player, String target, @Nullable Location returnLoc, @Nullable Rotation rotation) throws Exception {
		sendPlayer(player, target, returnLoc, rotation == null ? null : rotation.getNormalizedYaw(), rotation == null ? null : rotation.getNormalizedPitch());
	}

	public static void sendPlayer(Player player, String target, @Nullable Location returnLoc, @Nullable Float returnYaw, @Nullable Float returnPitch) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		/* Don't allow transferring while transferring */
		if (DataEventListener.isPlayerTransferring(player)) {
			return;
		}

		long startTime = System.currentTimeMillis();

		if (target.equalsIgnoreCase(MonumentaRedisSync.config().getShardName())) {
			player.sendMessage(Component.text("Can not transfer to the same server you are already on", NamedTextColor.RED));
			return;
		}

		/* If any return params were specified, mark them on the player */
		if (returnLoc != null || returnYaw != null || returnPitch != null) {
			DataEventListener.setPlayerReturnParams(player, returnLoc, returnYaw, returnPitch);
		}

		PlayerServerTransferEvent event = new PlayerServerTransferEvent(player, target);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return;
		}

		player.sendMessage(Component.text("Transferring you to " + target, NamedTextColor.GOLD));

		savePlayer(player);

		/* Lock player during transfer and prevent data saving when they log out */
		DataEventListener.setPlayerAsTransferring(player);

		DataEventListener.waitForPlayerToSaveThenSync(player, () -> {
			/*
			 * Use plugin messages to tell bungee to transfer the player.
			 * This is nice because in the event of multiple bungeecord's,
			 * it'll use the one the player is connected to.
			 */
			ByteArrayDataOutput out = ByteStreams.newDataOutput();
			out.writeUTF("Connect");
			out.writeUTF(target);

			player.sendPluginMessage(mrs, "BungeeCord", out.toByteArray());
		});

		mrs.getLogger().fine(() -> "Transferring players took " + (System.currentTimeMillis() - startTime) + " milliseconds on main thread");
	}

	public static void stashPut(Player player, @Nullable String name) throws Exception {
		savePlayer(player);

		DataEventListener.waitForPlayerToSaveThenAsync(player, () -> {
			List<RedisFuture<?>> futures = new ArrayList<>();

			RedisAPI api = MonumentaRedisSync.redisApi();

			String saveName = name;
			if (saveName == null) {
				saveName = player.getUniqueId().toString();
			} else {
				futures.add(api.async().sadd(getStashListPath(), saveName));
			}

			try {
				/* Read the most-recent player data save, and copy it to the stash */
				RedisFuture<byte[]> dataFuture = api.asyncStringBytes().lindex(getRedisDataPath(player), 0);
				RedisFuture<String> advanceFuture = api.async().lindex(getRedisAdvancementsPath(player), 0);
				RedisFuture<String> scoreFuture = api.async().lindex(getRedisScoresPath(player), 0);
				RedisFuture<String> pluginFuture = api.async().lindex(getRedisPluginDataPath(player), 0);
				RedisFuture<String> historyFuture = api.async().lindex(getRedisHistoryPath(player), 0);

				futures.add(api.asyncStringBytes().hset(getStashPath(), saveName + "-data", dataFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName + "-scores", scoreFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName + "-advancements", advanceFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName + "-plugins", pluginFuture.get()));
				futures.add(api.async().hset(getStashPath(), saveName + "-history", historyFuture.get()));

				if (!LettuceFutures.awaitAll(TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[0]))) {
					MonumentaRedisSync.getInstance().getLogger().severe("Got timeout waiting to commit stash data for player '" + player.getName() + "'");
					player.sendMessage(Component.text("Got timeout trying to commit stash data", NamedTextColor.RED));
					return;
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().log(Level.SEVERE, "Got exception while committing stash data for player '" + player.getName() + "'", ex);
				player.sendMessage(Component.text("Failed to save stash data: " + ex.getMessage(), NamedTextColor.RED));
				return;
			}

			player.sendMessage(Component.text("Data, scores, advancements saved to stash successfully", NamedTextColor.GOLD));
		});
	}

	public static void stashGet(Player player, @Nullable String name) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		/*
		 * Save player in case this was a mistake so they can get back
		 * This also saves per-shard data like location
		 */
		savePlayer(player);

		/* Lock player during stash get */
		DataEventListener.setPlayerAsTransferring(player);

		/* Wait for save to complete */
		DataEventListener.waitForPlayerToSaveThenAsync(player, () -> {
			List<RedisFuture<?>> futures = new ArrayList<>();

			RedisAPI api = MonumentaRedisSync.redisApi();

			String saveName = name;
			if (saveName == null) {
				saveName = player.getUniqueId().toString();
			}

			try {
				/* Read from the stash, and push it to the player's data */

				RedisFuture<byte[]> dataFuture = api.asyncStringBytes().hget(getStashPath(), saveName + "-data");
				RedisFuture<String> advanceFuture = api.async().hget(getStashPath(), saveName + "-advancements");
				RedisFuture<String> scoreFuture = api.async().hget(getStashPath(), saveName + "-scores");
				RedisFuture<String> pluginFuture = api.async().hget(getStashPath(), saveName + "-plugins");
				RedisFuture<String> historyFuture = api.async().hget(getStashPath(), saveName + "-history");

				/* Make sure there's actually data */
				if (dataFuture.get() == null || advanceFuture.get() == null || scoreFuture.get() == null || pluginFuture.get() == null || historyFuture.get() == null) {
					if (name == null) {
						player.sendMessage(Component.text("You don't have any stash data", NamedTextColor.RED));
					} else {
						player.sendMessage(Component.text("No stash data found for '" + name + "'", NamedTextColor.RED));
					}
					return;
				}

				futures.add(api.asyncStringBytes().lpush(getRedisDataPath(player), dataFuture.get()));
				futures.add(api.async().lpush(getRedisAdvancementsPath(player), advanceFuture.get()));
				futures.add(api.async().lpush(getRedisScoresPath(player), scoreFuture.get()));
				futures.add(api.async().lpush(getRedisPluginDataPath(player), pluginFuture.get()));
				futures.add(api.async().lpush(getRedisHistoryPath(player), "stash@" + historyFuture.get()));

				if (!LettuceFutures.awaitAll(TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[0]))) {
					MonumentaRedisSync.getInstance().getLogger().severe("Got timeout loading stash data for player '" + player.getName() + "'");
					player.sendMessage(Component.text("Got timeout loading stash data", NamedTextColor.RED));
					return;
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().log(Level.SEVERE, "Got exception while loading stash data for player '" + player.getName() + "'", ex);
				player.sendMessage(Component.text("Failed to load stash data: " + ex.getMessage(), NamedTextColor.RED));
				return;
			}

			/* Kick the player on the main thread to force rejoin */
			Bukkit.getServer().getScheduler().runTask(mrs, () -> player.kick(Component.text("Stash data loaded successfully")));
		});
	}

	public static void stashInfo(Player player, @Nullable String name) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		RedisAPI api = MonumentaRedisSync.redisApi();

		String saveName;
		if (name != null) {
			saveName = name;
		} else {
			saveName = player.getUniqueId().toString();
		}

		Bukkit.getScheduler().runTaskAsynchronously(mrs, () -> {
			String history = api.async().hget(getStashPath(), saveName + "-history").toCompletableFuture().join();
			Bukkit.getScheduler().runTask(mrs, () -> {
				if (history == null) {
					if (name == null) {
						player.sendMessage(Component.text("You don't have any stash data", NamedTextColor.RED));
					} else {
						player.sendMessage(Component.text("No stash data found for '" + name + "'", NamedTextColor.RED));
					}
					return;
				}

				String[] split = history.split("\\|");
				if (split.length != 3) {
					player.sendMessage(Component.text("Got corrupted history with " + split.length + " entries: " + history, NamedTextColor.RED));
					return;
				}

				if (name == null) {
					player.sendMessage(Component.text("Stash last saved on " + split[0] + " " + getTimeDifferenceSince(Long.parseLong(split[1])) + " ago", NamedTextColor.GOLD));
				} else {
					player.sendMessage(Component.text("Stash '" + name + "' last saved on " + split[0] + " by " + split[2] + " " + getTimeDifferenceSince(Long.parseLong(split[1])) + " ago", NamedTextColor.GOLD));
				}
			});
		});
	}

	public static void playerRollback(Player moderator, Player player, int index) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		/*
		 * Save player in case this was a mistake so they can get back
		 * This also saves per-shard data like location
		 */
		savePlayer(player);

		/* Now that data has saved, the index we want to roll back to is +1 older */
		final int rollbackIndex = index + 1;

		/* Lock player during rollback */
		DataEventListener.setPlayerAsTransferring(player);

		/* Wait for save to complete */
		DataEventListener.waitForPlayerToSaveThenAsync(player, () -> {
			List<RedisFuture<?>> futures = new ArrayList<>();

			RedisAPI api = MonumentaRedisSync.redisApi();

			try {
				/* Read the history element and push it to the player's data */

				RedisFuture<byte[]> dataFuture = api.asyncStringBytes().lindex(getRedisDataPath(player), rollbackIndex);
				RedisFuture<String> advanceFuture = api.async().lindex(getRedisAdvancementsPath(player), rollbackIndex);
				RedisFuture<String> scoreFuture = api.async().lindex(getRedisScoresPath(player), rollbackIndex);
				RedisFuture<String> pluginFuture = api.async().lindex(getRedisPluginDataPath(player), rollbackIndex);
				RedisFuture<String> historyFuture = api.async().lindex(getRedisHistoryPath(player), rollbackIndex);

				/* Make sure there's actually data */
				if (dataFuture.get() == null || advanceFuture.get() == null || scoreFuture.get() == null || pluginFuture.get() == null || historyFuture.get() == null) {
					moderator.sendMessage(Component.text("Failed to retrieve player's rollback data", NamedTextColor.RED));
					return;
				}

				futures.add(api.asyncStringBytes().lpush(getRedisDataPath(player), dataFuture.get()));
				futures.add(api.async().lpush(getRedisAdvancementsPath(player), advanceFuture.get()));
				futures.add(api.async().lpush(getRedisScoresPath(player), scoreFuture.get()));
				futures.add(api.async().lpush(getRedisPluginDataPath(player), pluginFuture.get()));
				futures.add(api.async().lpush(getRedisHistoryPath(player), "rollback@" + historyFuture.get()));

				if (!LettuceFutures.awaitAll(TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[0]))) {
					MonumentaRedisSync.getInstance().getLogger().severe("Got timeout loading rollback data for player '" + player.getName() + "'");
					moderator.sendMessage(Component.text("Got timeout loading rollback data", NamedTextColor.RED));
					return;
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().log(Level.SEVERE, "Got exception while loading rollback data for player '" + player.getName() + "'", ex);
				moderator.sendMessage(Component.text("Failed to load rollback data: " + ex.getMessage(), NamedTextColor.RED));
				return;
			}

			moderator.sendMessage(Component.text("Player " + player.getName() + " rolled back successfully", NamedTextColor.GREEN));

			/* Kick the player on the main thread to force rejoin */
			Bukkit.getServer().getScheduler().runTask(mrs, () -> player.kick(Component.text("Your player data has been rolled back, and you can now re-join the server")));
		});
	}

	public static void playerLoadFromPlayer(Player loadTo, Player loadFrom, int index) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		/*
		 * Save player in case this was a mistake so they can get back
		 * This also saves per-shard data like location
		 */
		savePlayer(loadTo);

		/* Lock player during load */
		DataEventListener.setPlayerAsTransferring(loadTo);

		/* Wait for save to complete */
		DataEventListener.waitForPlayerToSaveThenAsync(loadTo, () -> {
			List<RedisFuture<?>> futures = new ArrayList<>();

			RedisAPI api = MonumentaRedisSync.redisApi();

			try {
				/* Read the history element and push it to the player's data */

				RedisFuture<byte[]> dataFuture = api.asyncStringBytes().lindex(getRedisDataPath(loadFrom), index);
				RedisFuture<String> advanceFuture = api.async().lindex(getRedisAdvancementsPath(loadFrom), index);
				RedisFuture<String> scoreFuture = api.async().lindex(getRedisScoresPath(loadFrom), index);
				RedisFuture<String> pluginFuture = api.async().lindex(getRedisPluginDataPath(loadFrom), index);
				RedisFuture<String> historyFuture = api.async().lindex(getRedisHistoryPath(loadFrom), index);

				/* Make sure there's actually data */
				if (dataFuture.get() == null || advanceFuture.get() == null || scoreFuture.get() == null || pluginFuture.get() == null || historyFuture.get() == null) {
					loadTo.sendMessage(Component.text("Failed to retrieve player's data to load", NamedTextColor.RED));
					return;
				}

				futures.add(api.asyncStringBytes().lpush(getRedisDataPath(loadTo), dataFuture.get()));
				futures.add(api.async().lpush(getRedisAdvancementsPath(loadTo), advanceFuture.get()));
				futures.add(api.async().lpush(getRedisScoresPath(loadTo), scoreFuture.get()));
				futures.add(api.async().lpush(getRedisPluginDataPath(loadTo), pluginFuture.get()));
				futures.add(api.async().lpush(getRedisHistoryPath(loadTo), "loadfrom@" + loadFrom.getName() + "@" + historyFuture.get()));

				if (!LettuceFutures.awaitAll(TIMEOUT_SECONDS, TimeUnit.SECONDS, futures.toArray(new RedisFuture[0]))) {
					MonumentaRedisSync.getInstance().getLogger().severe("Got timeout loading data for player '" + loadFrom.getName() + "'");
					loadTo.sendMessage(Component.text("Got timeout loading data", NamedTextColor.RED));
					return;
				}
			} catch (InterruptedException | ExecutionException ex) {
				MonumentaRedisSync.getInstance().getLogger().log(Level.SEVERE, "Got exception while loading data for player '" + loadFrom.getName() + "'", ex);
				loadTo.sendMessage(Component.text("Failed to load data: " + ex.getMessage(), NamedTextColor.RED));
				return;
			}

			/* Kick the player on the main thread to force rejoin */
			Bukkit.getServer().getScheduler().runTask(mrs, () -> loadTo.kick(Component.text("Data loaded from player " + loadFrom.getName() + " at index " + index + " and you can now re-join the server")));
		});
	}

	public static String getRedisDataPath(Player player) {
		return getRedisDataPath(player.getUniqueId());
	}

	public static String getRedisDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:data", MonumentaRedisSync.config().getServerDomain(), uuid.toString());
	}

	public static String getRedisHistoryPath(Player player) {
		return getRedisHistoryPath(player.getUniqueId());
	}

	public static String getRedisHistoryPath(UUID uuid) {
		return String.format("%s:playerdata:%s:history", MonumentaRedisSync.config().getServerDomain(), uuid.toString());
	}

	public static String getRedisPerShardDataPath(Player player) {
		return getRedisPerShardDataPath(player.getUniqueId());
	}

	public static String getRedisPerShardDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:sharddata", MonumentaRedisSync.config().getServerDomain(), uuid.toString());
	}

	public static String getRedisPerShardDataWorldKey(World world) {
		return getRedisPerShardDataWorldKey(world.getUID(), world.getName());
	}

	public static String getRedisPerShardDataWorldKey(UUID worldUUID, String worldName) {
		return worldUUID.toString() + ":" + worldName;
	}


	public static String getRedisPluginDataPath(Player player) {
		return getRedisPluginDataPath(player.getUniqueId());
	}

	public static String getRedisPluginDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:plugins", MonumentaRedisSync.config().getServerDomain(), uuid.toString());
	}

	public static String getRedisAdvancementsPath(Player player) {
		return getRedisAdvancementsPath(player.getUniqueId());
	}

	public static String getRedisAdvancementsPath(UUID uuid) {
		return String.format("%s:playerdata:%s:advancements", MonumentaRedisSync.config().getServerDomain(), uuid.toString());
	}

	public static String getRedisScoresPath(Player player) {
		return getRedisScoresPath(player.getUniqueId());
	}

	public static String getRedisScoresPath(UUID uuid) {
		return String.format("%s:playerdata:%s:scores", MonumentaRedisSync.config().getServerDomain(), uuid.toString());
	}

	public static String getStashPath() {
		return String.format("%s:stash", MonumentaRedisSync.config().getServerDomain());
	}

	public static String getStashListPath() {
		return String.format("%s:stashlist", MonumentaRedisSync.config().getServerDomain());
	}

	public static String getTimeDifferenceSince(long compareTime) {
		final long diff = System.currentTimeMillis() - compareTime;
		final long diffSeconds = diff / 1000 % 60;
		final long diffMinutes = diff / (60 * 1000) % 60;
		final long diffHours = diff / (60 * 60 * 1000) % 24;
		final long diffDays = diff / (24 * 60 * 60 * 1000);

		String timeStr = "";
		if (diffDays > 0) {
			timeStr += diffDays + " day";
			if (diffDays > 1) {
				timeStr += "s";
			}
		}

		if (diffDays > 0 && (diffHours > 0 || diffMinutes > 0 || diffSeconds > 0)) {
			timeStr += " ";
		}

		if (diffHours > 0) {
			timeStr += diffHours + " hour";
			if (diffHours > 1) {
				timeStr += "s";
			}
		}

		if (diffHours > 0 && (diffMinutes > 0 || diffSeconds > 0)) {
			timeStr += " ";
		}

		if (diffMinutes > 0) {
			timeStr += diffMinutes + " minute";
			if (diffMinutes > 1) {
				timeStr += "s";
			}
		}

		if (diffMinutes > 0 && diffSeconds > 0 && (diffDays == 0 && diffHours == 0)) {
			timeStr += " ";
		}

		if (diffSeconds > 0 && (diffDays == 0 && diffHours == 0)) {
			timeStr += diffSeconds + " second";
			if (diffSeconds > 1) {
				timeStr += "s";
			}
		}

		return timeStr;
	}

	/**
	 * Saves all of player's data, including advancements, scores, plugin data, inventory, world location, etc.
	 * <p>
	 * Also creates a rollback point like all full saves.
	 * <p>
	 * Takes several milliseconds so care should be taken not to call this too frequently
	 */
	public static void savePlayer(Player player) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		try {
			mrs.getVersionAdapter().savePlayer(player);
		} catch (Exception ex) {
			String message = "Failed to save player data for player '" + player.getName() + "'";
			mrs.getLogger().severe(message);
			throw new Exception(message, ex);
		}
	}

	/**
	 * Gets player plugin data from the cache.
	 * <p>
	 * Only valid if the player is currently on this shard.
	 *
	 * @param uuid              Player's UUID to get data for
	 * @param pluginIdentifier  A unique string key identifying which plugin data to get for this player
	 *
	 * @return plugin data for this identifier (or null if it doesn't exist or player isn't online)
	 */
	public static @Nullable JsonObject getPlayerPluginData(UUID uuid, String pluginIdentifier) {
		JsonObject pluginData = DataEventListener.getPlayerPluginData(uuid);
		if (pluginData == null || !pluginData.has(pluginIdentifier)) {
			return null;
		}

		JsonElement pluginDataElement = pluginData.get(pluginIdentifier);
		if (!pluginDataElement.isJsonObject()) {
			return null;
		}

		return pluginDataElement.getAsJsonObject();
	}

	/**
	 * Gets a map of all player scoreboard values.
	 * <p>
	 * If player is online, will pull them from the current scoreboard. This work will be done on the main thread (will take several milliseconds).
	 * If player is offline, will pull them from the most recent redis save on an async thread, then compose them into a map (basically no main thread time)
	 * <p>
	 * The return future will always complete on the main thread with either results or an exception.
	 * Suggest chaining on .whenComplete((data, ex) -> your code) to do something with this data when complete
	 */
	public static CompletableFuture<Map<String, Integer>> getPlayerScores(UUID uuid) {
		CompletableFuture<Map<String, Integer>> future = new CompletableFuture<>();

		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		Player player = Bukkit.getPlayer(uuid);
		if (player != null) {
			Map<String, Integer> scores = new HashMap<>();
			for (Objective objective : Bukkit.getScoreboardManager().getMainScoreboard().getObjectives()) {
				Score score = objective.getScore(player.getName());
				scores.put(objective.getName(), score.getScore());
			}
			future.complete(scores);
			return future;
		}

		RedisAsyncCommands<String, String> commands = MonumentaRedisSync.redisApi().async();

		commands.lindex(getRedisScoresPath(uuid), 0)
			.thenApply(
				(scoreData) -> new Gson().fromJson(scoreData, JsonObject.class).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().getAsInt())))
			.whenComplete((scoreMap, ex) -> Bukkit.getScheduler().runTask(mrs, () -> {
				if (ex != null) {
					future.completeExceptionally(ex);
				} else {
					future.complete(scoreMap);
				}
			}));

		return future;
	}

	private static Boolean transformPlayerSaveResult(MonumentaRedisSync mrs, TransactionResult result) {
		if (result.isEmpty() || result.size() != 5 || result.get(0) == null
			|| result.get(1) == null || result.get(2) == null || result.get(3) == null || result.get(4) == null) {
			mrs.getLogger().severe("Failed to commit player data");
			return false;
		}

		return true;
	}

	/**
	 * If MonumentaNetworkRelay is installed, returns a list of all other shard names
	 * that are currently up and valid transfer targets from this server.
	 * <p>
	 * If MonumentaNetworkRelay is not installed, returns an empty array.
	 */
	public static String[] getOnlineTransferTargets() {
		return NetworkRelayIntegration.getOnlineTransferTargets();
	}

	/**
	 * Runs the result of an asynchronous transaction on the main thread after it is completed
	 * <p>
	 * Will always call the callback function eventually, even if the resulting transaction fails or is lost.
	 * <p>
	 * When the function is called, either data will be non-null and exception null,
	 * or data will be null and the exception will be non-null
	 */
	public static <T> void runOnMainThreadWhenComplete(Plugin plugin, CompletableFuture<T> future, BiConsumer<T, Throwable> func) {
		future.whenComplete((T result, Throwable ex) -> Bukkit.getScheduler().runTask(plugin, () -> func.accept(result, ex)));
	}
}
