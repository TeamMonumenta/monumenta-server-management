package com.playmonumenta.redissync.player;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerAdvancementDataSaveEvent;
import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.event.player.PlayerDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerDataSaveEvent;
import com.google.common.collect.ImmutableMap;
import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.config.BukkitConfig;
import com.playmonumenta.redissync.event.PlayerJoinSetWorldEvent;
import com.playmonumenta.redissync.player.HistoryMetaData.Reason;
import com.playmonumenta.redissync.utils.ScoreboardUtils;
import io.papermc.paper.event.server.ServerResourcesReloadedEvent;
import java.util.function.Consumer;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerEvent;
import org.bukkit.event.player.PlayerQuitEvent;

class PlayerDataListener implements Listener {
	private final Logger mLogger;
	private final PlayerDataManager mPlayerDataManager;
	private final VersionAdapter mVersionAdapter;
	private final BukkitConfig mConfig;

	PlayerDataListener(Logger logger, PlayerDataManager manager, MonumentaRedisSync plugin) {
		this.mLogger = logger;
		this.mPlayerDataManager = manager;
		this.mVersionAdapter = plugin.getVersionAdapter();
		this.mConfig = plugin.getBukkitConfig();
	}

	private void handleDataEventCommon(PlayerEvent event, Consumer<LocalRedisPlayer> handler) {
		Player player = event.getPlayer();

		if (mConfig.isSavingDisabled()) {
			return;
		}

		final var localPlayerData = mPlayerDataManager.getLocalPlayerData(player.getUniqueId());

		if (localPlayerData == null) {
			mLogger.severe("local playerdata cache does not contain an entry from uuid=%s".formatted(player.getUniqueId()));
			return;
		}

		handler.accept(localPlayerData);
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void asyncPlayerPreLoginEvent(AsyncPlayerPreLoginEvent event) {
		mPlayerDataManager.preloadPlayerData(event.getUniqueId());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void serverResourcesReloadedEvent(ServerResourcesReloadedEvent event) {
		mLogger.fine("ServerResourcesReloadedEvent caused by " + event.getCause() + ", saving for all players...");
		for (Player player : Bukkit.getOnlinePlayers()) {
			mLogger.finer("Saving player " + player.getName() + " due to datapack reload");
			try {
				mPlayerDataManager.savePlayerDataWithHistory(player, Reason.ADVANCEMENT_RELOAD.create()).begin();
			} catch (Exception ex) {
				mLogger.severe("Failed to save player '" + player.getName() + "': " + ex.getMessage());
				ex.printStackTrace();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataLoadEvent(PlayerAdvancementDataLoadEvent event) {
		handleDataEventCommon(event, localRedisPlayer -> {
			final var data = localRedisPlayer.currentPlayerData();
			event.setJsonData(data.advancements());
		});
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerDataLoadEvent(PlayerDataLoadEvent event) {
		handleDataEventCommon(event, localRedisPlayer -> {
			try {
				var data = localRedisPlayer.currentPlayerData();
				final var player = event.getPlayer();

				mVersionAdapter.resetPlayerScores(player.getName(), Bukkit.getScoreboardManager().getMainScoreboard());
				ScoreboardUtils.loadFromJsonObject(player, data.scoreData());

				ShardData shardData = data.shardData().get(mConfig.getShardName());

				if (shardData == null) {
					mLogger.fine("Player '%s' has never been to this shard before".formatted(player.getName()));
					shardData = new ShardData(null, null, ImmutableMap.of());
				}

				final var setWorldEvent = new PlayerJoinSetWorldEvent(
					player,
					shardData.findWorld(),
					shardData.worldUuid(),
					shardData.worldName()
				);

				Bukkit.getPluginManager().callEvent(setWorldEvent);

				final var playerWorld = setWorldEvent.getWorld();
				mLogger.finer("After PlayerJoinSetWorldEvent for player '%s' got world={%s: %s}".formatted(player.getName(), playerWorld.getUID(), playerWorld.getName()));
				var worldData = shardData.worldData().get(playerWorld.getUID());

				if (worldData == null) {
					mLogger.finer("No world shard data for player '" + player.getName() + "', using default");
					worldData = new WorldData(null, null);
				} else {
					mLogger.finer("Found world shard data for player '" + player.getName() + "': '" + worldData + "'");
				}

				if (worldData.pos() == null) {
					worldData = worldData.withPos(new PlayerPos(playerWorld.getSpawnLocation()));
				}

				event.setData(mVersionAdapter.retrieveSaveData(data.playerData(), worldData));
				data = data.withShardData(mConfig.getShardName(), shardData.withWorld(playerWorld, worldData));

				// save the current player data
				localRedisPlayer.savePlayer(data).begin();
			} catch (Exception e) {
				// TODO: handle here
				localRedisPlayer.disableSaving();
			}
		});
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerQuitEvent(PlayerQuitEvent event) {
		if (mConfig.isSavingDisabled()) {
			return;
		}

		this.mPlayerDataManager.savePlayerDataWithHistory(event.getPlayer(), Reason.DISCONNECT.create()).begin();
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerConnectionCloseEvent(PlayerConnectionCloseEvent event) {
		this.mPlayerDataManager.onDisconnect(event.getPlayerUniqueId());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataSaveEvent(PlayerAdvancementDataSaveEvent event) {
		event.setCancelled(true);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerDataSaveEvent(PlayerDataSaveEvent event) {
		event.setCancelled(true);
	}
}
