package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

public class RabbitMQManagerAbstractionBukkit implements RabbitMQManagerAbstractionInterface {
	private @Nullable BukkitTask mHeartbeatRunnable = null;
	private final Plugin mPlugin;

	protected RabbitMQManagerAbstractionBukkit(Plugin plugin) {
		mPlugin = plugin;
	}

	@Override
	public void startHeartbeatRunnable(Runnable runnable, int delaySeconds, int periodSeconds) {
		mHeartbeatRunnable = Bukkit.getScheduler().runTaskTimer(mPlugin, runnable, delaySeconds * 20L, periodSeconds * 20L);
	}

	@Override
	public void scheduleProcessPacket(Runnable runnable) {
		Bukkit.getScheduler().runTask(mPlugin, runnable);
	}

	@Override
	public void stopHeartbeatRunnable() {
		if (mHeartbeatRunnable != null) {
			mHeartbeatRunnable.cancel();
		}
	}

	@Override
	public void stopServer() {
		Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "stop");
	}

	@Override
	public void sendMessageEvent(String channel, String source, JsonObject data) {
		NetworkRelayMessageEvent event = new NetworkRelayMessageEvent(channel, source, data);
		Bukkit.getPluginManager().callEvent(event);
	}

	@Override
	public JsonObject gatherHeartbeatData() {
		GatherHeartbeatDataEvent event = new GatherHeartbeatDataEvent();
		Bukkit.getPluginManager().callEvent(event);
		return event.getPluginData();
	}

	@Override
	public void sendDestOnlineEvent(String dest) {
		DestOnlineEvent event = new DestOnlineEvent(dest);
		Bukkit.getPluginManager().callEvent(event);
	}

	@Override
	public void sendDestOfflineEvent(String dest) {
		DestOfflineEvent event = new DestOfflineEvent(dest);
		Bukkit.getPluginManager().callEvent(event);
	}
}
