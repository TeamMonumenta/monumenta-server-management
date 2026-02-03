package com.playmonumenta.worlds.paper;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkrelay.shardhealth.GatherShardHealthDataEvent;
import com.playmonumenta.networkrelay.shardhealth.GetPluginHealthFactorsEvent;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class NetworkRelayIntegration implements Listener {
	public static final String PLUGIN_IDENTIFIER = "MonumentaWorldManagement";

	@EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
	public void gatherShardHealthDataEvent(GatherShardHealthDataEvent event) {
		JsonObject pluginHealthData = new JsonObject();
		pluginHealthData.addProperty("pregenProgress", WorldGenerator.getInstance().progress());
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
			pluginData.get("pregenProgress") instanceof JsonPrimitive pregenProgressPrimitive &&
			pregenProgressPrimitive.isNumber()
		) {
			event.includePluginHealthFactor(pregenProgressPrimitive.getAsDouble());
		}
	}
}
