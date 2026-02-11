package com.playmonumenta.worlds.paper;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkrelay.shardhealth.AverageShardHealthDataAddSampleEvent;
import com.playmonumenta.networkrelay.shardhealth.AverageShardHealthDataDivideSamplesEvent;
import com.playmonumenta.networkrelay.shardhealth.GatherShardHealthDataEvent;
import com.playmonumenta.networkrelay.shardhealth.GetPluginHealthFactorsEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class NetworkRelayIntegration implements Listener {
	public static final String PLUGIN_IDENTIFIER = "MonumentaWorldManagement";
	public static final String PREGEN_PROGRESS_KEY = "pregenProgress";

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
