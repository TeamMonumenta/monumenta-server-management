package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import net.md_5.bungee.api.plugin.Event;

public class NetworkRelayMessageEventBungee extends Event {

	private final String mChannel;
	private final String mSource;
	private final JsonObject mData;

	public NetworkRelayMessageEventBungee(String channel, String source, JsonObject data) {
		mChannel = channel;
		mSource = source;
		mData = data;
	}

	public String getChannel() {
		return mChannel;
	}

	public String getSource() {
		return mSource;
	}

	public JsonObject getData() {
		return mData;
	}
}
