package com.playmonumenta.networkrelay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkrelay.shardhealth.ShardHealth;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class NetworkRelayAPI {
	public enum ServerType {
		PROXY("proxy"),
		MINECRAFT("minecraft"),
		ALL("all"),
		OTHER("other");

		final String mId;

		ServerType(String id) {
			mId = id;
		}

		@Override
		public String toString() {
			return mId;
		}

		public static ServerType fromString(@Nullable String id) {
			// TODO: remove this in the future - usb
			if ("bungee".equals(id)) {
				return PROXY;
			}
			for (ServerType serverType : values()) {
				if (serverType.toString().equals(id)) {
					return serverType;
				}
			}
			return OTHER;
		}
	}

	public static final String COMMAND_CHANNEL = "monumentanetworkrelay.command";
	public static final String HEARTBEAT_CHANNEL = "monumentanetworkrelay.heartbeat";
	protected static final String NETWORK_RELAY_HEARTBEAT_IDENTIFIER = "monumentanetworkrelay";

	public static void sendMessage(String destination, String channel, JsonObject data) throws Exception {
		getInstance().sendNetworkMessage(destination, channel, data);
	}

	public static void sendBroadcastMessage(String channel, JsonObject data) throws Exception {
		sendMessage("*", channel, data);
	}

	public static void sendCommand(String destination, String command) throws Exception {
		sendCommand(destination, command, ServerType.ALL);
	}

	public static void sendCommand(String destination, String command, ServerType serverType) throws Exception {
		JsonObject data = new JsonObject();
		data.addProperty("server-type", serverType.toString());
		data.addProperty("command", command);
		sendMessage(destination, COMMAND_CHANNEL, data);
	}

	public static void sendBroadcastCommand(String command) throws Exception {
		sendCommand("*", command, ServerType.ALL);
	}

	public static void sendBroadcastCommand(String command, ServerType serverType) throws Exception {
		sendCommand("*", command, serverType);
	}

	public static void sendExpiringMessage(String destination, String channel, JsonObject data, long ttlSeconds) throws Exception {
		getInstance().sendExpiringNetworkMessage(destination, channel, data, ttlSeconds);
	}

	public static void sendExpiringBroadcastMessage(String channel, JsonObject data, long ttlSeconds) throws Exception {
		sendExpiringMessage("*", channel, data, ttlSeconds);
	}

	public static void sendExpiringCommand(String destination, String command, long ttlSeconds) throws Exception {
		sendExpiringCommand(destination, command, ttlSeconds, ServerType.ALL);
	}

	public static void sendExpiringCommand(String destination, String command, long ttlSeconds, ServerType serverType) throws Exception {
		JsonObject data = new JsonObject();
		data.addProperty("server-type", serverType.toString());
		data.addProperty("command", command);
		sendExpiringMessage(destination, COMMAND_CHANNEL, data, ttlSeconds);
	}

	public static void sendExpiringBroadcastCommand(String command, long ttlSeconds) throws Exception {
		sendExpiringCommand("*", command, ttlSeconds, ServerType.ALL);
	}

	public static void sendExpiringBroadcastCommand(String command, long ttlSeconds, ServerType serverType) throws Exception {
		sendExpiringCommand("*", command, ttlSeconds, serverType);
	}

	public static String getShardName() {
		return getInstance().getShardName();
	}

	public static Set<String> getOnlineShardNames() {
		return getInstance().getOnlineShardNames();
	}

	public static Set<String> getOnlineDestinationTypes() {
		return getInstance().getOnlineDestinationTypes();
	}

	public static @Nullable String getOnlineDestinationType(String destination) {
		return getInstance().getOnlineDestinationType(destination);
	}

	public static Set<String> getOnlineDestinationsOfType(String type) {
		return getInstance().getOnlineDestinationsOfType(type);
	}

	public static Set<String> getOnlinePlayerNames() {
		return RemotePlayerAPI.getOnlinePlayerNames();
	}

	public static Set<String> getVisiblePlayerNames() {
		return RemotePlayerAPI.getVisiblePlayerNames();
	}

	public static boolean isPlayerOnline(String playerName) {
		return RemotePlayerAPI.isPlayerOnline(playerName);
	}

	public static boolean isPlayerOnline(UUID playerUuid) {
		return RemotePlayerAPI.isPlayerOnline(playerUuid);
	}

	@Nullable
	public static String getPlayerShard(String playerName) {
		return RemotePlayerAPI.getPlayerShard(playerName);
	}

	@Nullable
	public static String getPlayerShard(UUID playerUuid) {
		return RemotePlayerAPI.getPlayerShard(playerUuid);
	}

	@Nullable
	public static RemotePlayerData getRemotePlayer(String playerName) {
		return RemotePlayerAPI.getRemotePlayer(playerName);
	}

	@Nullable
	public static RemotePlayerData getRemotePlayer(UUID playerUuid) {
		return RemotePlayerAPI.getRemotePlayer(playerUuid);
	}

	public static boolean isPlayerVisible(String playerName) {
		return RemotePlayerAPI.isPlayerVisible(playerName);
	}

	public static boolean isPlayerVisible(UUID playerUuid) {
		return RemotePlayerAPI.isPlayerVisible(playerUuid);
	}

	public static boolean refreshPlayer(UUID playerUuid) {
		return RemotePlayerAPI.refreshPlayer(playerUuid);
	}

	/**
	 * Gets the most recent plugin data provided via heartbeat
	 * <p>
	 * Throws an exception only if the plugin isn't loaded or connected to the network relay
	 *
	 * @param shardName Name of the shard to retrieve data for
	 * @param pluginIdentifier Plugin identifier passed to ShardGatherHeartbeatDataEvent
	 *
	 * @return JsonObject stored in the most recent heartbeat, or null if either shardName or pluginIdentifier not found
	 */
	public static @Nullable JsonObject getHeartbeatPluginData(String shardName, String pluginIdentifier) {
		@Nullable JsonObject allShardData = getInstance().getOnlineShardHeartbeatData().get(shardName);
		if (allShardData != null) {
			JsonElement element = allShardData.get(pluginIdentifier);
			if (element != null && element.isJsonObject()) {
				return element.getAsJsonObject();
			}
		}
		return null;
	}

	public static ShardHealth remoteShardHealth(String shardName) {
		JsonObject remoteNetworkRelayHeartbeatData
			= getHeartbeatPluginData(shardName, NETWORK_RELAY_HEARTBEAT_IDENTIFIER);

		if (
			remoteNetworkRelayHeartbeatData != null &&
				remoteNetworkRelayHeartbeatData.get("shard_health") instanceof JsonObject shardHealthJson
		) {
			return ShardHealth.fromJson(shardHealthJson);
		}

		return ShardHealth.zeroHealth();
	}

	/**
	 * Gets the ping between a proxy and a shard
	 * @param proxy The string ID for the proxy to be checked
	 * @param shard The string ID for the Minecraft shard to be checked
	 * @return The ping in milliseconds between the proxy and the shard, or null if the connection timed out
	 */
	public static @Nullable Long getProxyToShardPingMs(String proxy, String shard) {
		JsonObject proxyHeartbeat = getHeartbeatPluginData(proxy, NETWORK_RELAY_HEARTBEAT_IDENTIFIER);
		if (
			proxyHeartbeat != null
				&& proxyHeartbeat.get("shard-pings") instanceof JsonObject shardPingsJson
				&& shardPingsJson.get(shard) instanceof JsonPrimitive shardPingJson
				&& shardPingJson.isNumber()
		) {
			return shardPingJson.getAsLong();
		}
		return null;
	}

	private static RabbitMQManager getInstance() {
		return RabbitMQManager.getInstance();
	}
}
