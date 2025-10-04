package com.playmonumenta.redissync.player;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Task;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.config.BukkitConfig;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

public class PlayerDataManager {
	static final Gson GSON = new Gson();

	private final Map<UUID, LocalRedisPlayer> mOnlinePlayerCache = Object2ObjectMaps.synchronize(
		new Object2ObjectOpenHashMap<>()
	);

	private final Logger mLogger;
	private final VersionAdapter mAdapter;
	private final BukkitConfig mConfig;
	private final RedisHandler mRedisHandler;

	public PlayerDataManager(MonumentaRedisSync plugin) {
		Bukkit.getPluginManager().registerEvents(
			new PlayerDataListener(plugin.getLogger(), this, plugin),
			plugin
		);
		this.mLogger = plugin.getLogger();
		this.mAdapter = plugin.getVersionAdapter();
		this.mConfig = plugin.getBukkitConfig();
		this.mRedisHandler = new RedisHandler(plugin);
	}

	/**
	 * @param uuid
	 */
	public void preloadPlayerData(UUID uuid) {
		if (mOnlinePlayerCache.containsKey(uuid)) {
			// TODO: BAD BAD BAD
			return;
		}

		mOnlinePlayerCache.put(uuid, new LocalRedisPlayer(uuid, mRedisHandler));
	}

	public LocalRedisPlayer getLocalPlayerData(UUID uuid) {
		return mOnlinePlayerCache.get(uuid);
	}

	PlayerData loadFromPlayer(PlayerData data, Player player) {
		final var scores = mAdapter.getPlayerScores(
			player.getName(),
			Bukkit.getScoreboardManager().getMainScoreboard()
		);

		final var playerData = mAdapter.savePlayer(player);
		final var playerWorld = player.getWorld();

		var shardData = data.shardData().get(mConfig.getShardName());

		if (shardData == null) {
			mLogger.warning("player %s missing shard data at save".formatted(player.getName()));
			shardData = new ShardData(playerWorld.getUID(), playerWorld.getName(), ImmutableMap.of());
		}

		shardData = shardData.withWorld(playerWorld, playerData.extractedWorldData());

		// TODO: fire the plugin data save event

		return data
			.withShardData(mConfig.getShardName(), shardData)
			.withPlayerData(playerData.nbt())
			.withScores(scores)
			.withAdvancements(playerData.advancementData());
	}

	public Task<Void> savePlayerData(Player player) {
		return savePlayerDataWithHistory(player, null);
	}

	@Coroutine
	public Task<Void> savePlayerDataWithHistory(Player player, @Nullable HistoryMetaData historyMetaData) {
		final var localRedisPlayer = getLocalPlayerData(player.getUniqueId());

		if (localRedisPlayer == null) {
			mLogger.severe("local playerdata cache does not contain an entry from uuid=%s".formatted(player.getUniqueId()));
			return Co.ret();
		}

		if (localRedisPlayer.isSavingDisabled()) {
			mLogger.fine("skipping saving for saving-disabled player with uuid=%s".formatted(player.getUniqueId()));
			return Co.ret();
		}

		final var newData = loadFromPlayer(
			localRedisPlayer.currentPlayerData(),
			player
		);

		if (historyMetaData != null) {
			Co.await(localRedisPlayer.pushHistoryEntry(newData, historyMetaData, mConfig.getMaxHistorySize()));
		} else {
			localRedisPlayer.currentPlayerData(newData);
			Co.await(localRedisPlayer.savePlayer());
		}

		return Co.ret();
	}
}
