package com.playmonumenta.networkchat.channel.interfaces;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;

public interface ChannelAutoJoin {
	default boolean autoJoinFromJson(JsonObject channelJson) {
		JsonPrimitive autoJoinJson = channelJson.getAsJsonPrimitive("autoJoin");
		if (autoJoinJson != null && autoJoinJson.isBoolean()) {
			return autoJoinJson.getAsBoolean();
		}
		return defaultAutoJoinState();
	}

	default void autoJoinToJson(JsonObject object, boolean autoJoin) {
		object.addProperty("autoJoin", autoJoin);
	}

	boolean defaultAutoJoinState();

	boolean getAutoJoin();

	void setAutoJoin(boolean autoJoin);
}
