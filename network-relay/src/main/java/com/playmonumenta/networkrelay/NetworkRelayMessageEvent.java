package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class NetworkRelayMessageEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final String mChannel;
	private final String mSource;
	private final JsonObject mData;

	public NetworkRelayMessageEvent(String channel, String source, JsonObject data) {
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

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
