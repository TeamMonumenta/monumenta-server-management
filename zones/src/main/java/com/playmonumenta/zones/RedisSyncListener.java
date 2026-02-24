package com.playmonumenta.zones;

import com.playmonumenta.redissync.event.PlayerServerTransferEvent;
import com.playmonumenta.redissync.event.PlayerTransferFailEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class RedisSyncListener implements Listener {
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerServerTransferEvent(PlayerServerTransferEvent event) {
		ZoneManager.getInstance().setTransferring(event.getPlayer(), true);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerTransferFailEvent(PlayerTransferFailEvent event) {
		ZoneManager.getInstance().setTransferring(event.getPlayer(), false);
	}
}
