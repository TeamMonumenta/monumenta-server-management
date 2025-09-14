package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.util.MMLog;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;
import org.jetbrains.annotations.Nullable;

public final class RemotePlayerManagerBungee extends RemotePlayerManagerAbstraction implements Listener {
	private static final RemotePlayerManagerBungee INSTANCE = new RemotePlayerManagerBungee();

	private RemotePlayerManagerBungee() {
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

	static RemotePlayerManagerBungee getInstance() {
		return INSTANCE;
	}

	@Override
	public String getServerType() {
		return RemotePlayerProxy.SERVER_TYPE;
	}

	@Override
	public String getServerId() {
		@Nullable String shardName = null;
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

	static RemotePlayerProxy fromLocal(ProxiedPlayer player, boolean isOnline) {
		// player.getServer() has no information prior to the ServerSwitchEvent - we populate the player's information in the PostLoginEvent
		@Nullable String targetShard = player.getServer() != null ? player.getServer().getInfo().getName() : "";
		return fromLocal(player, isOnline, targetShard);
	}

	static RemotePlayerProxy fromLocal(ProxiedPlayer player, boolean isOnline, String targetShard) {
		return new RemotePlayerProxy(
			INSTANCE.getServerId(),
			player.getUniqueId(),
			player.getName(),
			isOnline,
			null,
			targetShard
		);
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
	private @Nullable ScheduledTask mRefreshTimer = null;

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

			mRefreshTimer = ProxyServer.getInstance().getScheduler().schedule(NetworkRelayBungee.getInstance(), () -> refreshLocalPlayers(true), 1, TimeUnit.SECONDS);
		}
	}

	@Override
	void refreshLocalPlayers(boolean forceBroadcast) {
		for (ProxiedPlayer player : ProxyServer.getInstance().getPlayers()) {
			refreshLocalPlayer(player, forceBroadcast);
		}
	}

	@Override
	boolean refreshLocalPlayer(UUID uuid, boolean forceBroadcast) {
		@Nullable ProxiedPlayer localPlayer = ProxyServer.getInstance().getPlayer(uuid);
		if (localPlayer != null && localPlayer.isConnected()) {
			refreshLocalPlayer(localPlayer, forceBroadcast);
			return true;
		}
		return false;
	}

	// Run this on local players whenever their information is out of date
	void refreshLocalPlayer(ProxiedPlayer player, boolean forceBroadcast) {
		MMLog.fine(() -> "Refreshing local player " + player.getName());
		RemotePlayerProxy localPlayer = fromLocal(player, true);

		// update local player with data
		if (updateLocalPlayer(localPlayer, false, forceBroadcast)) {
			localPlayer.broadcast();
		}
	}

	@Override
	void callPlayerLoadEvent(RemotePlayerAbstraction player) {
		RemotePlayerLoadedEventBungee remotePE = new RemotePlayerLoadedEventBungee(player);
		ProxyServer.getInstance().getPluginManager().callEvent(remotePE);
	}

	@Override
	void callPlayerUnloadEvent(RemotePlayerAbstraction player) {
		RemotePlayerUnloadedEventBungee remotePE = new RemotePlayerUnloadedEventBungee(player);
		ProxyServer.getInstance().getPluginManager().callEvent(remotePE);
	}

	@Override
	void callPlayerUpdatedEvent(RemotePlayerAbstraction player) {
		RemotePlayerUpdatedEventBungee remotePE = new RemotePlayerUpdatedEventBungee(player);
		ProxyServer.getInstance().getPluginManager().callEvent(remotePE);
	}

	@Override
	Map<String, JsonObject> callGatherPluginDataEvent(RemotePlayerAbstraction player) {
		GatherRemotePlayerDataEventBungee remotePE = new GatherRemotePlayerDataEventBungee(player);
		ProxyServer.getInstance().getPluginManager().callEvent(remotePE);
		return remotePE.getPluginData();
	}

	@Override
	boolean playerShouldBeRefreshed(RemotePlayerAbstraction player) {
		// TODO: NetworkChat only refreshes if the player is offline
		if (player.mIsOnline) {
			return false;
		}
		if (!player.getServerType().equals(RemotePlayerProxy.SERVER_TYPE)) {
			return false;
		}
		@Nullable ProxiedPlayer localPlayer = ProxyServer.getInstance().getPlayer(player.mUuid);
		return localPlayer != null && localPlayer.isConnected();
	}

	@Override
	void refreshLocalPlayerWithDelay(UUID uuid) {
		refreshPlayer(uuid, true);
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void destOnlineEvent(DestOnlineEventBungee event) {
		String remoteShardName = event.getDest();
		if (getServerId().equals(remoteShardName)) {
			return;
		}
		registerServer(remoteShardName);
	}

	@EventHandler(priority = EventPriority.LOW)
	public void destOfflineEvent(DestOfflineEventBungee event) {
		String remoteShardName = event.getDest();
		if (getServerId().equals(remoteShardName)) {
			return;
		}
		unregisterServer(remoteShardName);
	}

	// This is when the player logins into the proxy
	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerConnectEvent(PostLoginEvent event) {
		ProxiedPlayer player = event.getPlayer();
		refreshLocalPlayer(player, true);
	}

	// This is when the player connects or reconnects to a shard on the proxy
	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerChangedServerEvent(ServerSwitchEvent event) {
		ProxiedPlayer player = event.getPlayer();
		refreshLocalPlayer(player, true);
	}

	// This is when the player disconnects from the proxy
	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerQuitEvent(PlayerDisconnectEvent event) {
		ProxiedPlayer player = event.getPlayer();
		String playerProxy = getPlayerProxy(player.getUniqueId());
		if (playerProxy != null && !playerProxy.equals(getServerId())) {
			MMLog.warning(() -> "Refusing to unregister player " + player.getName() + ": they are on another proxy");
			refreshRemotePlayer(player.getUniqueId());
			return;
		}
		RemotePlayerProxy localPlayer = fromLocal(player, false);
		if (updateLocalPlayer(localPlayer, false, true)) {
			localPlayer.broadcast();
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void networkRelayMessageEventBungee(NetworkRelayMessageEventBungee event) {
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
