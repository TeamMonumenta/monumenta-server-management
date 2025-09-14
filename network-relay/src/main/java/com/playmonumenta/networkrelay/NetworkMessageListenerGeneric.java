package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public class NetworkMessageListenerGeneric {
	private final String mServerType;

	protected NetworkMessageListenerGeneric(BiConsumer<Integer, Consumer<Object>> registerEventMethod,
	                                        String serverType) {
		mServerType = serverType;

		registerEventMethod.accept(0, (Object event) -> {
			if (event instanceof GatherHeartbeatDataEventGeneric) {
				gatherHeartbeatData((GatherHeartbeatDataEventGeneric) event);
			}
		});
	}

	// EventHandler(priority = EventPriority.NORMAL)
	public void gatherHeartbeatData(GatherHeartbeatDataEventGeneric event) {
		JsonObject data = new JsonObject();
		data.addProperty("server-type", mServerType);
		event.setPluginData(NetworkRelayAPI.NETWORK_RELAY_HEARTBEAT_IDENTIFIER, data);
	}
}
