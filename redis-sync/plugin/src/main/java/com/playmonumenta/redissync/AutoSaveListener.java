package com.playmonumenta.redissync;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerDataLoadEvent;
import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.config.BukkitConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class AutoSaveListener implements Listener {
	private final Map<UUID, BukkitTask> mPendingSaves = new HashMap<>();

	protected AutoSaveListener(Plugin plugin, VersionAdapter adapter) {
		Logger logger = plugin.getLogger();

		Bukkit.getScheduler().runTaskTimer(plugin, () -> {
			// Create a local copy of the online players list
			List<? extends Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());

			if (players.isEmpty()) {
				// Nothing to do this iteration
				return;
			}

			// Sort the players list by name
			// This helps ensure that a player will be saved consistently about the same time apart
			players.sort(Comparator.comparing((Player a) -> a.getName()));

			// Distribute saves evenly in the autosave interval.
			// This means that at most a player will take two autosave intervals to save
			int delayBetweenSaves = BukkitConfig.getTicksPerPlayerAutosave() / players.size();

			// Remove all the previous iteration saves
			for (Map.Entry<UUID, BukkitTask> entry : mPendingSaves.entrySet()) {
				logger.severe("Player autosave for " + entry.getKey() + " did not complete before next autosave!");
				entry.getValue().cancel();
			}
			mPendingSaves.clear();

			// Schedule an individual save task one-time runnable for each player
			for (int i = 0; i < players.size(); i++) {
				Player player = players.get(i);
				UUID uuid = player.getUniqueId();

				// Schedule this player's save for that later time slot
				BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> {
					try {
						if (player.isOnline() && !DataEventListener.isPlayerTransferring(player)) {
							adapter.savePlayer(player);
						}
					} catch (Exception ex) {
						logger.severe("Failed to autosave player " + player.getName() + ":" + ex.getMessage());
						ex.printStackTrace();
					}

					mPendingSaves.remove(uuid);
				}, (long) i * delayBetweenSaves);

				mPendingSaves.put(uuid, task);
			}
		}, BukkitConfig.getTicksPerPlayerAutosave(), BukkitConfig.getTicksPerPlayerAutosave());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerAdvancementDataLoadEvent(PlayerAdvancementDataLoadEvent event) {
		cancelSaveTask(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerDataLoadEvent(PlayerDataLoadEvent event) {
		cancelSaveTask(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerJoinEvent(PlayerJoinEvent event) {
		cancelSaveTask(event.getPlayer());
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void playerQuitEvent(PlayerQuitEvent event) {
		cancelSaveTask(event.getPlayer());
	}

	private void cancelSaveTask(Player player) {
		BukkitTask cleanupTask = mPendingSaves.remove(player.getUniqueId());
		if (cleanupTask != null && !cleanupTask.isCancelled()) {
			cleanupTask.cancel();
		}
	}
}
