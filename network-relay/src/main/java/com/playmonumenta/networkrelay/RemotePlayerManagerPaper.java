package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.util.MMLog;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

public final class RemotePlayerManagerPaper extends RemotePlayerManagerAbstraction implements Listener {
	private static final RemotePlayerManagerPaper INSTANCE = new RemotePlayerManagerPaper();

	private RemotePlayerManagerPaper() {
		String lShard = getServerId();
		try {
			for (String shard : NetworkRelayAPI.getOnlineShardNames()) {
				if (shard.equals(lShard)) {
					continue;
				}
				MMLog.fine(() -> "Registering shard " + shard);
				registerServer(shard);
			}
		} catch (Exception ex) {
			MMLog.severe(() -> "Failed to get remote shard names");
			throw new RuntimeException("Failed to get remote shard names", ex);
		}
		onRefreshRequest();
	}

	static RemotePlayerManagerPaper getInstance() {
		return INSTANCE;
	}

	@Override
	public String getServerType() {
		return RemotePlayerMinecraft.SERVER_TYPE;
	}

	@Override
	public String getServerId() {
		@Nullable
		String shardName = null;
		try {
			shardName = NetworkRelayAPI.getShardName();
		} catch (Exception e) {
			MMLog.severe(() -> "Failed to get shard name");
		}
		if (shardName == null) {
			throw new RuntimeException("Got null shard name");
		}
		return shardName;
	}

	static RemotePlayerMinecraft fromLocal(Player player, boolean isOnline) {
		return new RemotePlayerMinecraft(INSTANCE.getServerId(), player.getUniqueId(), player.getName(), isOnline, internalPlayerHiddenTest(player), player.getWorld().getName());
	}

	static boolean internalPlayerHiddenTest(Player player) {
		for (MetadataValue meta : player.getMetadata("vanished")) {
			if (meta.asBoolean()) {
				return true;
			}
		}
		return false;
	}

	boolean isPlayerVisible(Player player) {
		boolean cachedResult = isPlayerVisible(player.getUniqueId());
		boolean currentResult = !internalPlayerHiddenTest(player);
		if (cachedResult ^ currentResult) {
			refreshLocalPlayer(player, false);
		}
		return currentResult;
	}

	@Override
	boolean refreshPlayer(UUID playerUuid, boolean forceBroadcast) {
		if (refreshLocalPlayer(playerUuid, forceBroadcast)) {
			return true;
		}
		refreshRemotePlayer(playerUuid);
		return false;
	}

	private long mNextRefreshTime = 0L;
	private @Nullable BukkitTask mRefreshTimer = null;

	@Override
	public void onRefreshRequest() {
		long now = System.currentTimeMillis();
		if (now >= mNextRefreshTime) {
			mNextRefreshTime = now + 1000;
			if (mRefreshTimer != null) {
				mRefreshTimer.cancel();
			}
			mRefreshTimer = null;
			refreshLocalPlayers(true);
		} else {
			if (mRefreshTimer != null) {
				return;
			}

			mRefreshTimer = Bukkit.getScheduler().runTaskLater(NetworkRelay.getInstance(), () -> refreshLocalPlayers(true), 20L);
		}
	}

	@Override
	void refreshLocalPlayers(boolean forceBroadcast) {
		for (Player player : Bukkit.getOnlinePlayers()) {
			refreshLocalPlayer(player, forceBroadcast);
		}
	}

	@Override
	boolean refreshLocalPlayer(UUID uuid, boolean forceBroadcast) {
		@Nullable Player localPlayer = Bukkit.getPlayer(uuid);
		if (localPlayer != null && localPlayer.isOnline()) {
			refreshLocalPlayer(localPlayer, forceBroadcast);
			return true;
		}
		return false;
	}

	// Run this on local players whenever their information is out of date
	void refreshLocalPlayer(Player player, boolean forceBroadcast) {
		MMLog.fine(() -> "Refreshing local player " + player.getName());
		RemotePlayerMinecraft localPlayer = fromLocal(player, true);

		// update local player with data
		if (updateLocalPlayer(localPlayer, false, forceBroadcast)) {
			localPlayer.broadcast();
		}
	}

	@Override
	void callPlayerLoadEvent(RemotePlayerAbstraction player) {
		RemotePlayerLoadedEvent remotePE = new RemotePlayerLoadedEvent(player);
		Bukkit.getServer().getPluginManager().callEvent(remotePE);
	}

	@Override
	void callPlayerUnloadEvent(RemotePlayerAbstraction player) {
		RemotePlayerUnloadedEvent remotePE = new RemotePlayerUnloadedEvent(player);
		Bukkit.getServer().getPluginManager().callEvent(remotePE);
	}

	@Override
	void callPlayerUpdatedEvent(RemotePlayerAbstraction player) {
		RemotePlayerUpdatedEvent remotePE = new RemotePlayerUpdatedEvent(player);
		Bukkit.getServer().getPluginManager().callEvent(remotePE);
	}

	@Override
	Map<String, JsonObject> callGatherPluginDataEvent(RemotePlayerAbstraction player) {
		GatherRemotePlayerDataEvent remotePE = new GatherRemotePlayerDataEvent(player);
		Bukkit.getServer().getPluginManager().callEvent(remotePE);
		return remotePE.getPluginData();
	}

	@Override
	boolean playerShouldBeRefreshed(RemotePlayerAbstraction player) {
		// TODO: NetworkChat only refreshes if the player is offline
		if (player.mIsOnline) {
			return false;
		}
		if (!player.getServerType().equals(RemotePlayerMinecraft.SERVER_TYPE)) {
			return false;
		}
		@Nullable Player localPlayer = Bukkit.getPlayer(player.mUuid);
		return localPlayer != null && localPlayer.isOnline();
	}

	@Override
	void refreshLocalPlayerWithDelay(UUID uuid) {
		refreshPlayer(uuid, true);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void destOnlineEvent(DestOnlineEvent event) {
		String remoteShardName = event.getDest();
		if (getServerId().equals(remoteShardName)) {
			return;
		}
		registerServer(remoteShardName);
	}

	@EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
	public void destOfflineEvent(DestOfflineEvent event) {
		String remoteShardName = event.getDest();
		if (getServerId().equals(remoteShardName)) {
			return;
		}
		unregisterServer(remoteShardName);
	}

	// Player ran a command
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void playerCommandPreprocessEvent(PlayerCommandPreprocessEvent event) {
		Player player = event.getPlayer();
		String command = event.getMessage();

		if (command.startsWith("/pv ")
			|| "/pv".equals(command)
			|| command.contains("vanish")) {
			Bukkit.getScheduler().runTask(NetworkRelay.getInstance(), () -> refreshLocalPlayer(player, false));
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		refreshLocalPlayer(player, true);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void playerQuitEvent(PlayerQuitEvent event) {
		Player player = event.getPlayer();
		RemotePlayerAbstraction remotePlayer = getRemotePlayerShard(getRemotePlayer(player.getUniqueId()));
		if (remotePlayer != null && remotePlayer.mIsOnline && !remotePlayer.mServerId.equals(getServerId())) {
			MMLog.warning(() -> "Refusing to unregister player " + player.getName() + ": they are on another shard");
			refreshRemotePlayer(player.getUniqueId());
			return;
		}
		RemotePlayerMinecraft localPlayer = fromLocal(player, false);
		if (updateLocalPlayer(localPlayer, false, true)) {
			localPlayer.broadcast();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void playerChangedWorldEvent(PlayerChangedWorldEvent event) {
		Player player = event.getPlayer();
		refreshLocalPlayer(player, false);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		switch (event.getChannel()) {
			case REMOTE_PLAYER_UPDATE_CHANNEL: {
				@Nullable JsonObject data = event.getData();
				if (!Objects.equals(event.getSource(), getServerId())) {
					if (data == null) {
						MMLog.severe(() -> "Got " + REMOTE_PLAYER_UPDATE_CHANNEL + " channel with null data");
						return;
					}
					remotePlayerChange(data);
				}
				break;
			}
			case REMOTE_PLAYER_REFRESH_CHANNEL: {
				@Nullable JsonObject data = event.getData();
				remotePlayerRefresh(data);
				break;
			}
			default: {
				break;
			}
		}
	}
}
