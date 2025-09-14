package com.playmonumenta.networkchatexample;

import com.playmonumenta.networkrelay.NetworkRelayAPI;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class ChatListener implements Listener {
	private final boolean mJoinMessagesEnabled;

	public ChatListener(boolean joinMessagesEnabled) {
		mJoinMessagesEnabled = joinMessagesEnabled;
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerJoinEvent(PlayerJoinEvent event) throws Exception {
		String message = event.getJoinMessage();

		if (mJoinMessagesEnabled && message != null && !message.isEmpty()) {
			NetworkRelayAPI.sendBroadcastCommand("tellraw @a \"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
		}
		event.setJoinMessage("");
	}

	@EventHandler(priority = EventPriority.LOW)
	public void playerQuitEvent(PlayerQuitEvent event) throws Exception {
		String message = event.getQuitMessage();

		if (mJoinMessagesEnabled && message != null && !message.isEmpty()) {
			NetworkRelayAPI.sendBroadcastCommand("tellraw @a \"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
		}
		event.setQuitMessage("");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void playerDeathEvent(PlayerDeathEvent event) throws Exception {
		String message = event.getDeathMessage();

		if (message != null && !message.isEmpty()) {
			NetworkRelayAPI.sendBroadcastCommand("tellraw @a \"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"");
		}
		event.setDeathMessage("");
	}
}
