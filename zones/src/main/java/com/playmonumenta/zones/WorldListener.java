package com.playmonumenta.zones;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

public class WorldListener implements Listener {
	private final ZonesPlugin mPlugin;

	public WorldListener(ZonesPlugin plugin) {
		mPlugin = plugin;
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void worldLoadEvent(WorldLoadEvent event) {
		mPlugin.mZoneManager.onLoadWorld(event.getWorld());
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
	public void worldUnloadEvent(WorldUnloadEvent event) {
		mPlugin.mZoneManager.onUnloadWorld(event.getWorld());
	}
}
