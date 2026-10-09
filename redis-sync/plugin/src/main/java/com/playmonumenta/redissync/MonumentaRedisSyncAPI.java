package com.playmonumenta.redissync;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.common.event.PlayerServerTransferEvent;
import com.playmonumenta.redissync.adapters.VersionAdapter.ReturnParams;
import com.playmonumenta.redissync.adapters.VersionAdapter.SaveData;
import com.playmonumenta.redissync.data.ContentData;
import com.playmonumenta.redissync.event.PlayerContentChangeRequestEvent;
import com.playmonumenta.redissync.event.UpdateAvailableContentIdsEvent;
import com.playmonumenta.redissync.utils.MMLog;
import com.playmonumenta.redissync.utils.Trie;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.wrappers.Rotation;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.TransactionResult;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
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
		private String mContent;
		private String mPluginData;
		private String mHistory;
		/* Whether this was read from redis, and the newest history entry as it was then (null if there was none) */
		private final boolean mWasRead;
		private final @Nullable String mReadHistory;

		public RedisPlayerData(UUID uuid, Object nbtTagCompoundData, String advancements,
		                       String scores, String pluginData, String content, String history) {
			this(uuid, nbtTagCompoundData, advancements, scores, pluginData, content, history, false, null);
		}

		private RedisPlayerData(UUID uuid, Object nbtTagCompoundData, String advancements, String scores, String pluginData,
		                        String content, String history, boolean wasRead, @Nullable String readHistory) {
			mWasRead = wasRead;
			mReadHistory = readHistory;
			mUUID = uuid;
			mNbtTagCompoundData = nbtTagCompoundData;
			mAdvancements = advancements;
			mScores = scores;
			mPluginData = pluginData;
			mContent = content;
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

		public String getContent() {
			return mContent;
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

		public void setContent(String content) {
			this.mContent = content;
		}

		public void setHistory(String history) {
			this.mHistory = history;
		}
	}

	public static final int TIMEOUT_SECONDS = 10;
	/* A transfer to a shard network relay does not list as online is expected to fail fast */
	static final int OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS = 25;
	public static final ArgumentSuggestions<CommandSender> SUGGESTIONS_ALL_CACHED_PLAYER_NAMES = ArgumentSuggestions.strings((info) ->
		getAllCachedPlayerNames().toArray(String[]::new));


	private static final String DEFAULT_CONTENT_JSON = new ContentData("").toJson().toString();
	private static final byte[] DEFAULT_CONTENT_BYTES = DEFAULT_CONTENT_JSON.getBytes(StandardCharsets.UTF_8);
	private static final Trie<UUID> mNameToUuidTrie = new Trie<>();
	private static final Map<String, UUID> mNameToUuid = new ConcurrentHashMap<>();
	private static final Map<String, UUID> mNameLowercaseToUuid = new ConcurrentHashMap<>();
	private static final Map<UUID, String> mUuidToName = new ConcurrentHashMap<>();

	protected static void updateUuidToName(UUID uuid, String name) {
		mUuidToName.put(uuid, name);
	}

	protected static void updateNameToUuid(String name, UUID uuid) {
		mNameToUuid.put(name, uuid);
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

	/**
	 * Refreshes content provided by other plugins
	 */
	public static void refreshAvailableContentIds() {
		UpdateAvailableContentIdsEvent event = new UpdateAvailableContentIdsEvent();
		event.callEvent();
		DataEventListener.updateAvailableContentEvent(event);
	}

	/**
	 * Gets the set of known available content IDs
	 * @return All known available content IDs
	 */
	public static Set<String> availableContentIds() {
		return DataEventListener.getAvailableContentIds();
	}

	// Thread-safe: backed by ConcurrentHashMap, callable from any thread
	public static @Nullable String cachedUuidToName(UUID uuid) {
		return mUuidToName.get(uuid);
	}

	// Thread-safe: backed by ConcurrentHashMap, callable from any thread
	public static @Nullable UUID cachedNameToUuid(String name) {
		// Player names are case-sensitive (see scoreboard values) - only use case-insensitive version as a fallback.
		UUID caseSensitiveUuid = mNameToUuid.get(name);
		if (caseSensitiveUuid != null) {
			return caseSensitiveUuid;
		}
		return mNameLowercaseToUuid.get(name.toLowerCase(Locale.ROOT));
	}

	public static Set<String> getAllCachedPlayerNames() {
		return Collections.unmodifiableSet(mNameToUuid.keySet());
	}

	public static Set<UUID> getAllCachedPlayerUuids() {
		return Collections.unmodifiableSet(mUuidToName.keySet());
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
		return PlayerSessions.isLocked(player);
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

	@SuppressWarnings("deprecation")
	public static void sendPlayer(Player player, String target, @Nullable Location returnLoc, @Nullable Float returnYaw, @Nullable Float returnPitch) throws Exception {
		Plugin mrs = DataEventListener.getPlugin();

		/* Before the transfer events fire, which they must not for a player already locked */
		if (PlayerSessions.isLocked(player)) {
			return;
		}

		long startTime = System.currentTimeMillis();

		if (target.equalsIgnoreCase(CommonConfig.getShardName())) {
			player.sendMessage(Component.text("Can not transfer to the same server you are already on", NamedTextColor.RED));
			return;
		}

		com.playmonumenta.redissync.event.PlayerServerTransferEvent legacyEvent = new com.playmonumenta.redissync.event.PlayerServerTransferEvent(player, target);
		Bukkit.getPluginManager().callEvent(legacyEvent);
		if (legacyEvent.isCancelled()) {
			return;
		}
		PlayerServerTransferEvent event = new PlayerServerTransferEvent(player, target);
		Bukkit.getPluginManager().callEvent(event);
		if (event.isCancelled()) {
			return;
		}

		player.sendMessage(Component.text("Transferring you to " + target, NamedTextColor.GOLD));

		int timeoutTicks = SessionLock.TIMEOUT_TICKS;
		if (!Arrays.asList(NetworkRelayIntegration.getOnlineTransferTargets()).contains(target)) {
			timeoutTicks = OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS;
		}

		/* The save the target shard loads, with any return location applied to it */
		ReturnParams returnParams = returnLoc != null || returnYaw != null || returnPitch != null ? new ReturnParams(returnLoc, returnYaw, returnPitch) : null;
		SessionLock lock = PlayerSessions.lock(player, timeoutTicks, returnParams);

		/* The target shard loads from redis, so the proxy must not move the player before the save commits */
		lock.afterSaves(List.of(), () -> {
			/* Sent over the player's own connection, so it reaches whichever proxy they are on */
			ByteArrayDataOutput out = ByteStreams.newDataOutput();
			out.writeUTF("Connect");
			out.writeUTF(target);

			player.sendPluginMessage(mrs, "BungeeCord", out.toByteArray());
			return CompletableFuture.completedFuture(null);
		});

		MMLog.trace(() -> "Transferring players took " + (System.currentTimeMillis() - startTime) + " milliseconds on main thread");
	}

	public static void stashPut(Player player, @Nullable String name) throws Exception {
		/* No lock: this only reads the player's data, and stashes whatever is newest once this save commits */
		savePlayer(player);

		PlayerSessions.waitForSaves(player, () -> {
			final String saveName = name != null ? name : player.getUniqueId().toString();

			if (name != null) {
				try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
					conn.sadd(getStashListPath(), saveName).exceptionally(ex -> {
						MMLog.severe("Redis sadd failed in stashPut: " + ex.getMessage());
						return null;
					});
				}
			}

			/* Read every history list's newest entry atomically as bytes */
			RedisAPI.multiStringBytes(conn -> {
				conn.lindex(getRedisDataPath(player), 0);
				conn.lindex(getRedisAdvancementsPath(player), 0);
				conn.lindex(getRedisScoresPath(player), 0);
				conn.lindex(getRedisPluginDataPath(player), 0);
				conn.lindex(getRedisContentPath(player), 0);
				conn.lindex(getRedisHistoryPath(player), 0);
			}).whenComplete((readResult, readEx) -> {
				if (readEx != null) {
					MMLog.severe("Got exception while reading data for stash put for player '" + player.getName() + "'", readEx);
					player.sendMessage(Component.text("Failed to save stash data: " + readEx.getMessage(), NamedTextColor.RED));
					return;
				}

				byte[] data = readResult.get(0);
				byte[] advance = readResult.get(1);
				byte[] score = readResult.get(2);
				byte[] plugin = readResult.get(3);
				byte[] content = Objects.requireNonNullElse(readResult.get(4), DEFAULT_CONTENT_BYTES);
				byte[] history = readResult.get(5);

				if (data == null || advance == null || score == null || plugin == null || history == null) {
					MMLog.severe("Failed to retrieve player's data to stash for player '" + player.getName() + "'");
					player.sendMessage(Component.text("Failed to save stash data: player data missing", NamedTextColor.RED));
					return;
				}

				/* Write them to the stash atomically as bytes */
				PlayerSessions.trackWrite(RedisAPI.multiStringBytes(conn -> {
					conn.hset(getStashPath(), saveName + "-data", data);
					conn.hset(getStashPath(), saveName + "-advancements", advance);
					conn.hset(getStashPath(), saveName + "-scores", score);
					conn.hset(getStashPath(), saveName + "-plugins", plugin);
					conn.hset(getStashPath(), saveName + "-content", content);
					conn.hset(getStashPath(), saveName + "-history", history);
				})).whenComplete((writeResult, writeEx) -> {
					if (writeEx != null) {
						MMLog.severe("Got exception while committing stash data for player '" + player.getName() + "'", writeEx);
						player.sendMessage(Component.text("Failed to save stash data: " + writeEx.getMessage(), NamedTextColor.RED));
						return;
					}
					player.sendMessage(Component.text("Data, scores, advancements saved to stash successfully", NamedTextColor.GOLD));
				});
			});
		}, true);
	}

	public static void stashGet(Player player, @Nullable String name) throws Exception {

		/* Saves the player first, in case this was a mistake, so they can get back */
		SessionLock lock = PlayerSessions.lock(player);
		lock.afterSaves(List.of(), () -> {
			final String saveName = name != null ? name : player.getUniqueId().toString();
			Component missing = name == null
				? Component.text("You don't have any stash data", NamedTextColor.RED)
				: Component.text("No stash data found for '" + name + "'", NamedTextColor.RED);

			return copySavedStateThenKick(lock, player, "stash data", conn -> {
				conn.hget(getStashPath(), saveName + "-data");
				conn.hget(getStashPath(), saveName + "-advancements");
				conn.hget(getStashPath(), saveName + "-scores");
				conn.hget(getStashPath(), saveName + "-plugins");
				conn.hget(getStashPath(), saveName + "-content");
				conn.hget(getStashPath(), saveName + "-history");
			}, missing, "stash@", null, Component.text("Stash data loaded successfully"));
		});
	}

	/**
	 * The end of a data handoff: reads one saved state (the six fields {@code read} queues, in order
	 * data, advancements, scores, plugin data, content, history), pushes it as the locked player's
	 * newest save, and kicks them so they rejoin with it. Any failure is reported to {@code notify};
	 * one before the write releases the lock.
	 *
	 * @return the redis work, complete once the state is written or the handoff has failed
	 */
	private static CompletableFuture<Void> copySavedStateThenKick(SessionLock lock, Player notify, String what,
	                                                                Consumer<RedisAPI.BorrowedCommands<String, byte[]>> read, Component missing,
	                                                                String historyPrefix, @Nullable Component success, Component kickMessage) {
		Player to = lock.getPlayer();
		return RedisAPI.multiStringBytes(read).<CompletableFuture<Void>>handle((readResult, readEx) -> {
			if (readEx != null) {
				MMLog.severe("Got exception while reading " + what + " for player '" + to.getName() + "'", readEx);
				tell(notify, Component.text("Failed to load " + what + ": " + readEx.getMessage(), NamedTextColor.RED));
				lock.releaseLater();
				return CompletableFuture.<Void>completedFuture(null);
			}

			byte[] data = readResult.get(0);
			byte[] advance = readResult.get(1);
			byte[] score = readResult.get(2);
			byte[] plugin = readResult.get(3);
			byte[] content = Objects.requireNonNullElse(readResult.get(4), DEFAULT_CONTENT_BYTES);
			byte[] historyRaw = readResult.get(5);

			/* Make sure there's actually data */
			if (data == null || advance == null || score == null || plugin == null || historyRaw == null) {
				MMLog.warning("No " + what + " to load for player '" + to.getName() + "'");
				tell(notify, missing);
				lock.releaseLater();
				return CompletableFuture.<Void>completedFuture(null);
			}

			byte[] historyOut = (historyPrefix + new String(historyRaw, StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8);

			/*
			 * From here the player's in-memory state is stale, even if the kick below never happens.
			 * So from here every outcome kicks them: a failed write may still have partly landed, and
			 * only a rejoin loads whatever redis now holds.
			 */
			lock.handOff();

			/* Push them onto every history list atomically as bytes */
			CompletableFuture<TransactionResult> write;
			try {
				write = RedisAPI.multiStringBytes(conn -> {
					conn.lpush(getRedisDataPath(to), data);
					conn.lpush(getRedisAdvancementsPath(to), advance);
					conn.lpush(getRedisScoresPath(to), score);
					conn.lpush(getRedisPluginDataPath(to), plugin);
					conn.lpush(getRedisContentPath(to), content);
					conn.lpush(getRedisHistoryPath(to), historyOut);
				});
			} catch (RuntimeException ex) {
				write = CompletableFuture.failedFuture(ex);
			}
			return write.<Void>handle((writeResult, writeEx) -> {
				Throwable failure = writeEx != null ? writeEx : RedisAPI.firstCommandError(writeResult);
				if (failure != null) {
					MMLog.severe("Got exception while writing " + what + " for player '" + to.getName() + "'; kicking them so they reload whatever was saved", failure);
					tell(notify, Component.text("Failed to load " + what + ": " + failure.getMessage(), NamedTextColor.RED));
				} else if (success != null) {
					tell(notify, success);
				}
				/* The rejoin loads the replacement; until they go, the lock keeps kicking them */
				PlayerSessions.runOnMainThread(() -> to.kick(failure == null ? kickMessage
					: Component.text("Failed to load " + what + ", please rejoin", NamedTextColor.RED)));
				return null;
			});
		}).thenCompose(written -> written);
	}

	/* Messages from redis callbacks, which are not on the main thread */
	private static void tell(Player player, Component message) {
		PlayerSessions.runOnMainThread(() -> player.sendMessage(message));
	}

	public static void stashInfo(Player player, @Nullable String name) {
		Plugin mrs = DataEventListener.getPlugin();

		String saveName = name != null ? name : player.getUniqueId().toString();

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.hget(getStashPath(), saveName + "-history").whenComplete((history, ex) -> {
				Bukkit.getScheduler().runTask(mrs, () -> {
					if (ex != null) {
						MMLog.severe("Redis hget failed in stashInfo", ex);
						player.sendMessage(Component.text("Error fetching stash info: " + ex.getMessage(), NamedTextColor.RED));
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
		Plugin mrs = DataEventListener.getPlugin();

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
									MMLog.severe("Redis hmget failed in stashList", e);
									player.sendMessage(Component.text("Error fetching stash list entries: " + e.getMessage(), NamedTextColor.RED));
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

		/* Saves the player first, in case this was a mistake, so they can get back */
		SessionLock lock = PlayerSessions.lock(player);
		/* If that save pushed an entry, the one to roll back to is one older, once it has committed */
		final int rollbackIndex = lock.savePushedEntry() ? index + 1 : index;
		lock.afterSaves(List.of(), () -> copySavedStateThenKick(lock, moderator, "rollback data", conn -> {
			conn.lindex(getRedisDataPath(player), rollbackIndex);
			conn.lindex(getRedisAdvancementsPath(player), rollbackIndex);
			conn.lindex(getRedisScoresPath(player), rollbackIndex);
			conn.lindex(getRedisPluginDataPath(player), rollbackIndex);
			conn.lindex(getRedisContentPath(player), rollbackIndex);
			conn.lindex(getRedisHistoryPath(player), rollbackIndex);
		}, Component.text("Failed to retrieve player's rollback data", NamedTextColor.RED), "rollback@",
			Component.text("Player " + player.getName() + " rolled back successfully", NamedTextColor.GREEN),
			Component.text("Your player data has been rolled back, and you can now re-join the server")));
	}

	public static void playerLoadFromPlayer(Player loadTo, Player loadFrom, int index) throws Exception {

		/* Saves the player first, in case this was a mistake, so they can get back */
		SessionLock lock = PlayerSessions.lock(loadTo);
		/* Waits for loadFrom's saves too, so the copy is of their newest data */
		lock.afterSaves(List.of(loadFrom), () -> copySavedStateThenKick(lock, loadTo, "data", conn -> {
			conn.lindex(getRedisDataPath(loadFrom), index);
			conn.lindex(getRedisAdvancementsPath(loadFrom), index);
			conn.lindex(getRedisScoresPath(loadFrom), index);
			conn.lindex(getRedisPluginDataPath(loadFrom), index);
			conn.lindex(getRedisContentPath(loadFrom), index);
			conn.lindex(getRedisHistoryPath(loadFrom), index);
		}, Component.text("Failed to retrieve player's data to load", NamedTextColor.RED), "loadfrom@" + loadFrom.getName() + "@",
			null, Component.text("Data loaded from player " + loadFrom.getName() + " at index " + index + " and you can now re-join the server")));
	}

	/**
	 * Sets a player's location on a given world.
	 *
	 * @param player    the player whose location to set
	 * @param worldName the name of the world
	 * @param loc       a vector containing coordinates the player should be set to
	 * @param yaw       the yaw the player should be set to
	 * @param pitch     the pitch the player should be set to
	 *
	 * @return a completable future which is completed when the saving has finished
	 */
	public static CompletableFuture<Void> setPlayerLocationOnWorld(Player player, String worldName, Vector loc, double yaw, double pitch) {
		if (BukkitConfigAPI.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return CompletableFuture.completedFuture(null);
		}

		CompletableFuture<Void> future = new CompletableFuture<>();

		String shardDataPath = getRedisPerShardDataPath(player);
		String worldKey = getRedisPerShardDataWorldKey(worldName);
		// Also update the local sharddata cache
		Map<String, String> shardDataMap = PlayerSessions.getShardData(player.getUniqueId());
		JsonObject worldShardDataJson;
		if (shardDataMap == null) {
			worldShardDataJson = new JsonObject();
		} else {
			String worldShardData = shardDataMap.get(worldKey);
			if (worldShardData == null) {
				worldShardDataJson = new JsonObject();
			} else {
				worldShardDataJson = new Gson().fromJson(worldShardData, JsonObject.class);
			}
		}

		JsonArray pos = new JsonArray();
		pos.add(loc.getX());
		pos.add(loc.getY());
		pos.add(loc.getZ());
		worldShardDataJson.add("Pos", pos);

		JsonArray rotation = new JsonArray();
		rotation.add(yaw);
		rotation.add(pitch);
		worldShardDataJson.add("Rotation", rotation);

		String worldShardDataStr = worldShardDataJson.toString();

		if (shardDataMap != null) {
			shardDataMap.put(worldKey, worldShardDataStr);
		}

		PlayerSessions.trackWrite(RedisAPI.multi(commands -> {
			commands.hset(shardDataPath, worldKey, worldShardDataStr);
		})).whenComplete((unused, ex2) -> {
			if (ex2 != null) {
				MMLog.severe("Failed to save player data for player=" + player.getName(), ex2);
				future.completeExceptionally(ex2);
				return;
			}
			future.complete(null);
		});
		return future;
	}

	/**
	 * Sets a player's world and location on a different shard.
	 *
	 * @param player    the player whose location to set
	 * @param shard     the shard that the world is on
	 * @param worldName the name of the world
	 * @param loc       a vector containing coordinates the player should be set to
	 * @param yaw       the yaw the player should be set to
	 * @param pitch     the pitch the player should be set to
	 *
	 * @return a completable future which is completed when the saving has finished
	 */
	public static CompletableFuture<Void> setPlayerWorldAndLocationOnShard(Player player, String shard, String worldName, Vector loc, double yaw, double pitch) {
		if (BukkitConfigAPI.getSavingDisabled()) {
			/* No data saved, no data loaded */
			return CompletableFuture.completedFuture(null);
		}

		CompletableFuture<Void> future = PlayerSessions.trackWrite(new CompletableFuture<>());

		String shardDataPath = getRedisPerShardDataPath(player);
		RedisFuture<String> shardDataFuture;
		String worldKey = getRedisPerShardDataWorldKey(worldName);
		try (RedisAPI.BorrowedCommands<String, String> commands = RedisAPI.borrow()) {
			shardDataFuture = commands.hget(shardDataPath, worldKey);
		}
		shardDataFuture.toCompletableFuture().whenComplete((worldShardData, ex) -> {
			if (ex != null) {
				MMLog.severe("Failed to set location on other shard for player=" + player.getName(), ex);
				future.completeExceptionally(ex);
				return;
			}

			final JsonObject worldShardDataJson;
			if (worldShardData == null || worldShardData.isEmpty()) {
				MMLog.trace("No world shard data for player '" + player.getName() + "', using default");
				worldShardDataJson = new JsonObject();
			} else {
				MMLog.trace("Found world shard data for player '" + player.getName() + "': '" + worldShardData + "'");
				worldShardDataJson = new Gson().fromJson(worldShardData, JsonObject.class);
			}

			JsonArray pos = new JsonArray();
			pos.add(loc.getX());
			pos.add(loc.getY());
			pos.add(loc.getZ());
			worldShardDataJson.add("Pos", pos);

			JsonArray rotation = new JsonArray();
			rotation.add(yaw);
			rotation.add(pitch);
			worldShardDataJson.add("Rotation", rotation);

			JsonObject newShardData = new JsonObject();
			newShardData.addProperty("World", worldName);
			String overallShardDataStr = new Gson().toJson(newShardData);

			RedisAPI.multi(commands -> {
				commands.hset(shardDataPath, worldKey, worldShardDataJson.toString());
				commands.hset(shardDataPath, shard, overallShardDataStr);
			}).whenComplete((unused, ex2) -> {
				if (ex2 != null) {
					MMLog.severe("Failed to save player data for player=" + player.getName(), ex2);
					future.completeExceptionally(ex2);
					return;
				}
				future.complete(null);
			});
		});
		return future;
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
		return getRedisPerShardDataWorldKey(world.getName());
	}

	public static String getRedisPerShardDataWorldKey(String worldName) {
		return "worlddata:" + worldName;
	}

	public static String getRedisPluginDataPath(Player player) {
		return getRedisPluginDataPath(player.getUniqueId());
	}

	public static String getRedisPluginDataPath(UUID uuid) {
		return String.format("%s:playerdata:%s:plugins", CommonConfig.getServerDomain(), uuid.toString());
	}

	public static String getRedisContentPath(Player player) {
		return getRedisContentPath(player.getUniqueId());
	}

	public static String getRedisContentPath(UUID uuid) {
		return String.format("%s:playerdata:%s:content", CommonConfig.getServerDomain(), uuid.toString());
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
	 * Saves all of player's data, including advancements, scores, plugin data, content, inventory, world location, etc.
	 * <p>
	 * Also creates a rollback point like all full saves.
	 * <p>
	 * Takes several milliseconds so care should be taken not to call this too frequently
	 */
	public static void savePlayer(Player player) throws Exception {
		try {
			DataEventListener.getAdapter().savePlayer(player);
		} catch (Exception ex) {
			String message = "Failed to save player data for player '" + player.getName() + "'";
			MMLog.severe(message);
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
		JsonObject pluginData = PlayerSessions.getPluginData(uuid);
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

		private static final List<String> SPAWN_KEYS = List.of("SpawnX", "SpawnY", "SpawnZ", "SpawnForced", "SpawnAngle", "SpawnDimension");

		private final @Nullable Location mSpawnLoc; // {"SpawnX":-1450,"SpawnY":241,"SpawnZ":-1498,"SpawnAngle":0.0}, null if none saved for this world
		private final Location mPlayerLoc; // {"Pos":[-1280.5,95.0,5369.7001953125],"Rotation":[-358.9,2.1]}
		private final Vector mMotion; // {"Motion":[0.0,-0.0784000015258789,0.0]}
		private final boolean mSpawnForced; // {"SpawnForced":true}
		private final boolean mFlying; // {"flying":false}
		private final boolean mFallFlying; // {"FallFlying":false}
		private final float mFallDistance; // {"FallDistance":0.0}
		private final boolean mOnGround; // {"OnGround":true}

		private PlayerWorldData(@Nullable Location spawnLoc, Location playerLoc, Vector motion, boolean spawnForced, boolean flying, boolean fallFlying, float fallDistance, boolean onGround) {
			mSpawnLoc = spawnLoc;
			mPlayerLoc = playerLoc;
			mMotion = motion;
			mSpawnForced = spawnForced;
			mFlying = flying;
			mFallFlying = fallFlying;
			mFallDistance = fallDistance;
			mOnGround = onGround;
		}

		public @Nullable Location getSpawnLoc() {
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
			if (mSpawnLoc != null) {
				player.setBedSpawnLocation(mSpawnLoc, mSpawnForced);
			}
		}

		/**
		 * Whether a saved spawn point is located in the given world. A missing dimension is the vanilla default, the overworld.
		 */
		static boolean spawnDimensionMatches(@Nullable String spawnDimension, String worldKey, String worldName) {
			if (spawnDimension == null || spawnDimension.isEmpty()) {
				spawnDimension = "minecraft:overworld";
			}
			return spawnDimension.equals(worldKey) || spawnDimension.equals(worldName);
		}

		/**
		 * Removes the spawn point from saved world data if it is not located in that world.
		 * Spawn is one global value per player but is saved with every world, so a copy saved while standing
		 * in a different world must not be applied here, or the player would respawn in the wrong world.
		 */
		static void removeForeignSpawn(JsonObject worldData, World world) {
			if (SPAWN_KEYS.stream().noneMatch(worldData::has)) {
				return;
			}
			String dimension = worldData.get("SpawnDimension") instanceof JsonPrimitive primitive ? primitive.getAsString() : null;
			if (!spawnDimensionMatches(dimension, world.getKey().toString(), world.getName())) {
				SPAWN_KEYS.forEach(worldData::remove);
			}
		}

		private static PlayerWorldData fromJson(@Nullable String jsonStr, World world) {
			// Defaults to world spawn
			Location playerLoc = world.getSpawnLocation();
			Location spawnLoc = null;
			Vector motion = new Vector(0, 0, 0);
			boolean spawnForced = true;
			boolean flying = false;
			boolean fallFlying = false;
			float fallDistance = 0;
			boolean onGround = true;

			if (jsonStr != null && !jsonStr.isEmpty()) {
				try {
					JsonObject obj = new Gson().fromJson(jsonStr, JsonObject.class);
					removeForeignSpawn(obj, world);
					if (obj.has("SpawnX") && obj.has("SpawnY") && obj.has("SpawnZ")) {
						spawnLoc = new Location(world, obj.get("SpawnX").getAsDouble(), obj.get("SpawnY").getAsDouble(), obj.get("SpawnZ").getAsDouble());
						if (obj.has("SpawnAngle")) {
							spawnLoc.setYaw(obj.get("SpawnAngle").getAsFloat());
						}
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
					MMLog.severe("Failed to parse shard data", ex);
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
		Map<String, String> shardData = PlayerSessions.getShardData(player.getUniqueId());
		if (shardData == null || shardData.isEmpty()) {
			return PlayerWorldData.fromJson(null, world);
		}

		String worldShardData = shardData.get(getRedisPerShardDataWorldKey(world));
		if (worldShardData == null || worldShardData.isEmpty()) {
			return PlayerWorldData.fromJson(null, world);
		}

		return PlayerWorldData.fromJson(worldShardData, world);
	}

	/**
	 * Gets player current full content data
	 *
	 * @param player Player to get data for
	 * @return The player's content JSON, which is empty if not set
	 */
	public static ContentData getPlayerContentData(Player player) {
		return getPlayerContentData(player.getUniqueId());
	}

	/**
	 * Gets player current full content data
	 *
	 * @param playerUUID Player UUID to get data for
	 * @return The player's content JSON, which is empty if not set
	 */
	public static ContentData getPlayerContentData(UUID playerUUID) {
		return PlayerSessions.getContentData(playerUUID);
	}

	/**
	 * Requests that a player be sent to content by another plugin
	 * <p/>
	 * If no plugin handles this event, the player's content does not change.
	 *
	 * @param player Player to send data for
	 * @param contentData JSON corresponding to the content
	 */
	public static void requestPlayerContentDataChange(Player player, ContentData contentData) {
		requestPlayerContentDataChange(player, Collections.emptySet(), contentData);
	}

	/**
	 * Requests that a player be sent to content by another plugin
	 * <p/>
	 * If no plugin handles this event, the player's content does not change.
	 *
	 * @param player Player to send data for
	 * @param others Other players to send data for
	 * @param contentData JSON corresponding to the content
	 */
	public static void requestPlayerContentDataChange(Player player, Collection<Player> others, ContentData contentData) {
		Set<Player> copyOthers = new HashSet<>(others);
		copyOthers.remove(player);
		PlayerContentChangeRequestEvent newEvent = new PlayerContentChangeRequestEvent(player, copyOthers, contentData);
		Bukkit.getPluginManager().callEvent(newEvent);
	}

	/**
	 * Saves the player's content; should be called by an implementing plugin
	 *
	 * @param playerUUID  Player UUID to save data for
	 * @param contentData The content data to be saved for the player
	 */
	public static void savePlayerContent(UUID playerUUID, ContentData contentData) {
		ContentData oldContent = PlayerSessions.getContentData(playerUUID);
		PlayerSessions.setContentData(playerUUID, contentData);

		String oldContentId = oldContent == null ? "null" : oldContent.getId();
		String newContentId = contentData == null ? "null" : contentData.getId();
		if (!oldContentId.equals(newContentId)) {
			DataEventListener.notifyContentChange(playerUUID);
		}
	}

	/** Future returns non-null if successfully loaded data, null on error */
	@Nullable
	private static RedisPlayerData transformPlayerData(UUID uuid,
		byte[] data, byte[] advancementsBytes, byte[] scoresBytes, byte[] pluginDataBytes, byte[] contentBytes, byte[] historyBytes) {
		if (data == null) {
			MMLog.warning("Failed to retrieve player data; likely player didn't make it past the tutorial");
			return null;
		}

		try {
			String advancements;
			String scores;
			String pluginData;
			String content;
			String history;

			if (advancementsBytes == null) {
				MMLog.severe("Player advancements data was missing or corrupted and has been reset");
				advancements = "{}";
			} else {
				advancements = new String(advancementsBytes, StandardCharsets.UTF_8);
			}

			if (scoresBytes == null) {
				MMLog.severe("Player scores data was missing or corrupted and has been reset");
				scores = "{}";
			} else {
				scores = new String(scoresBytes, StandardCharsets.UTF_8);
			}

			if (pluginDataBytes == null) {
				MMLog.warning("Player pluginData was missing or corrupted and has been reset");
				pluginData = "{}";
			} else {
				pluginData = new String(pluginDataBytes, StandardCharsets.UTF_8);
			}

			if (contentBytes == null) {
				MMLog.warning("Player content was missing or corrupted and has been reset");
				content = "";
			} else {
				content = new String(contentBytes, StandardCharsets.UTF_8);
			}

			if (historyBytes == null) {
				MMLog.warning("Player history data was missing or corrupted and has been reset");
				history = "UpdateAllPlayers|" + System.currentTimeMillis() + "|unknown";
			} else {
				history = new String(historyBytes, StandardCharsets.UTF_8);
			}

			return new RedisPlayerData(uuid, DataEventListener.getAdapter().retrieveSaveData(data, new JsonObject()), advancements, scores, pluginData, content, history,
				true, historyBytes == null ? null : history);
		} catch (Exception e) {
			MMLog.severe("Failed to parse player data", e);
			return null;
		}
	}

	public static CompletableFuture<RedisPlayerData> getOfflinePlayerData(UUID uuid) throws Exception {
		if (Bukkit.getPlayer(uuid) != null) {
			throw new Exception("Player " + uuid + " is online");
		}


		return RedisAPI.multiStringBytes(conn -> {
			conn.lindex(getRedisDataPath(uuid), 0);
			conn.lindex(getRedisAdvancementsPath(uuid), 0);
			conn.lindex(getRedisScoresPath(uuid), 0);
			conn.lindex(getRedisPluginDataPath(uuid), 0);
			conn.lindex(getRedisContentPath(uuid), 0);
			conn.lindex(getRedisHistoryPath(uuid), 0);
		}).thenApply(result -> transformPlayerData(uuid,
			result.get(0), result.get(1), result.get(2), result.get(3), result.get(4), result.get(5)));
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

		Plugin mrs = DataEventListener.getPlugin();

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

	/*
	 * Pushes an offline write onto every history list, but only if the newest history entry is still
	 * the one expected. The check and push are one step, so nothing can save in between.
	 * KEYS: data, advancements, scores, plugin data, content, history. ARGV 1-6: the values; 7: "any",
	 * "none" (the history must be empty) or "is" (it must be ARGV 8).
	 */
	private static final String OFFLINE_WRITE_SCRIPT = """
		local newest = redis.call('LINDEX', KEYS[6], 0)
		if ARGV[7] == 'none' and newest then return 0 end
		if ARGV[7] == 'is' and newest ~= ARGV[8] then return 0 end
		for i = 1, 6 do redis.call('LPUSH', KEYS[i], ARGV[i]) end
		return 1
		""";

	/**
	 * Writes back data read with {@link #getOfflinePlayerData}, as the player's newest save.
	 *
	 * <p>Refused (the future returns false) if the player is on this shard or logging in to it, or
	 * if they (or another offline write) have saved since the data was read: writing then would bury
	 * that newer data under the older copy. Data not read from redis is only checked for the
	 * player being here.
	 *
	 * <p>Future returns true if successfully committed, false if not
	 */
	public static CompletableFuture<Boolean> saveOfflinePlayerData(RedisPlayerData data) throws Exception {
		UUID uuid = data.getUniqueId();
		SaveData splitData = DataEventListener.getAdapter().extractSaveData(data.getNbtTagCompoundData(), null);
		/* An edit that keeps the history entry it read must still push a new one, or a second edit from the same read would pass */
		String history = data.mWasRead && data.getHistory().equals(data.mReadHistory) ? "offline@" + data.getHistory() : data.getHistory();
		String mode = !data.mWasRead ? "any" : data.mReadHistory == null ? "none" : "is";
		byte[] expected = (data.mReadHistory == null ? "" : data.mReadHistory).getBytes(StandardCharsets.UTF_8);

		/* The claim keeps a login from loading until this completes */
		CompletableFuture<Boolean> result = new CompletableFuture<>();
		if (!PlayerSessions.claimOfflineWrite(uuid, result)) {
			MMLog.warning("Refusing offline data write for uuid=" + uuid + ": they are on, logging in to or just leaving this shard, or another offline write for them is under way");
			result.complete(false);
			return result;
		}
		try {
			RedisFuture<Long> written;
			try (RedisAPI.BorrowedCommands<String, byte[]> conn = RedisAPI.borrowStringBytes()) {
				written = conn.eval(OFFLINE_WRITE_SCRIPT, ScriptOutputType.INTEGER,
					new String[] {getRedisDataPath(uuid), getRedisAdvancementsPath(uuid), getRedisScoresPath(uuid),
						getRedisPluginDataPath(uuid), getRedisContentPath(uuid), getRedisHistoryPath(uuid)},
					splitData.getData(), data.getAdvancements().getBytes(StandardCharsets.UTF_8), data.getScores().getBytes(StandardCharsets.UTF_8),
					data.getPluginData().getBytes(StandardCharsets.UTF_8), data.getContent().getBytes(StandardCharsets.UTF_8),
					history.getBytes(StandardCharsets.UTF_8), mode.getBytes(StandardCharsets.UTF_8), expected);
			}
			written.toCompletableFuture().whenComplete((pushed, ex) -> {
				if (ex != null) {
					MMLog.severe("Failed to commit offline player data for uuid=" + uuid, ex);
					result.complete(false);
				} else if (pushed == null || pushed != 1L) {
					MMLog.warning("Refusing offline data write for uuid=" + uuid + ": their data has been saved since it was read");
					result.complete(false);
				} else {
					result.complete(true);
				}
			});
		} catch (RuntimeException ex) {
			result.complete(false);
			throw ex;
		}
		return PlayerSessions.trackWrite(result);
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
