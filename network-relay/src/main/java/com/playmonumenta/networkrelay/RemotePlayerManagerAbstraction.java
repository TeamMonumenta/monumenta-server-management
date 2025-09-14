package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.util.MMLog;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.jetbrains.annotations.Nullable;

public abstract class RemotePlayerManagerAbstraction {
	public static final int REMOTE_PLAYER_MESSAGE_TTL = 5;
	public static final String REMOTE_PLAYER_CHANNEL_BASE = "monumenta.networkrelay.remote_player";
	public static final String REMOTE_PLAYER_REFRESH_CHANNEL = REMOTE_PLAYER_CHANNEL_BASE + ".refresh";
	public static final String REMOTE_PLAYER_UPDATE_CHANNEL = REMOTE_PLAYER_CHANNEL_BASE + ".update";

	// Fast lookup of player by UUID
	protected static final ConcurrentMap<UUID, RemotePlayerData> mRemotePlayersByUuid = new ConcurrentSkipListMap<>();
	// Fast lookup of player by name
	protected static final ConcurrentMap<String, RemotePlayerData> mRemotePlayersByName = new ConcurrentSkipListMap<>();
	// Required to handle timeout for a given server
	protected static final ConcurrentMap<String, ConcurrentMap<UUID, RemotePlayerData>> mRemotePlayersByServer
		= new ConcurrentSkipListMap<>();
	// Fast lookup of visible players
	protected static final ConcurrentSkipListSet<RemotePlayerData> mVisiblePlayers = new ConcurrentSkipListSet<>();

	protected Set<RemotePlayerData> getOnlinePlayers() {
		return new HashSet<>(mRemotePlayersByUuid.values());
	}

	protected Set<RemotePlayerData> getVisiblePlayers() {
		return new HashSet<>(mVisiblePlayers);
	}

	protected Set<RemotePlayerData> getOnlinePlayersOnServer(String serverId) {
		ConcurrentMap<UUID, RemotePlayerData> result = mRemotePlayersByServer.get(serverId);
		if (result == null) {
			return new HashSet<>();
		} else {
			return new HashSet<>(result.values());
		}
	}

	protected Set<RemotePlayerData> getVisiblePlayersOnServer(String serverId) {
		ConcurrentMap<UUID, RemotePlayerData> playersOnServer = mRemotePlayersByServer.get(serverId);
		if (playersOnServer == null) {
			return new HashSet<>();
		}
		Set<RemotePlayerData> result = new HashSet<>();
		for (RemotePlayerData remotePlayerData : playersOnServer.values()) {
			if (!remotePlayerData.isHidden()) {
				result.add(remotePlayerData);
			}
		}
		return result;
	}

	protected Set<String> getOnlinePlayerNames() {
		return new TreeSet<>(mRemotePlayersByName.keySet());
	}

	protected Set<String> getVisiblePlayerNames() {
		Set<String> result = new TreeSet<>();
		for (RemotePlayerData remotePlayerData : mVisiblePlayers) {
			result.add(remotePlayerData.mName);
		}
		return result;
	}

	protected Set<UUID> getOnlinePlayerUuids() {
		return new HashSet<>(mRemotePlayersByUuid.keySet());
	}

	protected Set<UUID> getVisiblePlayerUuids() {
		Set<UUID> result = new HashSet<>();
		for (RemotePlayerData remotePlayerData : mVisiblePlayers) {
			result.add(remotePlayerData.mUuid);
		}
		return result;
	}

	protected boolean isPlayerOnline(String playerName) {
		return mRemotePlayersByName.containsKey(playerName);
	}

	protected boolean isPlayerOnline(UUID playerUuid) {
		return mRemotePlayersByUuid.containsKey(playerUuid);
	}

	protected boolean isPlayerVisible(String playerName) {
		RemotePlayerData player = mRemotePlayersByName.get(playerName);
		return player != null && !player.isHidden();
	}

	protected boolean isPlayerVisible(UUID playerUuid) {
		RemotePlayerData player = mRemotePlayersByUuid.get(playerUuid);
		return player != null && !player.isHidden();
	}

	protected @Nullable String getPlayerProxy(String playerName) {
		return getPlayerProxy(mRemotePlayersByName.get(playerName));
	}

	protected @Nullable String getPlayerProxy(UUID playerUuid) {
		return getPlayerProxy(mRemotePlayersByUuid.get(playerUuid));
	}

	protected @Nullable String getPlayerProxy(@Nullable RemotePlayerData remotePlayerData) {
		if (remotePlayerData == null) {
			return null;
		}
		RemotePlayerAbstraction proxyData = remotePlayerData.get("proxy");
		if (proxyData == null) {
			return null;
		}
		return proxyData.getServerId();
	}

	protected @Nullable String getPlayerShard(String playerName) {
		return getPlayerShard(mRemotePlayersByName.get(playerName));
	}

	protected @Nullable String getPlayerShard(UUID playerUuid) {
		return getPlayerShard(mRemotePlayersByUuid.get(playerUuid));
	}

	protected @Nullable String getPlayerShard(@Nullable RemotePlayerData remotePlayerData) {
		if (remotePlayerData == null) {
			return null;
		}
		RemotePlayerAbstraction minecraftData = remotePlayerData.get("minecraft");
		if (minecraftData == null) {
			return null;
		}
		return minecraftData.getServerId();
	}

	protected @Nullable RemotePlayerData getRemotePlayer(String playerName) {
		return mRemotePlayersByName.get(playerName);
	}

	protected @Nullable RemotePlayerData getRemotePlayer(UUID playerUuid) {
		return mRemotePlayersByUuid.get(playerUuid);
	}

	protected @Nullable RemotePlayerAbstraction getRemotePlayerProxy(@Nullable RemotePlayerData remotePlayerData) {
		if (remotePlayerData == null) {
			return null;
		}
		return remotePlayerData.get("proxy");
	}

	protected @Nullable RemotePlayerAbstraction getRemotePlayerShard(@Nullable RemotePlayerData remotePlayerData) {
		if (remotePlayerData == null) {
			return null;
		}
		return remotePlayerData.get("minecraft");
	}

	// We received data from another server, add more data
	protected void remotePlayerChange(JsonObject data) {
		if (data == null) {
			MMLog.severe(() -> "Null player data received from an unknown source!");
			return;
		}
		RemotePlayerAbstraction player = RemotePlayerAbstraction.from(data);

		updateLocalPlayer(player, true, false);
	}

	protected void remotePlayerRefresh(JsonObject data) {
		if (data != null && data.has("uuid")) {
			String uuidString = data.get("uuid").getAsString();
			if (!uuidString.equals("*")) {
				UUID uuid = UUID.fromString(uuidString);
				refreshLocalPlayer(uuid, true);
				return;
			}
		}
		onRefreshRequest();
	}

	abstract String getServerType();

	abstract String getServerId();

	abstract boolean refreshPlayer(UUID playerUuid, boolean forceBroadcast);

	/**
	 * Refresh the local player if online
	 * @return true if the player was online, false if not
	 */
	abstract boolean refreshLocalPlayer(UUID playerUuid, boolean forceBroadcast);

	abstract void refreshLocalPlayers(boolean forceBroadcast);

	abstract void refreshLocalPlayerWithDelay(UUID playerUuid);

	// Call respective events on minecraft/proxy platforms
	abstract void callPlayerLoadEvent(RemotePlayerAbstraction player);

	abstract void callPlayerUnloadEvent(RemotePlayerAbstraction player);

	abstract void callPlayerUpdatedEvent(RemotePlayerAbstraction player);

	abstract Map<String, JsonObject> callGatherPluginDataEvent(RemotePlayerAbstraction player);

	abstract void onRefreshRequest();

	/**
	 * Check if this remote player is on our shard
	 * @see #refreshLocalPlayerWithDelay
	 * @return boolean that indicates if the player is online locally
	 */
	abstract boolean playerShouldBeRefreshed(RemotePlayerAbstraction player);

	boolean updateLocalPlayer(RemotePlayerAbstraction player, boolean isRemote) {
		return updateLocalPlayer(player, isRemote, false);
	}

	/**
	 * Update the locally cached player with data, from remote or not
	 * @param player - the player to update data for
	 * @param isRemote - if it originated from another shard/proxy
	 * @param forceBroadcast - force update to broadcast, even if there are no changes, unless the player is on another shard
	 * @see RemotePlayerManagerAbstraction#updatePlayer
	 * @return A boolean indicating if the local player changes should be broadcast to other shards
	 */
	boolean updateLocalPlayer(RemotePlayerAbstraction player, boolean isRemote, boolean forceBroadcast) {
		RemotePlayerData oldPlayerData = getRemotePlayer(player.mUuid);
		String serverType = player.getServerType();
		RemotePlayerAbstraction oldPlayer = oldPlayerData != null ? oldPlayerData.get(serverType) : null;

		// Gather plugin data (if local and online)
		if (!isRemote && player.mIsOnline) {
			player.mPluginData.clear();
			player.mPluginData.putAll(this.callGatherPluginDataEvent(player));
		}

		// Update the player before calling events
		this.updatePlayer(player);

		MMLog.fine(() -> "Old player: " + oldPlayer);
		MMLog.fine(() -> "New player: " + player);

		if (player.mIsOnline && (oldPlayer == null || !oldPlayer.mIsOnline)) {
			this.callPlayerLoadEvent(player);
			MMLog.fine(() -> "Loaded player: " + player.mName + " remote=" + isRemote + " serverType=" + serverType);
			return true;
		}

		boolean shouldBroadcast = false;
		if (!player.mIsOnline && (oldPlayer == null || oldPlayer.mIsOnline)) {
			this.callPlayerUnloadEvent(player);
			MMLog.fine(() -> "Unloaded player: " + player.mName + " remote=" + isRemote + " serverType=" + serverType);
			shouldBroadcast = true;
		} else if (!player.isSimilar(oldPlayer)) {
			this.callPlayerUpdatedEvent(player);
			MMLog.fine(() -> "Updated player: " + player.mName + " remote=" + isRemote + " serverType=" + serverType);
			shouldBroadcast = true;
		} else if (!isRemote && forceBroadcast) {
			// Broadcast local data, regardless of if data changed or not
			MMLog.fine(() -> "Broadcasted player: " + player.mName + " remote=" + isRemote + " serverType=" + serverType);
			shouldBroadcast = true;
		}

		if (isRemote && this.playerShouldBeRefreshed(player)) {
			// Player logged off on remote shard, but is locally online.
			// This can happen if the remote shard was not notified the player logged in here in time.
			MMLog.warning(() -> "Detected race condition, triggering refresh on " + player.mName + " remote=" + isRemote + " serverType=" + serverType);
			this.refreshLocalPlayerWithDelay(player.mUuid);
		}

		return shouldBroadcast;
	}

	protected void updatePlayer(RemotePlayerAbstraction playerServerData) {
		if (playerServerData == null) {
			return;
		}
		MMLog.fine(() -> "Registering player: " + playerServerData);
		String serverType = playerServerData.getServerType();
		String serverId = playerServerData.getServerId();
		UUID playerId = playerServerData.mUuid;
		String playerName = playerServerData.mName;
		boolean isOnline = playerServerData.mIsOnline;
		if (!isOnline) {
			MMLog.fine(() -> "Player is offline instead; unregistering them");
		}

		// Handle UUID/name checks
		RemotePlayerData allPlayerData = mRemotePlayersByUuid.get(playerId);
		if (allPlayerData == null) {
			MMLog.fine(() -> "Player: " + playerName + " was previously offline network-wide");
			if (isOnline) {
				allPlayerData = new RemotePlayerData(playerId, playerName);
				mRemotePlayersByUuid.put(playerId, allPlayerData);
				mRemotePlayersByName.put(playerName, allPlayerData);
			} else {
				MMLog.fine(() -> "Nothing to do!");
				return;
			}
		}

		RemotePlayerAbstraction oldPlayerServerData = allPlayerData.register(playerServerData);
		if (oldPlayerServerData != null) {
			String oldServerId = oldPlayerServerData.getServerId();
			MMLog.fine(() -> "Player: " + playerName + " was previously on server type " + serverType + ", ID " + oldServerId);
			Map<UUID, RemotePlayerData> allRemoteServerPlayerData = mRemotePlayersByServer.get(oldServerId);
			if (allRemoteServerPlayerData != null && (isOnline || oldServerId.equals(serverId))) {
				allRemoteServerPlayerData.remove(oldPlayerServerData.mUuid);
			}
		}
		if (allPlayerData.isHidden()) {
			mVisiblePlayers.remove(allPlayerData);
		} else {
			mVisiblePlayers.add(allPlayerData);
		}
		if (isOnline) {
			mRemotePlayersByServer.computeIfAbsent(serverId, k -> new ConcurrentSkipListMap<>())
				.put(playerId, allPlayerData);
		} else if (!allPlayerData.isOnline()) {
			// Last of that player's info is offline; unregister them completely
			mRemotePlayersByUuid.remove(playerId);
			mRemotePlayersByName.remove(playerName);
		}
	}

	protected boolean registerServer(String serverId) {
		if (mRemotePlayersByServer.containsKey(serverId)) {
			return false;
		}
		MMLog.fine(() -> "Registering server ID " + serverId);
		mRemotePlayersByServer.put(serverId, new ConcurrentSkipListMap<>());
		refreshRemotePlayers(serverId);
		return true;
	}

	protected boolean unregisterServer(String serverId) {
		boolean isRemote = !getServerId().equals(serverId);
		ConcurrentMap<UUID, RemotePlayerData> remotePlayers = mRemotePlayersByServer.remove(serverId);
		if (remotePlayers == null) {
			return false;
		}

		MMLog.fine(() -> "Unregistering server ID " + serverId);
		String serverType = RabbitMQManager.getInstance().getOnlineDestinationType(serverId);
		if (serverType == null) {
			throw new IllegalStateException("ERROR: Server type for server ID cleared before unregistering players from that server: id:" + serverId);
		}
		for (RemotePlayerData allPlayerData : remotePlayers.values()) {
			RemotePlayerAbstraction oldPlayerData = allPlayerData.get(serverType);
			if (oldPlayerData == null) {
				continue;
			}
			updateLocalPlayer(oldPlayerData.asOffline(), isRemote);
		}
		return true;
	}

	protected void refreshRemotePlayer(UUID uuid) {
		JsonObject data = new JsonObject();
		data.addProperty("uuid", uuid.toString());
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(REMOTE_PLAYER_REFRESH_CHANNEL,
				data,
				REMOTE_PLAYER_MESSAGE_TTL);
		} catch (Exception ex) {
			MMLog.severe(() -> "Failed to broadcast to channel " + REMOTE_PLAYER_REFRESH_CHANNEL);
		}
	}

	protected void refreshRemotePlayers() {
		JsonObject data = new JsonObject();
		data.addProperty("uuid", "*");
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(REMOTE_PLAYER_REFRESH_CHANNEL,
				data,
				REMOTE_PLAYER_MESSAGE_TTL);
		} catch (Exception ex) {
			MMLog.severe(() -> "Failed to broadcast to channel " + REMOTE_PLAYER_REFRESH_CHANNEL);
		}
	}

	/**
	 * Request a refresh from a specific shard
	 * @param serverId - shard to ask
	 */
	protected void refreshRemotePlayers(String serverId) {
		JsonObject data = new JsonObject();
		data.addProperty("uuid", "*");
		try {
			NetworkRelayAPI.sendExpiringMessage(serverId, REMOTE_PLAYER_REFRESH_CHANNEL,
				data,
				REMOTE_PLAYER_MESSAGE_TTL);
		} catch (Exception ex) {
			MMLog.severe(() -> "Failed to broadcast to channel " + REMOTE_PLAYER_REFRESH_CHANNEL);
		}
	}

	protected void shutdown() {
		ConcurrentMap<UUID, RemotePlayerData> remotePlayers = mRemotePlayersByServer.get(getServerId());
		if (remotePlayers == null) {
			return;
		}
		String serverType = getServerType();
		for (RemotePlayerData allPlayerData : remotePlayers.values()) {
			RemotePlayerAbstraction oldPlayerData = allPlayerData.get(serverType);
			if (oldPlayerData == null) {
				continue;
			}
			updateLocalPlayer(oldPlayerData.asOffline(), false, true);
		}
	}
}
