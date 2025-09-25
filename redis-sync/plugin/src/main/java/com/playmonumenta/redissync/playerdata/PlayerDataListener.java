package com.playmonumenta.redissync.playerdata;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerAdvancementDataSaveEvent;
import com.destroystokyo.paper.event.player.PlayerDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerDataSaveEvent;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.config.BukkitConfig;
import com.playmonumenta.redissync.event.PlayerJoinSetWorldEvent;
import com.playmonumenta.redissync.utils.ScoreboardUtils;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.Nullable;

class PlayerDataListener implements Listener {
	private final Logger mLogger;
	private final PlayerDataManager mPlayerDataManager;
	private final VersionAdapter mVersionAdapter;

	PlayerDataListener(Logger logger, PlayerDataManager mPlayerDataManager, VersionAdapter mVersionAdapter) {
		this.mLogger = logger;
		this.mPlayerDataManager = mPlayerDataManager;
		this.mVersionAdapter = mVersionAdapter;
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void serverResourcesReloadedEvent(ServerResourcesReloadedEvent event) {
		mLogger.fine("ServerResourcesReloadedEvent caused by " + event.getCause() + ", saving for all players...");
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

	private void handleDataEventCommon(PlayerEvent event, Consumer<LocalRedisPlayer> handler) {
		Player player = event.getPlayer();

		if (BukkitConfig.getSavingDisabled()) {
			return;
		}

		final var localPlayerData = mPlayerDataManager.getLocalPlayerData(player.getUniqueId());

		if (localPlayerData == null) {
			mLogger.severe("local playerdata cache does not contain an entry from uuid=%s".formatted(player.getUniqueId()));
			return;
		}

		handler.accept(localPlayerData);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataLoadEvent(PlayerAdvancementDataLoadEvent event) {
		handleDataEventCommon(event, localRedisPlayer -> {
			final var data = localRedisPlayer.currentPlayerData();
			event.setJsonData(data.advancements());
		});
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataSaveEvent(PlayerAdvancementDataSaveEvent event) {
		handleDataEventCommon(event, localRedisPlayer -> {
			localRedisPlayer.currentPlayerData(data -> data.withAdvancements(event.getJsonData()));
			event.setCancelled(true);
		});
	}

	private void initializeWorldData(Player player, @Nullable JsonObject shardDataJson) {
		World playerWorld = null;
		UUID lastSavedWorldUUID = null;
		String lastSavedWorldName = null;

		mLogger.finer("Shard data loaded for player=" + player.getName());

		if (shardDataJson == null) {
			mLogger.fine("Player '" + player.getName() + "' has never been to this shard before");
		} else {
			if (shardDataJson.has("WorldUUID")) {
				lastSavedWorldUUID = UUID.fromString(shardDataJson.get("WorldUUID").getAsString());
				playerWorld = Bukkit.getWorld(lastSavedWorldUUID);
			}

			if (shardDataJson.has("World")) {
				lastSavedWorldName = shardDataJson.get("World").getAsString();
				if (playerWorld == null) {
					playerWorld = Bukkit.getWorld(lastSavedWorldName);
				}
			}
		}

		if (playerWorld == null) {
			playerWorld = Bukkit.getWorlds().get(0);
		}

		final var event = new PlayerJoinSetWorldEvent(player, playerWorld, lastSavedWorldUUID, lastSavedWorldName);
		Bukkit.getPluginManager().callEvent(event);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataLoadEvent(PlayerDataLoadEvent event) {
		handleDataEventCommon(event, localRedisPlayer -> {
			final var data = localRedisPlayer.currentPlayerData();

			// load scoreboard
			final var player = event.getPlayer();
			mVersionAdapter.resetPlayerScores(player.getName(), Bukkit.getScoreboardManager().getMainScoreboard());
			ScoreboardUtils.loadFromJsonObject(player, data.scoreData());

			// load shard json
			final var shardDataJson = data.shardData().get(BukkitConfig.getShardName());

			if (shardDataJson != null && shardDataJson.has("WorldUUID")) {
				try {
					lastSavedWorldUUID = UUID.fromString(shardDataJson.get("WorldUUID").getAsString());
					World world = Bukkit.getWorld(lastSavedWorldUUID);
					if (world != null) {
						playerWorld = world;
					}
				} catch (Exception ex) {
					mLogger.severe("Got sharddata WorldUUID='" + shardDataJson.get("WorldUUID").getAsString() + "' " +
						"which is invalid: " + ex.getMessage());
					ex.printStackTrace();
				}
			}
		});
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataSaveEvent(PlayerDataSaveEvent event) {
		handleDataEventCommon(event, localRedisPlayer -> {

		});
	}
}
