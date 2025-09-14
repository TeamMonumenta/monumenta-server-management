package com.playmonumenta.networkrelay;

import java.util.Set;
import java.util.UUID;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.Nullable;

public class RemotePlayerAPI {
	private static @MonotonicNonNull RemotePlayerManagerAbstraction mManager = null;

	public static void init(RemotePlayerManagerAbstraction manager) {
		mManager = manager;
	}

	public static Set<RemotePlayerData> getOnlinePlayers() {
		innerCheckManagerLoaded();
		return mManager.getOnlinePlayers();
	}

	public static Set<RemotePlayerData> getVisiblePlayers() {
		innerCheckManagerLoaded();
		return mManager.getVisiblePlayers();
	}

	public static Set<RemotePlayerData> getOnlinePlayersOnServer(String serverId) {
		innerCheckManagerLoaded();
		return mManager.getOnlinePlayersOnServer(serverId);
	}

	public static Set<RemotePlayerData> getVisiblePlayersOnServer(String serverId) {
		innerCheckManagerLoaded();
		return mManager.getVisiblePlayersOnServer(serverId);
	}

	public static Set<String> getOnlinePlayerNames() {
		innerCheckManagerLoaded();
		return mManager.getOnlinePlayerNames();
	}

	public static Set<String> getVisiblePlayerNames() {
		innerCheckManagerLoaded();
		return mManager.getVisiblePlayerNames();
	}

	public static Set<UUID> getOnlinePlayerUuids() {
		innerCheckManagerLoaded();
		return mManager.getOnlinePlayerUuids();
	}

	public static Set<UUID> getVisiblePlayerUuids() {
		innerCheckManagerLoaded();
		return mManager.getVisiblePlayerUuids();
	}

	public static boolean isPlayerOnline(String playerName) {
		innerCheckManagerLoaded();
		return mManager.isPlayerOnline(playerName);
	}

	public static boolean isPlayerOnline(UUID playerUuid) {
		innerCheckManagerLoaded();
		return mManager.isPlayerOnline(playerUuid);
	}

	public static @Nullable String getPlayerProxy(String playerName) {
		innerCheckManagerLoaded();
		return mManager.getPlayerProxy(playerName);
	}

	public static @Nullable String getPlayerProxy(UUID playerUuid) {
		innerCheckManagerLoaded();
		return mManager.getPlayerProxy(playerUuid);
	}

	public static @Nullable String getPlayerShard(String playerName) {
		innerCheckManagerLoaded();
		return mManager.getPlayerShard(playerName);
	}

	public static @Nullable String getPlayerShard(UUID playerUuid) {
		innerCheckManagerLoaded();
		return mManager.getPlayerShard(playerUuid);
	}

	public static @Nullable RemotePlayerData getRemotePlayer(String playerName) {
		innerCheckManagerLoaded();
		return mManager.getRemotePlayer(playerName);
	}

	public static @Nullable RemotePlayerData getRemotePlayer(UUID playerUuid) {
		innerCheckManagerLoaded();
		return mManager.getRemotePlayer(playerUuid);
	}

	public static boolean isPlayerVisible(String playerName) {
		innerCheckManagerLoaded();
		return mManager.isPlayerVisible(playerName);
	}

	public static boolean isPlayerVisible(UUID playerUuid) {
		innerCheckManagerLoaded();
		return mManager.isPlayerVisible(playerUuid);
	}

	public static boolean refreshPlayer(UUID playerUuid) {
		innerCheckManagerLoaded();
		return mManager.refreshPlayer(playerUuid, false);
	}

	public static boolean isManagerLoaded() {
		return !(mManager == null);
	}

	private static void innerCheckManagerLoaded() {
		if (mManager == null) {
			throw new IllegalStateException("RemotePlayerManager is not loaded");
		}
	}
}
