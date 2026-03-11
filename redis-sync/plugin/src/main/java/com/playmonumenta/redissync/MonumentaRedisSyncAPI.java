package com.playmonumenta.redissync;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.adapters.VersionAdapter.SaveData;
import com.playmonumenta.redissync.event.PlayerServerTransferEvent;
import com.playmonumenta.redissync.utils.Trie;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.wrappers.Rotation;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisFuture;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.BiConsumer;
import java.util.logging.Level;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Score;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public class MonumentaRedisSyncAPI {
	public static class RedisPlayerData {
		private final UUID mUUID;
		private Object mNbtTagCompoundData;
		private String mAdvancements;
		private String mScores;
		private String mPluginData;
		private String mHistory;

		public RedisPlayerData(UUID uuid, Object nbtTagCompoundData, String advancements,
		                       String scores, String pluginData, String history) {
			mUUID = uuid;
			mNbtTagCompoundData = nbtTagCompoundData;
			mAdvancements = advancements;
			mScores = scores;
			mPluginData = pluginData;
			mHistory = history;
		}

		public UUID getUniqueId() {
			return mUUID;
		}

		public Object getNbtTagCompoundData() {
			return mNbtTagCompoundData;
		}

		public String getAdvancements() {
			return mAdvancements;
		}

		public String getScores() {
			return mScores;
		}

		public String getPluginData() {
			return mPluginData;
		}

		public String getHistory() {
			return mHistory;
		}

		public UUID getmUUID() {
			return mUUID;
		}

		public void setNbtTagCompoundData(Object nbtTagCompoundData) {
			this.mNbtTagCompoundData = nbtTagCompoundData;
		}

		public void setAdvancements(String advancements) {
			this.mAdvancements = advancements;
		}

		public void setScores(String scores) {
			this.mScores = scores;
		}

		public void setPluginData(String pluginData) {
			this.mPluginData = pluginData;
		}

		public void setHistory(String history) {
			this.mHistory = history;
		}
	}

	public static final int TIMEOUT_SECONDS = 10;
	public static final ArgumentSuggestions<CommandSender> SUGGESTIONS_ALL_CACHED_PLAYER_NAMES = ArgumentSuggestions.strings((info) ->
		getAllCachedPlayerNames().toArray(String[]::new));

	private static final Trie<UUID> mNameToUuidTrie = new Trie<>();
	private static final Map<String, UUID> mNameLowercaseToUuid = new ConcurrentHashMap<>();
	private static final Map<UUID, String> mUuidToName = new ConcurrentHashMap<>();

	protected static void updateUuidToName(UUID uuid, String name) {
		mUuidToName.put(uuid, name);
	}

	protected static void updateNameToUuid(String name, UUID uuid) {
		mNameLowercaseToUuid.put(name.toLowerCase(Locale.ROOT), uuid);
		mNameToUuidTrie.put(name, uuid);
	}

	public static CompletableFuture<String> uuidToName(UUID uuid) {
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			return conn.hget("uuid2name", uuid.toString()).toCompletableFuture();
		}
	}

	public static CompletableFuture<UUID> nameToUUID(String name) {
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			return conn.hget("name2uuid", name).thenApply((uuid) -> (uuid == null || uuid.isEmpty()) ? null : UUID.fromString(uuid)).toCompletableFuture();
		}
	}

	public static CompletableFuture<Set<String>> getAllPlayerNames() {
		RedisFuture<Map<String, String>> future;
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			future = conn.hgetall("name2uuid");
		}
		return future.thenApply(Map::keySet).toCompletableFuture();
	}

	public static CompletableFuture<Set<UUID>> getAllPlayerUUIDs() {
		RedisFuture<Map<String, String>> future;
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			future = conn.hgetall("uuid2name");
		}
		return future.thenApply((data) -> data.keySet().stream().map(UUID::fromString).collect(Collectors.toSet())).toCompletableFuture();
	}

	// Thread-safe: backed by ConcurrentHashMap, callable from any thread
	public static @Nullable String cachedUuidToName(UUID uuid) {
		return mUuidToName.get(uuid);
	}

	// Thread-safe: backed by ConcurrentHashMap, callable from any thread
	public static @Nullable UUID cachedNameToUuid(String name) {
		return mNameLowercaseToUuid.get(name.toLowerCase(Locale.ROOT));
	}

	public static Set<String> getAllCachedPlayerNames() {
		return new ConcurrentSkipListSet<>(mUuidToName.values());
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

	public static boolean isPlayerTransferring(Player player) {
		return DataEventListener.isPlayerTransferring(player);
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

		if (target.equalsIgnoreCase(CommonConfig.getShardName())) {
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
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();
		savePlayer(player);

		DataEventListener.waitForPlayerToSaveThenSync(player, () -> {
			final String saveName = name != null ? name : player.getUniqueId().toString();

			if (name != null) {
				try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
					conn.sadd(getStashListPath(), saveName).exceptionally(ex -> {
						mrs.getLogger().severe("Redis sadd failed in stashPut: " + ex.getMessage());
						return null;
					});
				}
			}

			/* Read all five fields from the player's current save atomically as bytes */
			RedisAPI.multiStringBytes(conn -> {
				conn.lindex(getRedisDataPath(player), 0);
				conn.lindex(getRedisAdvancementsPath(player), 0);
				conn.lindex(getRedisScoresPath(player), 0);
				conn.lindex(getRedisPluginDataPath(player), 0);
				conn.lindex(getRedisHistoryPath(player), 0);
			}).whenComplete((readResult, readEx) -> {
				if (readEx != null) {
					mrs.getLogger().log(Level.SEVERE, "Got exception while reading data for stash put for player '" + player.getName() + "'", readEx);
					player.sendMessage(Component.text("Failed to save stash data: " + readEx.getMessage(), NamedTextColor.RED));
					return;
				}

				byte[] data = (byte[]) readResult.get(0);
				byte[] advance = (byte[]) readResult.get(1);
				byte[] score = (byte[]) readResult.get(2);
				byte[] plugin = (byte[]) readResult.get(3);
				byte[] history = (byte[]) readResult.get(4);

				if (data == null || advance == null || score == null || plugin == null || history == null) {
					mrs.getLogger().severe("Failed to retrieve player's data to stash for player '" + player.getName() + "'");
					player.sendMessage(Component.text("Failed to save stash data: player data missing", NamedTextColor.RED));
					return;
				}

				/* Write all five fields to the stash atomically as bytes */
				RedisAPI.multiStringBytes(conn -> {
					conn.hset(getStashPath(), saveName + "-data", data);
					conn.hset(getStashPath(), saveName + "-advancements", advance);
					conn.hset(getStashPath(), saveName + "-scores", score);
					conn.hset(getStashPath(), saveName + "-plugins", plugin);
					conn.hset(getStashPath(), saveName + "-history", history);
				}).whenComplete((writeResult, writeEx) -> {
					if (writeEx != null) {
						mrs.getLogger().log(Level.SEVERE, "Got exception while committing stash data for player '" + player.getName() + "'", writeEx);
						player.sendMessage(Component.text("Failed to save stash data: " + writeEx.getMessage(), NamedTextColor.RED));
						return;
					}
					player.sendMessage(Component.text("Data, scores, advancements saved to stash successfully", NamedTextColor.GOLD));
				});
			});
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
		DataEventListener.waitForPlayerToSaveThenSync(player, () -> {
			final String saveName = name != null ? name : player.getUniqueId().toString();

			/* Read all five fields from the stash atomically as bytes */
			RedisAPI.multiStringBytes(conn -> {
				conn.hget(getStashPath(), saveName + "-data");
				conn.hget(getStashPath(), saveName + "-advancements");
				conn.hget(getStashPath(), saveName + "-scores");
				conn.hget(getStashPath(), saveName + "-plugins");
				conn.hget(getStashPath(), saveName + "-history");
			}).whenComplete((readResult, readEx) -> {
				if (readEx != null) {
					mrs.getLogger().log(Level.SEVERE, "Got exception while loading stash data for player '" + player.getName() + "'", readEx);
					player.sendMessage(Component.text("Failed to load stash data: " + readEx.getMessage(), NamedTextColor.RED));
					return;
				}

				byte[] data = (byte[]) readResult.get(0);
				byte[] advance = (byte[]) readResult.get(1);
				byte[] score = (byte[]) readResult.get(2);
				byte[] plugin = (byte[]) readResult.get(3);
				byte[] historyRaw = (byte[]) readResult.get(4);

				/* Make sure there's actually data */
				if (data == null || advance == null || score == null || plugin == null || historyRaw == null) {
					if (name == null) {
						player.sendMessage(Component.text("You don't have any stash data", NamedTextColor.RED));
					} else {
						player.sendMessage(Component.text("No stash data found for '" + name + "'", NamedTextColor.RED));
					}
					return;
				}

				byte[] historyOut = ("stash@" + new String(historyRaw, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);

				/* Write all five fields to the player's data atomically as bytes */
				RedisAPI.multiStringBytes(conn -> {
					conn.lpush(getRedisDataPath(player), data);
					conn.lpush(getRedisAdvancementsPath(player), advance);
					conn.lpush(getRedisScoresPath(player), score);
					conn.lpush(getRedisPluginDataPath(player), plugin);
					conn.lpush(getRedisHistoryPath(player), historyOut);
				}).whenComplete((writeResult, writeEx) -> {
					if (writeEx != null) {
						mrs.getLogger().log(Level.SEVERE, "Got exception while writing stash data for player '" + player.getName() + "'", writeEx);
						player.sendMessage(Component.text("Failed to load stash data: " + writeEx.getMessage(), NamedTextColor.RED));
						return;
					}
					/* Kick the player on the main thread to force rejoin */
					Bukkit.getServer().getScheduler().runTask(mrs, () -> player.kick(Component.text("Stash data loaded successfully")));
				});
			});
		});
	}

	public static void stashInfo(Player player, @Nullable String name) {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		String saveName = name != null ? name : player.getUniqueId().toString();

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.hget(getStashPath(), saveName + "-history").whenComplete((history, ex) -> {
				Bukkit.getScheduler().runTask(mrs, () -> {
					if (ex != null) {
						mrs.getLogger().severe("Redis hget failed in stashInfo: " + ex.getMessage());
						player.sendMessage(Component.text("Error fetching stash info: " + ex.getMessage(), NamedTextColor.RED));
						ex.printStackTrace();
						return;
					}
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
	}

	public static void stashList(Player player, @Nullable String searchName) {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		CompletableFuture<List<String>> hkeysFuture;
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			hkeysFuture = conn.hkeys(getStashPath()).toCompletableFuture();
		}
		hkeysFuture.whenComplete((keyResults, ex) -> {
			Bukkit.getScheduler().runTask(mrs, () -> {
				if (ex != null) {
					player.sendMessage(Component.text("Error fetching stash list: " + ex.getMessage(), NamedTextColor.RED));
					return;
				}
				List<String> stashNames = keyResults
					.stream()
					.filter(field -> field.endsWith("-history"))
					.map(field ->
						field.substring(0, field.length() - 8)
					)
					.sorted()
					.toList();

				if (stashNames.isEmpty()) {
					player.sendMessage(Component.text("No stashes were found"));
					return;
				}

				if (searchName != null) {
					player.sendMessage(Component.text("Listing stashes saved by username " + searchName, NamedTextColor.GOLD));
					player.sendMessage(Component.text("If a stash you saved does not appear here, it may have been overwritten by another user, or you may have saved it under a past username", NamedTextColor.GOLD));

					try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
						conn.hmget(getStashPath(), stashNames.stream().map((stash) -> stash + "-history").toArray(String[]::new)).whenComplete((results, e) -> {
							Bukkit.getScheduler().runTask(mrs, () -> {
								if (e != null) {
									mrs.getLogger().severe("Redis hmget failed in stashList: " + ex.getMessage());
									player.sendMessage(Component.text("Error fetching stash list entries: " + e.getMessage(), NamedTextColor.RED));
									ex.printStackTrace();
									return;
								}

								for (KeyValue<String, String> historyEntry : results) {
									String stash = historyEntry.getKey();
									if (stash.endsWith("-history")) { // Should always be true...
										stash = stash.substring(0, stash.length() - "-history".length());
									}
									String history = historyEntry.getValue();

									if (history == null) {
										player.sendMessage(Component.text("Failed to get data for an existing stash? name: " + stash, NamedTextColor.RED));
										continue;
									}

									String[] split = history.split("\\|");
									if (split.length != 3) {
										player.sendMessage(Component.text("Stash is seemingly corrupted, with " + split.length + " entries: " + history, NamedTextColor.RED));
										continue;
									}

									if (searchName.equals(split[2])) {
										player.sendMessage(Component.text("Stash '" + stash + "' last saved on " + split[0] + " by " + split[2] + " " + getTimeDifferenceSince(Long.parseLong(split[1])) + " ago", NamedTextColor.WHITE));
									}
								}
							});
						});
					}
				} else {
					List<Component> formattedStashNames = stashNames
						.stream()
						.map(stashName -> {
							UUID uuid;
							try {
								uuid = UUID.fromString(stashName);
							} catch (IllegalArgumentException ignored) {
								uuid = null;
							}
							Component result = Component.text(stashName).clickEvent(ClickEvent.copyToClipboard(stashName));
							if (uuid != null && mUuidToName.containsKey(uuid)) {
								result = result.hoverEvent(Component.text("This UUID belongs to " + mUuidToName.get(uuid)));
							} else {
								result = result.hoverEvent(Component.empty());
							}
							return result;
						})
						.toList();
					player.sendMessage(Component.text(formattedStashNames.size() + " stashes found (click a stash name to copy): ", NamedTextColor.GOLD));
					Component merge = Component.empty().append(formattedStashNames.getFirst()); // appending to empty component fixes hover text weirdness
					for (int i = 1; i < formattedStashNames.size(); i++) {
						merge = merge.append(Component.text(", ")).append(formattedStashNames.get(i));
					}
					player.sendMessage(merge);
					player.sendMessage(Component.text("Use `/stash list user` to list only your stashes, or `/stash list user [username]` for stashes saved by a certain player. " +
						"Note that this searches by username only, so old usernames must be searched separately", NamedTextColor.GOLD));
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
		DataEventListener.waitForPlayerToSaveThenSync(player, () -> {
			/* Read all five fields atomically as bytes */
			RedisAPI.multiStringBytes(conn -> {
				conn.lindex(getRedisDataPath(player), rollbackIndex);
				conn.lindex(getRedisAdvancementsPath(player), rollbackIndex);
				conn.lindex(getRedisScoresPath(player), rollbackIndex);
				conn.lindex(getRedisPluginDataPath(player), rollbackIndex);
				conn.lindex(getRedisHistoryPath(player), rollbackIndex);
			}).whenComplete((readResult, readEx) -> {
				if (readEx != null) {
					mrs.getLogger().log(Level.SEVERE, "Got exception while reading rollback data for player '" + player.getName() + "'", readEx);
					moderator.sendMessage(Component.text("Failed to load rollback data: " + readEx.getMessage(), NamedTextColor.RED));
					return;
				}

				byte[] data = (byte[]) readResult.get(0);
				byte[] advance = (byte[]) readResult.get(1);
				byte[] score = (byte[]) readResult.get(2);
				byte[] plugin = (byte[]) readResult.get(3);
				byte[] historyRaw = (byte[]) readResult.get(4);

				/* Make sure there's actually data */
				if (data == null || advance == null || score == null || plugin == null || historyRaw == null) {
					moderator.sendMessage(Component.text("Failed to retrieve player's rollback data", NamedTextColor.RED));
					return;
				}

				byte[] historyOut = ("rollback@" + new String(historyRaw, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);

				/* Write all five fields atomically as bytes */
				RedisAPI.multiStringBytes(conn -> {
					conn.lpush(getRedisDataPath(player), data);
					conn.lpush(getRedisAdvancementsPath(player), advance);
					conn.lpush(getRedisScoresPath(player), score);
					conn.lpush(getRedisPluginDataPath(player), plugin);
					conn.lpush(getRedisHistoryPath(player), historyOut);
				}).whenComplete((writeResult, writeEx) -> {
					if (writeEx != null) {
						mrs.getLogger().log(Level.SEVERE, "Got exception while writing rollback data for player '" + player.getName() + "'", writeEx);
						moderator.sendMessage(Component.text("Failed to load rollback data: " + writeEx.getMessage(), NamedTextColor.RED));
						return;
					}
					moderator.sendMessage(Component.text("Player " + player.getName() + " rolled back successfully", NamedTextColor.GREEN));
					/* Kick the player on the main thread to force rejoin */
					Bukkit.getServer().getScheduler().runTask(mrs, () -> player.kick(Component.text("Your player data has been rolled back, and you can now re-join the server")));
				});
			});
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
		DataEventListener.waitForPlayerToSaveThenSync(loadTo, () -> {
			/* Read all five fields atomically as bytes */
			RedisAPI.multiStringBytes(conn -> {
				conn.lindex(getRedisDataPath(loadFrom), index);
				conn.lindex(getRedisAdvancementsPath(loadFrom), index);
				conn.lindex(getRedisScoresPath(loadFrom), index);
				conn.lindex(getRedisPluginDataPath(loadFrom), index);
				conn.lindex(getRedisHistoryPath(loadFrom), index);
			}).whenComplete((readResult, readEx) -> {
				if (readEx != null) {
					mrs.getLogger().log(Level.SEVERE, "Got exception while reading data for player '" + loadFrom.getName() + "'", readEx);
					loadTo.sendMessage(Component.text("Failed to load data: " + readEx.getMessage(), NamedTextColor.RED));
					return;
				}

				byte[] data = (byte[]) readResult.get(0);
				byte[] advance = (byte[]) readResult.get(1);
				byte[] score = (byte[]) readResult.get(2);
				byte[] plugin = (byte[]) readResult.get(3);
				byte[] historyRaw = (byte[]) readResult.get(4);

				if (data == null || advance == null || score == null || plugin == null || historyRaw == null) {
					loadTo.sendMessage(Component.text("Failed to retrieve player's data to load", NamedTextColor.RED));
					mrs.getLogger().severe("Failed to retrieve player's data to load for player '" + loadFrom.getName() + "'");
					return;
				}

				byte[] historyOut = ("loadfrom@" + loadFrom.getName() + "@" + new String(historyRaw, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);

				/* Write all five fields atomically as bytes */
				RedisAPI.multiStringBytes(conn -> {
					conn.lpush(getRedisDataPath(loadTo), data);
					conn.lpush(getRedisAdvancementsPath(loadTo), advance);
					conn.lpush(getRedisScoresPath(loadTo), score);
					conn.lpush(getRedisPluginDataPath(loadTo), plugin);
					conn.lpush(getRedisHistoryPath(loadTo), historyOut);
				}).whenComplete((writeResult, writeEx) -> {
					if (writeEx != null) {
						mrs.getLogger().log(Level.SEVERE, "Got exception while writing data for player '" + loadFrom.getName() + "'", writeEx);
						loadTo.sendMessage(Component.text("Failed to load data: " + writeEx.getMessage(), NamedTextColor.RED));
						return;
					}
					/* Kick the player on the main thread to force rejoin */
					Bukkit.getServer().getScheduler().runTask(mrs, () -> loadTo.kick(Component.text("Data loaded from player " + loadFrom.getName() + " at index " + index + " and you can now re-join the server")));
				});
			});
		});
	}

	public static String getRedisDataPath(Player player) {
		return getRedisDataPath(player.getUniqueId());
	}

	public static String getRedisDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:data", CommonConfig.getServerDomain(), uuid.toString());
	}

	public static String getRedisHistoryPath(Player player) {
		return getRedisHistoryPath(player.getUniqueId());
	}

	public static String getRedisHistoryPath(UUID uuid) {
		return String.format("%s:playerdata:%s:history", CommonConfig.getServerDomain(), uuid.toString());
	}

	public static String getRedisPerShardDataPath(Player player) {
		return getRedisPerShardDataPath(player.getUniqueId());
	}

	public static String getRedisPerShardDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:sharddata", CommonConfig.getServerDomain(), uuid.toString());
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
		return String.format("%s:playerdata:%s:plugins", CommonConfig.getServerDomain(), uuid.toString());
	}

	public static String getRedisAdvancementsPath(Player player) {
		return getRedisAdvancementsPath(player.getUniqueId());
	}

	public static String getRedisAdvancementsPath(UUID uuid) {
		return String.format("%s:playerdata:%s:advancements", CommonConfig.getServerDomain(), uuid.toString());
	}

	public static String getRedisScoresPath(Player player) {
		return getRedisScoresPath(player.getUniqueId());
	}

	public static String getRedisScoresPath(UUID uuid) {
		return String.format("%s:playerdata:%s:scores", CommonConfig.getServerDomain(), uuid.toString());
	}

	public static String getStashPath() {
		return String.format("%s:stash", CommonConfig.getServerDomain());
	}

	public static String getStashListPath() {
		return String.format("%s:stashlist", CommonConfig.getServerDomain());
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

	public static class PlayerWorldData {
		// Other sharddata fields that are not returned here: {"SpawnDimension":"minecraft:overworld","Dimension":0,"Paper.Origin":[-1450.0,241.0,-1498.0]}"}
		// Note: This list might be out of date

		private final Location mSpawnLoc; // {"SpawnX":-1450,"SpawnY":241,"SpawnZ":-1498,"SpawnAngle":0.0}
		private final Location mPlayerLoc; // {"Pos":[-1280.5,95.0,5369.7001953125],"Rotation":[-358.9,2.1]}
		private final Vector mMotion; // {"Motion":[0.0,-0.0784000015258789,0.0]}
		private final boolean mSpawnForced; // {"SpawnForced":true}
		private final boolean mFlying; // {"flying":false}
		private final boolean mFallFlying; // {"FallFlying":false}
		private final float mFallDistance; // {"FallDistance":0.0}
		private final boolean mOnGround; // {"OnGround":true}

		private PlayerWorldData(Location spawnLoc, Location playerLoc, Vector motion, boolean spawnForced, boolean flying, boolean fallFlying, float fallDistance, boolean onGround) {
			mSpawnLoc = spawnLoc;
			mPlayerLoc = playerLoc;
			mMotion = motion;
			mSpawnForced = spawnForced;
			mFlying = flying;
			mFallFlying = fallFlying;
			mFallDistance = fallDistance;
			mOnGround = onGround;
		}

		public Location getSpawnLoc() {
			return mSpawnLoc;
		}

		public Location getPlayerLoc() {
			return mPlayerLoc;
		}

		public Vector getMotion() {
			return mMotion;
		}

		public boolean getFallFlying() {
			return mFallFlying;
		}

		public double getFallDistance() {
			return mFallDistance;
		}

		public boolean getOnGround() {
			return mOnGround;
		}

		public void applyToPlayer(Player player) {
			player.teleport(mPlayerLoc);
			player.setVelocity(mMotion);
			player.setFlying(mFlying && player.getAllowFlight());
			player.setGliding(mFallFlying);
			player.setFallDistance(mFallDistance);
			player.setBedSpawnLocation(mSpawnLoc, mSpawnForced);
		}

		private static PlayerWorldData fromJson(@Nullable String jsonStr, World world) {
			// Defaults to world spawn
			Location spawnLoc = world.getSpawnLocation();
			Location playerLoc = spawnLoc.clone();
			Vector motion = new Vector(0, 0, 0);
			boolean spawnForced = true;
			boolean flying = false;
			boolean fallFlying = false;
			float fallDistance = 0;
			boolean onGround = true;

			if (jsonStr != null && !jsonStr.isEmpty()) {
				try {
					JsonObject obj = new Gson().fromJson(jsonStr, JsonObject.class);
					if (obj.has("SpawnX")) {
						spawnLoc.setX(obj.get("SpawnX").getAsDouble());
					}
					if (obj.has("SpawnY")) {
						spawnLoc.setY(obj.get("SpawnY").getAsDouble());
					}
					if (obj.has("SpawnZ")) {
						spawnLoc.setZ(obj.get("SpawnZ").getAsDouble());
					}
					if (obj.has("Pos")) {
						JsonArray arr = obj.get("Pos").getAsJsonArray();
						playerLoc.setX(arr.get(0).getAsDouble());
						playerLoc.setY(arr.get(1).getAsDouble());
						playerLoc.setZ(arr.get(2).getAsDouble());
					}
					if (obj.has("Rotation")) {
						JsonArray arr = obj.get("Rotation").getAsJsonArray();
						playerLoc.setYaw(arr.get(0).getAsFloat());
						playerLoc.setPitch(arr.get(1).getAsFloat());
					}
					if (obj.has("Motion")) {
						JsonArray arr = obj.get("Motion").getAsJsonArray();
						motion = new Vector(arr.get(0).getAsDouble(), arr.get(1).getAsDouble(), arr.get(2).getAsDouble());
					}
					if (obj.has("SpawnForced")) {
						spawnForced = obj.get("SpawnForced").getAsBoolean();
					}
					if (obj.has("flying")) {
						flying = obj.get("flying").getAsBoolean();
					}
					if (obj.has("FallFlying")) {
						fallFlying = obj.get("FallFlying").getAsBoolean();
					}
					if (obj.has("FallDistance")) {
						fallDistance = obj.get("FallDistance").getAsFloat();
					}
					if (obj.has("OnGround")) {
						onGround = obj.get("OnGround").getAsBoolean();
					}
				} catch (Exception ex) {
					ex.printStackTrace();
				}
			}

			return new PlayerWorldData(spawnLoc, playerLoc, motion, spawnForced, flying, fallFlying, fallDistance, onGround);
		}
	}

	/**
	 * Gets player location data for a world
	 * <p>
	 * Only valid if the player is currently on this shard.
	 *
	 * @param player  Player's to get data for
	 * @param world   World to get data for
	 *
	 * @return plugin data for this identifier (or null if it doesn't exist or player isn't online)
	 */
	public static PlayerWorldData getPlayerWorldData(Player player, World world) {
		Map<String, String> shardData = DataEventListener.getPlayerShardData(player.getUniqueId());
		if (shardData == null || shardData.isEmpty()) {
			return PlayerWorldData.fromJson(null, world);
		}

		String worldShardData = shardData.get(getRedisPerShardDataWorldKey(world));
		if (worldShardData == null || worldShardData.isEmpty()) {
			return PlayerWorldData.fromJson(null, world);
		}

		return PlayerWorldData.fromJson(worldShardData, world);
	}

	/** Future returns non-null if successfully loaded data, null on error */
	@Nullable
	private static RedisPlayerData transformPlayerData(MonumentaRedisSync mrs, UUID uuid,
		byte[] data, byte[] advancementsBytes, byte[] scoresBytes, byte[] pluginDataBytes, byte[] historyBytes) {
		if (data == null) {
			mrs.getLogger().warning("Failed to retrieve player data; likely player didn't make it past the tutorial");
			return null;
		}

		try {
			String advancements;
			String scores;
			String pluginData;
			String history;

			if (advancementsBytes == null) {
				mrs.getLogger().severe("Player advancements data was missing or corrupted and has been reset");
				advancements = "{}";
			} else {
				advancements = new String(advancementsBytes, StandardCharsets.UTF_8);
			}

			if (scoresBytes == null) {
				mrs.getLogger().severe("Player scores data was missing or corrupted and has been reset");
				scores = "{}";
			} else {
				scores = new String(scoresBytes, StandardCharsets.UTF_8);
			}

			if (pluginDataBytes == null) {
				mrs.getLogger().warning("Player pluginData was missing or corrupted and has been reset");
				pluginData = "{}";
			} else {
				pluginData = new String(pluginDataBytes, StandardCharsets.UTF_8);
			}

			if (historyBytes == null) {
				mrs.getLogger().warning("Player history data was missing or corrupted and has been reset");
				history = "UpdateAllPlayers|" + System.currentTimeMillis() + "|unknown";
			} else {
				history = new String(historyBytes, StandardCharsets.UTF_8);
			}

			return new RedisPlayerData(uuid, mrs.getVersionAdapter().retrieveSaveData(data, new JsonObject()), advancements, scores, pluginData, history);
		} catch (Exception e) {
			mrs.getLogger().severe("Failed to parse player data: " + e.getMessage());
			e.printStackTrace();
			return null;
		}
	}

	public static CompletableFuture<RedisPlayerData> getOfflinePlayerData(UUID uuid) throws Exception {
		if (Bukkit.getPlayer(uuid) != null) {
			throw new Exception("Player " + uuid + " is online");
		}

		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		return RedisAPI.multiStringBytes(conn -> {
			conn.lindex(getRedisDataPath(uuid), 0);
			conn.lindex(getRedisAdvancementsPath(uuid), 0);
			conn.lindex(getRedisScoresPath(uuid), 0);
			conn.lindex(getRedisPluginDataPath(uuid), 0);
			conn.lindex(getRedisHistoryPath(uuid), 0);
		}).thenApply(result -> transformPlayerData(mrs, uuid,
			result.get(0), result.get(1), result.get(2), result.get(3), result.get(4)));
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

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.lindex(getRedisScoresPath(uuid), 0)
				.thenApply(
					(scoreData) -> new Gson().fromJson(scoreData, JsonObject.class).entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().getAsInt())))
				.whenComplete((scoreMap, ex) -> Bukkit.getScheduler().runTask(mrs, () -> {
					if (ex != null) {
						future.completeExceptionally(ex);
					} else {
						future.complete(scoreMap);
					}
				}));
		}

		return future;
	}

	/** Future returns true if successfully committed, false if not */
	public static CompletableFuture<Boolean> saveOfflinePlayerData(RedisPlayerData data) throws Exception {
		MonumentaRedisSync mrs = MonumentaRedisSync.getInstance();

		SaveData splitData = mrs.getVersionAdapter().extractSaveData(data.getNbtTagCompoundData(), null);
		return RedisAPI.multiStringBytes(conn -> {
			conn.lpush(getRedisDataPath(data.getUniqueId()), splitData.getData());
			conn.lpush(getRedisAdvancementsPath(data.getUniqueId()), data.getAdvancements().getBytes(StandardCharsets.UTF_8));
			conn.lpush(getRedisScoresPath(data.getUniqueId()), data.getScores().getBytes(StandardCharsets.UTF_8));
			conn.lpush(getRedisPluginDataPath(data.getUniqueId()), data.getPluginData().getBytes(StandardCharsets.UTF_8));
			conn.lpush(getRedisHistoryPath(data.getUniqueId()), data.getHistory().getBytes(StandardCharsets.UTF_8));
		}).thenApply(result -> {
			if (result.isEmpty() || result.size() != 5 || result.get(0) == null
				|| result.get(1) == null || result.get(2) == null || result.get(3) == null || result.get(4) == null) {
				mrs.getLogger().severe("Failed to commit player data");
				return false;
			}
			return true;
		});
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
