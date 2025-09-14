package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.util.MMLog;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.scheduler.ScheduledTask;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

public final class RemotePlayerManagerVelocity extends RemotePlayerManagerAbstraction {
	private static final RemotePlayerManagerVelocity INSTANCE = new RemotePlayerManagerVelocity();
	private final ProxyServer mServer;

	public RemotePlayerManagerVelocity() {
		this.mServer = NetworkRelayVelocity.getInstance().mServer;
		String lShard = getServerId();
		try {
			for (String shard : NetworkRelayAPI.getOnlineShardNames()) {
				if (shard.equals(lShard)) {
					continue;
				}
				MMLog.info(() -> "Registering shard " + shard);
				registerServer(shard);
			}
		} catch (Exception ex) {
			MMLog.severe(() -> "Failed to get remote shard names");
			throw new RuntimeException("Failed to get remote shard names", ex);
		}
		onRefreshRequest();
	}

	static RemotePlayerManagerVelocity getInstance() {
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

	static RemotePlayerProxy fromLocal(Player player, boolean isOnline) {
		// player.getServer() has no information prior to the ServerSwitchEvent - we populate the player's information in the PostLoginEvent
		ServerConnection server = player.getCurrentServer().orElse(null);
		@Nullable String targetShard = server != null ? server.getServerInfo().getName() : "";
		return fromLocal(player, isOnline, targetShard);
	}

	static RemotePlayerProxy fromLocal(Player player, boolean isOnline, String targetShard) {
		return new RemotePlayerProxy(
			INSTANCE.getServerId(),
			player.getUniqueId(),
			player.getUsername(),
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

			mRefreshTimer = mServer.getScheduler().buildTask(NetworkRelayVelocity.getInstance(), () -> refreshLocalPlayers(true)).delay(1, TimeUnit.SECONDS).schedule();
		}
	}

	@Override
	void refreshLocalPlayers(boolean forceBroadcast) {
		for (Player player : mServer.getAllPlayers()) {
			refreshLocalPlayer(player, forceBroadcast);
		}
	}

	@Override
	boolean refreshLocalPlayer(UUID uuid, boolean forceBroadcast) {
		@Nullable Player localPlayer = mServer.getPlayer(uuid).orElse(null);
		if (localPlayer != null && localPlayer.isActive()) {
			refreshLocalPlayer(localPlayer, forceBroadcast);
			return true;
		}
		return false;
	}

	// Run this on local players whenever their information is out of date
	void refreshLocalPlayer(Player player, boolean forceBroadcast) {
		// MMLog.fine(() -> "Refreshing local player " + player.getName());
		RemotePlayerProxy localPlayer = fromLocal(player, true);

		// update local player with data
		if (updateLocalPlayer(localPlayer, false, forceBroadcast)) {
			localPlayer.broadcast();
		}
	}

	@Override
	void callPlayerLoadEvent(RemotePlayerAbstraction player) {
		RemotePlayerLoadedEventGeneric remotePE = new RemotePlayerLoadedEventGeneric(player);
		mServer.getEventManager().fireAndForget(remotePE);
	}

	@Override
	void callPlayerUnloadEvent(RemotePlayerAbstraction player) {
		RemotePlayerUnloadedEventGeneric remotePE = new RemotePlayerUnloadedEventGeneric(player);
		mServer.getEventManager().fireAndForget(remotePE);
	}

	@Override
	void callPlayerUpdatedEvent(RemotePlayerAbstraction player) {
		RemotePlayerUpdatedEventGeneric remotePE = new RemotePlayerUpdatedEventGeneric(player);
		mServer.getEventManager().fireAndForget(remotePE);
	}

	@Override
	Map<String, JsonObject> callGatherPluginDataEvent(RemotePlayerAbstraction player) {
		GatherRemotePlayerDataEventVelocity remotePE = new GatherRemotePlayerDataEventVelocity(player);
		try {
			mServer.getEventManager().fire(remotePE).get(5, TimeUnit.SECONDS);
		} catch (Exception ex) {
			MMLog.severe("Timeout for 5 seconds when gathering player plugin data");
			ex.printStackTrace();
		}
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
		@Nullable Player localPlayer = mServer.getPlayer(player.mUuid).orElse(null);
		return localPlayer != null && localPlayer.isActive();
	}

	@Override
	void refreshLocalPlayerWithDelay(UUID uuid) {
		refreshPlayer(uuid, true);
	}

	@Subscribe(order = PostOrder.LAST)
	public void destOnlineEvent(DestOnlineEventGeneric event) {
		String remoteShardName = event.getDest();
		if (getServerId().equals(remoteShardName)) {
			return;
		}
		registerServer(remoteShardName);
	}

	@Subscribe(order = PostOrder.EARLY)
	public void destOfflineEvent(DestOnlineEventGeneric event) {
		String remoteShardName = event.getDest();
		if (getServerId().equals(remoteShardName)) {
			return;
		}
		unregisterServer(remoteShardName);
	}

	// // This is when the player logins into the proxy
	@Subscribe(order = PostOrder.LAST)
	public EventTask playerConnectEvent(PostLoginEvent event) {
		Player player = event.getPlayer();
		return EventTask.async(() -> {
			refreshLocalPlayer(player, true);
		});
	}

	// This is when the player connects or reconnects to a shard on the proxy
	@Subscribe(order = PostOrder.LAST)
	public EventTask playerChangedServerEvent(ServerPostConnectEvent event) {
		Player player = event.getPlayer();
		return EventTask.async(() -> {
			refreshLocalPlayer(player, true);
		});
	}

	// This is when the player disconnects from the proxy
	@Subscribe(order = PostOrder.LAST)
	public @Nullable EventTask playerQuitEvent(DisconnectEvent event) {
		Player player = event.getPlayer();
		// The DisconnectEvent can fire BEFORE PostLoginEvent
		if (!isPlayerOnline(player.getUniqueId())) {
			return null;
		}
		String playerProxy = getPlayerProxy(player.getUniqueId());
		if (playerProxy != null && !playerProxy.equals(getServerId())) {
			MMLog.warning(() -> "Refusing to unregister player " + player.getUsername() + ": they are on another proxy");
			refreshRemotePlayer(player.getUniqueId());
			return null;
		}
		return EventTask.async(() -> {
			RemotePlayerProxy localPlayer = fromLocal(player, false);
			if (updateLocalPlayer(localPlayer, false, true)) {
				localPlayer.broadcast();
			}
		});
	}

	@Subscribe(order = PostOrder.LAST)
	public @Nullable EventTask networkRelayMessageEventGeneric(NetworkRelayMessageEventGeneric event) {
		switch (event.getChannel()) {
			case REMOTE_PLAYER_UPDATE_CHANNEL: {
				@Nullable JsonObject data = event.getData();
				if (!Objects.equals(event.getSource(), getServerId())) {
					if (data == null) {
						MMLog.severe(() -> "Got " + REMOTE_PLAYER_UPDATE_CHANNEL + " channel with null data");
						break;
					}
					return EventTask.async(() -> remotePlayerChange(data));
				}
				break;
			}
			case REMOTE_PLAYER_REFRESH_CHANNEL: {
				@Nullable JsonObject data = event.getData();
				return EventTask.async(() -> {
					remotePlayerRefresh(data);
				});
			}
			default: {
				break;
			}
		}
		return null;
	}
}
