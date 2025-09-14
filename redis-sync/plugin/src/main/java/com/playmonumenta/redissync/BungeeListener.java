package com.playmonumenta.redissync;

import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.UUID;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class BungeeListener implements Listener {
	public static final String uuidToNamePath = "uuid2name";
	public static final String nameToUUIDPath = "name2uuid";

	@EventHandler(priority = EventPriority.HIGHEST)
	public void postLoginEvent(PostLoginEvent event) {
		ProxiedPlayer player = event.getPlayer();

		String name = player.getName();
		UUID uuid = player.getUniqueId();

		RedisAsyncCommands<String, String> async = RedisAPI.getInstance().async();
		async.multi();
		async.hset("uuid2name", uuid.toString(), name);
		async.hset("name2uuid", name, uuid.toString());
		async.exec();
	}
}
