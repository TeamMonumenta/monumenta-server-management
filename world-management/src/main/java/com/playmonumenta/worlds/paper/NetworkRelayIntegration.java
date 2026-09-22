package com.playmonumenta.worlds.paper;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkrelay.DestOfflineEvent;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.networkrelay.shardhealth.AverageShardHealthDataAddSampleEvent;
import com.playmonumenta.networkrelay.shardhealth.AverageShardHealthDataDivideSamplesEvent;
import com.playmonumenta.networkrelay.shardhealth.GatherShardHealthDataEvent;
import com.playmonumenta.networkrelay.shardhealth.GetPluginHealthFactorsEvent;
import com.playmonumenta.worlds.common.MMLog;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class NetworkRelayIntegration implements Listener {
	public static final String PLUGIN_IDENTIFIER = "MonumentaWorldManagement";
	public static final String PREGEN_PROGRESS_KEY = "pregenProgress";
	public static final String CONTENT_CHANNEL = "MonumentaWorldManagementContent";
	public static final String CONTENT_REQUEST_CHANNEL = "MonumentaWorldManagementContentRequest";

	public static void broadcastContentRequest() {
		try {
			NetworkRelayAPI.sendBroadcastMessage(CONTENT_REQUEST_CHANNEL, new JsonObject());
		} catch (Exception ex) {
			MMLog.warning("Failed to broadcast content request", ex);
		}
	}

	public static void broadcastContentChange() {
		sendContentChange("*");
	}

	public static void sendContentChange(String destination) {
		Gson gson = new Gson();
		JsonObject contentInfoConfig = new JsonObject();
		WorldManagementPlugin.getContentInfo()
			.forEach((key, value) -> contentInfoConfig.add(key, gson.toJsonTree(value)));

		try {
			NetworkRelayAPI.sendMessage(destination, CONTENT_CHANNEL, contentInfoConfig);
		} catch (Exception ex) {
			MMLog.warning("Failed to broadcast content config", ex);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		switch (event.getChannel()) {
			case CONTENT_REQUEST_CHANNEL -> sendContentChange(event.getSource());
			case CONTENT_CHANNEL -> WorldManagementPlugin.registerRemoteContent(event.getSource(), event.getData());
			default -> {
			}
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void destOfflineEvent(DestOfflineEvent event) {
		WorldManagementPlugin.unregisterRemoteShard(event.getDest());
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void gatherShardHealthDataEvent(GatherShardHealthDataEvent event) {
		JsonObject pluginHealthData = new JsonObject();
		pluginHealthData.addProperty(PREGEN_PROGRESS_KEY, WorldGenerator.getInstance().progress());
		event.setPluginData(PLUGIN_IDENTIFIER, pluginHealthData);
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void getPluginHealthFactorsEvent(GetPluginHealthFactorsEvent event) {
		if (!PLUGIN_IDENTIFIER.equals(event.pluginIdentifier())) {
			return;
		}

		JsonObject pluginData = event.pluginData();
		if (pluginData == null) {
			return;
		}

		if (
			pluginData.get(PREGEN_PROGRESS_KEY) instanceof JsonPrimitive pregenProgressPrimitive &&
			pregenProgressPrimitive.isNumber()
		) {
			event.includePluginHealthFactor(pregenProgressPrimitive.getAsDouble());
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void averageShardHealthDataAddSampleEvent(AverageShardHealthDataAddSampleEvent event) {
		if (!PLUGIN_IDENTIFIER.equals(event.pluginIdentifier())) {
			return;
		}

		JsonObject sampleData = event.pluginDataSample();
		JsonObject runningTotal = event.runningPluginDataTotal();
		JsonObject sampleCounts = event.pluginDataSampleCounts();

		if (
			sampleData.get(PREGEN_PROGRESS_KEY) instanceof JsonPrimitive pregenProgressSamplePrimitive &&
			pregenProgressSamplePrimitive.isNumber()
		) {
			double pregenProgressSample = pregenProgressSamplePrimitive.getAsDouble();

			if (runningTotal == null) {
				runningTotal = new JsonObject();
			}
			double pregenProgressRunningTotal;
			if (runningTotal.get(PREGEN_PROGRESS_KEY) instanceof JsonPrimitive pregenProgressRunningTotalPrimitive) {
				pregenProgressRunningTotal = pregenProgressRunningTotalPrimitive.getAsDouble() + pregenProgressSample;
			} else {
				pregenProgressRunningTotal = pregenProgressSample;
			}
			runningTotal.addProperty(PREGEN_PROGRESS_KEY, pregenProgressRunningTotal);
			event.runningPluginDataTotal(runningTotal);

			long pregenProgressSampleCount;
			if (sampleCounts.get(PREGEN_PROGRESS_KEY) instanceof JsonPrimitive pregenProgressSampleCountPrimitive) {
				pregenProgressSampleCount = pregenProgressSampleCountPrimitive.getAsLong() + 1L;
			} else {
				pregenProgressSampleCount = 1L;
			}
			sampleCounts.addProperty(PREGEN_PROGRESS_KEY, pregenProgressSampleCount);
		}
	}

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void averageShardHealthDataDivideSamplesEvent(AverageShardHealthDataDivideSamplesEvent event) {
		if (!PLUGIN_IDENTIFIER.equals(event.pluginIdentifier())) {
			return;
		}

		JsonObject totalData = event.pluginDataTotal();
		JsonObject sampleCounts = event.pluginDataSampleCounts();
		JsonObject averagePluginData = new JsonObject();

		if (
			totalData.get(PREGEN_PROGRESS_KEY) instanceof JsonPrimitive pregenProgressTotalPrimitive &&
			pregenProgressTotalPrimitive.isNumber() &&
			sampleCounts.get(PREGEN_PROGRESS_KEY) instanceof JsonPrimitive pregenProgressCountPrimitive &&
			pregenProgressCountPrimitive.isNumber()
		) {
			double pregenProgressTotal = pregenProgressTotalPrimitive.getAsDouble();
			long pregenProgressCount = pregenProgressCountPrimitive.getAsLong();

			double pregenProgressAverage;
			if (pregenProgressCount == 0) {
				pregenProgressAverage = 0.0;
			} else {
				pregenProgressAverage = pregenProgressTotal / pregenProgressCount;
			}

			averagePluginData.addProperty(PREGEN_PROGRESS_KEY, pregenProgressAverage);
		}

		event.averagePluginData(averagePluginData);
	}
}
