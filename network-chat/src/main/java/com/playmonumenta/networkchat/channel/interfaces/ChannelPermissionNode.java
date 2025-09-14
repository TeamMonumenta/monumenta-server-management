package com.playmonumenta.networkchat.channel.interfaces;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import javax.annotation.Nullable;
import org.bukkit.command.CommandSender;

public interface ChannelPermissionNode {
	default @Nullable String permissionFromJson(JsonObject channelJson) {
		JsonPrimitive channelPermissionJson = channelJson.getAsJsonPrimitive("channelPermission");
		if (channelPermissionJson != null && channelPermissionJson.isString()) {
			return channelPermissionJson.getAsString();
		}
		return null;
	}

	default void permissionToJson(JsonObject object, @Nullable String permission) {
		if (permission != null) {
			object.addProperty("channelPermission", permission);
		}
	}

	@Nullable String getChannelPermission();

	boolean hasPermission(CommandSender sender);

	void setChannelPermission(@Nullable String newPerms);
}
