package com.playmonumenta.worlds.paper;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.event.PlayerJoinSetWorldEvent;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

public class WorldManagementListener implements Listener {
	private static final String IDENTIFIER = "MonumentaWorldManagementV1";
	private static @Nullable WorldManagementListener INSTANCE = null;

	private @Nullable BukkitTask mUnloadTask = null;
	private final Plugin mPlugin;
	private final Logger mLogger;

	protected WorldManagementListener(Plugin plugin) {
		mPlugin = plugin;
		mLogger = plugin.getLogger();
		INSTANCE = this;
		reloadConfig();
	}

	protected static @Nullable WorldManagementListener getInstance() {
		return INSTANCE;
	}

	/*
	 * Fix player respawn locations being in the overworld when actually instanced
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerRespawnEvent(PlayerRespawnEvent event) {
		if (!WorldManagementPlugin.isSortWorldByScoreOnRespawn()) {
			/* Not instanced, don't modify respawn location */
			return;
		}

		Player player = event.getPlayer();

		ShardInfo info = WorldManagementPlugin.getShardInfo(player);
		if (info == null) {
			mLogger.severe("sort-world-by-score-on-respawn is True but no instancing shard info exists");
			return;
		}

		int score = ScoreboardUtils.getScoreboardValue(player, info.getInstanceObjective()).orElse(0);
		if (score <= 0) {
			player.sendMessage(Component.text("You respawned on an instanced world without an instance assigned to you. Unless you are an operator, this is probably a bug", NamedTextColor.RED));
		} else {
			try {
				/* World should already be loaded, just need to grab it */
				String templateName;
				if (WorldManagementPlugin.allowInstanceAutocreation()) {
					templateName = info.getVariant(player);
				} else {
					templateName = null;
				}
				World world = MonumentaWorldManagementAPI.ensureWorldLoaded(info.getBaseWorldName() + score, templateName);

				// RESPAWN: The player is respawning in this world after having (probably) died there
				if (info.getRespawnInstanceCommand() != null) {
					Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
						// Note that this will run after the player has been moved to the correct world, since it runs a tick later
						if (Bukkit.getOnlinePlayers().contains(player)) {
							mLogger.fine("Running respawn command on player=" + player.getName() + " thread=" + Thread.currentThread().getName());
							Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId() + " at @s run " + info.getRespawnInstanceCommand());
						}
					}, 1);
				}

				if (!event.getRespawnLocation().getWorld().equals(world)) {
					/* Modify the event so the player respawns on this same world at spawn */
					event.setRespawnLocation(world.getSpawnLocation());
				}
			} catch (Exception ex) {
				String msg = "Failed to load your assigned world instance " + score + ": " + ex.getMessage();
				player.sendMessage(msg);
				mLogger.warning(msg);
				ex.printStackTrace();
			}
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerJoinSetWorldEvent(PlayerJoinSetWorldEvent event) {
		Player player = event.getPlayer();
		mLogger.fine("playerJoinSetWorldEvent: player=" + player.getName() + " thread=" + Thread.currentThread().getName());

		if (!WorldManagementPlugin.isSortWorldByScoreOnJoin()) {
			String lastSavedWorldName = event.getLastSavedWorldName();

			if (lastSavedWorldName != null) {
				// If not an instanced server, still try to load the player's last world & put them there
				try {
					World world = MonumentaWorldManagementAPI.ensureWorldLoaded(lastSavedWorldName, null);
					event.setWorld(world);
				} catch (Exception ex) {
					String msg = "Failed to load the last world you were on (" + lastSavedWorldName + "): " + ex.getMessage();
					player.sendMessage(msg);
					mLogger.warning(msg);
					ex.printStackTrace();
				}
			}
		} else {
			try {
				event.setWorld(getSortWorld(player));
			} catch (Exception ex) {
				mLogger.warning("Failed to set world for player " + player.getName() + ": " + ex.getMessage());
			}
		}

		if (WorldManagementPlugin.getNotifyWorldPermission() != null && player.hasPermission(WorldManagementPlugin.getNotifyWorldPermission())) {
			Bukkit.getScheduler().runTaskLater(mPlugin, () -> {
				if (Bukkit.getOnlinePlayers().contains(player)) {
					player.sendMessage(Component.text("Joined world " + event.getWorld().getName(), NamedTextColor.GREEN));
				}
			}, 1);
		}
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		ShardInfo info = WorldManagementPlugin.getShardInfo(player);
		if (info == null) {
			return;
		}
		String instanceObjective = info.getInstanceObjective();
		if (instanceObjective.isEmpty()) {
			return;
		}

		int score = ScoreboardUtils.getScoreboardValue(player, instanceObjective).orElse(0);
		if (score <= 0) {
			return;
		}

		JsonObject pluginData = MonumentaRedisSyncAPI.getPlayerPluginData(player.getUniqueId(), IDENTIFIER);
		boolean firstJoin = true;
		if (pluginData != null) {
			JsonObject lastJoinedWorlds = pluginData.getAsJsonObject("LastJoinedWorlds");
			if (lastJoinedWorlds != null) {
				JsonPrimitive lastJoinedWorldJson = lastJoinedWorlds.getAsJsonPrimitive(instanceObjective);
				if (lastJoinedWorldJson != null && lastJoinedWorldJson.isNumber()) {
					int lastJoinedWorld = lastJoinedWorldJson.getAsInt();
					firstJoin = lastJoinedWorld != score;
				}
			}
		}

		String command;
		if (firstJoin) {
			// JOIN: The player is joining this world for the first time
			command = info.getJoinInstanceCommand();
		} else {
			// REJOIN: The player is joining this world after having most recently left this world
			command = info.getRejoinInstanceCommand();
		}
		if (command != null) {
			mLogger.fine("Running (re)join command on player=" + player.getName() + " thread=" + Thread.currentThread().getName());
			Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), "execute as " + player.getUniqueId() + " at @s run " + command);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
	public void playerSaveEvent(PlayerSaveEvent event) {
		Player player = event.getPlayer();
		ShardInfo info = WorldManagementPlugin.getShardInfo(player);
		UUID playerId = player.getUniqueId();
		String instanceObjective = info == null ? "" : info.getInstanceObjective();
		int score = ScoreboardUtils.getScoreboardValue(player, instanceObjective).orElse(0);

		JsonObject pluginData = MonumentaRedisSyncAPI.getPlayerPluginData(playerId, IDENTIFIER);
		if (pluginData == null) {
			pluginData = new JsonObject();
		}
		JsonObject lastJoinedWorlds = pluginData.getAsJsonObject("LastJoinedWorlds");
		if (lastJoinedWorlds == null) {
			lastJoinedWorlds = new JsonObject();
			pluginData.add("LastJoinedWorlds", lastJoinedWorlds);
		}
		if (!instanceObjective.isEmpty()) {
			if (score <= 0) {
				lastJoinedWorlds.remove(instanceObjective);
			} else {
				lastJoinedWorlds.addProperty(instanceObjective, score);
			}
		}

		event.setPluginData(IDENTIFIER, pluginData);
	}

	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void playerChangedWorldEvent(PlayerChangedWorldEvent event) {
		Player player = event.getPlayer();
		if (WorldManagementPlugin.getNotifyWorldPermission() != null && player.hasPermission(WorldManagementPlugin.getNotifyWorldPermission())) {
			player.sendMessage(Component.text("Changed to world " + player.getLocation().getWorld().getName(), NamedTextColor.GREEN));
		}
	}

	protected void reloadConfig() {
		if (mUnloadTask != null && !mUnloadTask.isCancelled()) {
			mUnloadTask.cancel();
			mUnloadTask = null;
		}

		if (WorldManagementPlugin.getUnloadInactiveWorldAfterTicks() > 0) {
			Map<UUID, Integer> worldIdleTimes = new HashMap<>();

			mUnloadTask = Bukkit.getScheduler().runTaskTimer(mPlugin, () -> {
				List<World> worlds = Bukkit.getWorlds();
				for (int i = 1; i < worlds.size(); i++) { // Ignore the primary world
					World world = worlds.get(i);

					if (!world.getPlayers().isEmpty()) {
						worldIdleTimes.put(world.getUID(), 0);
					} else {
						int idleTime = worldIdleTimes.getOrDefault(world.getUID(), 0) + 200;
						if (idleTime > WorldManagementPlugin.getUnloadInactiveWorldAfterTicks()) {
							mLogger.info("Unloading world '" + world.getName() + "' which has had no players for " + idleTime + " ticks");
							MonumentaWorldManagementAPI.unloadWorld(world.getName()).whenComplete((unused, ex) -> {
								if (ex != null) {
									mLogger.warning("Failed to unload world '" + world.getName() + "': " + ex.getMessage());
								} else {
									worldIdleTimes.remove(world.getUID());
									mLogger.info("Unloaded world " + world.getName());
								}
							});
							// Even though it hasn't unloaded yet, reset its idle time so it won't attempt to unload constantly
							worldIdleTimes.put(world.getUID(), 0);
						} else {
							worldIdleTimes.put(world.getUID(), idleTime);
						}
					}
				}
			}, 200, 200);
		}
	}

	/**
	 * Gets the world where a player should be sorted to based on their instance score.
	 * <p>
	 * Throws an exception if score is negative or the world fails to load. Will trigger instance pregeneration if applicable.
	 * <p>
	 * Will not actually put the player on this world - need to do this and then also set their location data.
	 * <p>
	 * XXX: This should only be called as a precursor to moving the player to this world immediately afterwards on this same tick, otherwise the join/rejoin functions will be called incorrectly!
	 * <p>
	 * Must be called from the main thread
	 */
	protected World getSortWorld(Player player) throws Exception {
		ShardInfo info = WorldManagementPlugin.getShardInfo(player);
		if (info == null) {
			throw new Exception("Tried to get sort world for player but no instancing shard info exists");
		}

		int score = ScoreboardUtils.getScoreboardValue(player, info.getInstanceObjective()).orElse(0);
		if (score == 0) {
			List<World> worlds = Bukkit.getWorlds();
			if (worlds.isEmpty()) {
				throw new Exception("There are no loaded worlds; has the server started?");
			}
			return worlds.get(0);
		} else if (score < 0) {
			throw new Exception("Tried to sort player but instance score is negative");
		}

		String templateName;
		if (WorldManagementPlugin.allowInstanceAutocreation()) {
			templateName = info.getVariant(player);
		} else {
			templateName = null;
		}

		return MonumentaWorldManagementAPI.ensureWorldLoaded(info.getBaseWorldName() + score, templateName);
	}
}
