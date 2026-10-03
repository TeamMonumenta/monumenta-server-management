package com.playmonumenta.common.ext;

import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public record PluginScheduler(Plugin plugin) {
	public void run(Consumer<BukkitTask> task) {
		Bukkit.getScheduler().runTask(plugin, task);
	}
}
