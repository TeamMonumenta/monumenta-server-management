package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.shardhealth.ShardHealth;
import com.viaversion.viaversion.api.Via;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.jetbrains.annotations.Nullable;

public class NetworkMessageListener implements Listener {
	private final @Nullable String mServerAddress;

	public NetworkMessageListener(@Nullable String serverAddress) {
		mServerAddress = serverAddress;
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void gatherHeartbeatData(GatherHeartbeatDataEvent event) {
		JsonObject data = new JsonObject();
		if (mServerAddress != null && !mServerAddress.isEmpty()) {
			data.addProperty("server-address", mServerAddress);
		}
		data.addProperty("server-type", "minecraft");
		data.add("shard_health", ShardHealth.averageHealth().toJson());
		event.setPluginData(NetworkRelayAPI.NETWORK_RELAY_HEARTBEAT_IDENTIFIER, data);
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
	public void gatherRemotePlayerData(GatherRemotePlayerDataEvent event) {
		JsonObject data = new JsonObject();

		PluginManager pluginManager = Bukkit.getPluginManager();
		if (pluginManager.isPluginEnabled("viaversion")) {
			int protocolVersionNumber = Via.getAPI().getPlayerVersion(event.mRemotePlayer.mUuid);
			data.addProperty("protocol_version", protocolVersionNumber);
		}

		event.setPluginData("networkrelay", data);
	}
}
