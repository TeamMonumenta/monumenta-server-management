package com.playmonumenta.worlds.paper;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.LocationType;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

public class WorldCommands {
	private static final Map<UUID, Integer> INITIAL_VIEW_DISTANCES = new HashMap<>();

	@SuppressWarnings("unchecked")
	public static void register(WorldManagementPlugin worldPlugin) {
		ArgumentSuggestions<CommandSender> cachedWorldNameSuggestions = ArgumentSuggestions.strings((info) -> MonumentaWorldManagementAPI.getCachedAvailableWorlds());

		LocationArgument locationArg = new LocationArgument("location", LocationType.PRECISE_POSITION);
		IntegerArgument valueArg = new IntegerArgument("value", -1, 32);
		Argument<String> cachedWorldNameArg = new StringArgument("worldName").replaceSuggestions(cachedWorldNameSuggestions);
		Argument<String> allWorldNameArg = new StringArgument("worldName").replaceSuggestions(
			ArgumentSuggestions.strings((info) -> Bukkit.getWorlds().stream().map(World::getName).toArray(String[]::new)));
		EntitySelectorArgument.OnePlayer playerArg = new EntitySelectorArgument.OnePlayer("player");
		StringArgument worldNameArg = new StringArgument("worldName");
		StringArgument templateNameArg = new StringArgument("templateName");
		Argument<String> copyFromArg = new StringArgument("copyFromWorldName").replaceSuggestions(cachedWorldNameSuggestions);
		StringArgument newWorldNameArg = new StringArgument("newWorldName");
		EntitySelectorArgument.ManyPlayers targetsArg = new EntitySelectorArgument.ManyPlayers("targets");
		FloatArgument yawArg = new FloatArgument("yaw");
		FloatArgument pitchArg = new FloatArgument("pitch");

		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("worldmanagement")
				.withSubcommand(new CommandAPICommand("changeloglevel")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.changeloglevel"))
					.withSubcommand(new CommandAPICommand("INFO")
						.executes((sender, args) -> {
							worldPlugin.setLogLevel(Level.INFO);
						}))
					.withSubcommand(new CommandAPICommand("FINE")
						.executes((sender, args) -> {
							worldPlugin.setLogLevel(Level.FINE);
						}))
					.withSubcommand(new CommandAPICommand("FINER")
						.executes((sender, args) -> {
							worldPlugin.setLogLevel(Level.FINER);
						}))
					.withSubcommand(new CommandAPICommand("FINEST")
						.executes((sender, args) -> {
							worldPlugin.setLogLevel(Level.FINEST);
						})))
				.withSubcommand(new CommandAPICommand("setviewdistance")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.setviewdistance"))
					.withArguments(locationArg)
					.withArguments(valueArg)
					.executes((sender, args) -> {
						World world = args.getByArgument(locationArg).getWorld();
						int distance = args.getByArgument(valueArg);

						if (distance > 0) {
							if (!INITIAL_VIEW_DISTANCES.containsKey(world.getUID())) {
								INITIAL_VIEW_DISTANCES.put(world.getUID(), world.getViewDistance());
							}

							world.setViewDistance(distance);
							sender.sendMessage("View distance for world '" + world.getName() + "' set to " + distance);
						} else {
							Integer initial = INITIAL_VIEW_DISTANCES.get(world.getUID());
							if (initial == null) {
								sender.sendMessage("Original view distance for world '" + world.getName() + "' unchanged, currently " + world.getViewDistance());
							} else {
								world.setViewDistance(initial);
								sender.sendMessage("View distance for world '" + world.getName() + "' reset to " + initial);
							}
						}
					}))
				.withSubcommand(new CommandAPICommand("listworlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.listworlds"))
					.executes((sender, args) -> {
						sender.sendMessage("Currently loaded worlds:");
						for (World world : Bukkit.getWorlds()) {
							List<String> listOfPlayerNames = world.getPlayers().stream().map(HumanEntity::getName).collect(Collectors.toList());
							sender.sendMessage("  " + world.getName() + ": " + String.join(", ", listOfPlayerNames));
						}
						if (sender instanceof Player player) {
							sender.sendMessage("Current world: " + player.getWorld().getName());
						}
					}))
				.withSubcommand(new CommandAPICommand("loadworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.loadworld"))
					.withArguments(cachedWorldNameArg)
					.executes((sender, args) -> {
						String worldName = args.getByArgument(cachedWorldNameArg);

						try {
							MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, null);
						} catch (Exception ex) {
							throw CommandAPI.failWithString(ex.getMessage());
						}

						sender.sendMessage("Loaded world: " + worldName);
					}))
				.withSubcommand(new CommandAPICommand("unloadworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.unloadworld"))
					.withArguments(allWorldNameArg)
					.executes((sender, args) -> {
						String worldName = args.getByArgument(allWorldNameArg);

						sender.sendMessage("Started unloading world: " + worldName);
						MonumentaWorldManagementAPI.unloadWorld(worldName).whenComplete((unused, ex) -> {
							if (ex != null) {
								sender.sendMessage("Failed to unload world '" + worldName + "': " + ex.getMessage());
							} else {
								sender.sendMessage("Unloaded world '" + worldName + "'");
							}
						});
					}))
				.withSubcommand(new CommandAPICommand("unloadAllEmptyWorlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.unloadallemptyworlds"))
					.executes((sender, args) -> {
						sender.sendMessage("Started unloading empty worlds");
						for (World world : Bukkit.getWorlds()) {
							if (world.getPlayers().isEmpty() && !world.equals(Bukkit.getWorlds().get(0))) {
								String worldName = world.getName();
								MonumentaWorldManagementAPI.unloadWorld(worldName).whenComplete((unused, ex) -> {
									if (ex != null) {
										sender.sendMessage("Failed to unload world '" + worldName + "': " + ex.getMessage());
									} else {
										sender.sendMessage("Unloaded world '" + worldName + "'");
									}
								});
							}
						}
					}))
				.withSubcommand(new CommandAPICommand("forceworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.forceworld"))
					.withArguments(playerArg)
					.withArguments(cachedWorldNameArg)
					.executes((sender, args) -> {
						forceWorld(sender, args.getByArgument(playerArg), args.getByArgument(cachedWorldNameArg));
					}))
				.withSubcommand(new CommandAPICommand("sortworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.sortworld"))
					.withArguments(playerArg)
					.executes((sender, args) -> {
						try {
							MonumentaWorldManagementAPI.sortWorld(args.getByArgument(playerArg));
						} catch (Exception ex) {
							ex.printStackTrace();
							throw CommandAPI.failWithString(ex.getMessage());
						}
					}))
				.withSubcommand(new CommandAPICommand("createworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.createworld"))
					.withArguments(worldNameArg)
					.withArguments(templateNameArg)
					.executes((sender, args) -> {
						String worldName = args.getByArgument(worldNameArg);
						String templateName = args.getByArgument(templateNameArg);

						if (MonumentaWorldManagementAPI.isWorldAvailable(worldName)) {
							sender.sendMessage("World '" + worldName + "' already exists, this command is for creating new worlds");
							return;
						}

						sender.sendMessage("Started creating world '" + worldName + "' from template");
						try {
							MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, templateName);
							sender.sendMessage("Created and loaded world '" + worldName + "' from master copy");
						} catch (Exception ex) {
							sender.sendMessage("Failed to create world '" + worldName + "': " + ex.getMessage());
						}
					}))
				.withSubcommand(new CommandAPICommand("copyworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.copyworld"))
					.withArguments(copyFromArg)
					.withArguments(newWorldNameArg)
					.executes((sender, args) -> {
						String fromWorldName = args.getByArgument(copyFromArg);
						String newWorldName = args.getByArgument(newWorldNameArg);

						sender.sendMessage("Attempting to copy world '" + fromWorldName + "' to '" + newWorldName + "'...");
						MonumentaWorldManagementAPI.copyWorld(fromWorldName, newWorldName).whenComplete((unused, ex) -> {
							if (ex != null) {
								sender.sendMessage(ex.getMessage());
							} else {
								sender.sendMessage("Successfully copied world '" + fromWorldName + "' to '" + newWorldName + "'");
							}
						});
					}))
				.withSubcommand(new CommandAPICommand("deleteworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.deleteworld"))
					.withArguments(cachedWorldNameArg)
					.executes((sender, args) -> {
						String worldName = args.getByArgument(cachedWorldNameArg);

						MonumentaWorldManagementAPI.deleteWorld(worldName).whenComplete((unused, ex) -> {
							if (ex != null) {
								sender.sendMessage("Failed to delete world '" + worldName + "': " + ex.getMessage());
							} else {
								sender.sendMessage("Deleted world '" + worldName + "'");
							}
						});
					}))
				// Upgrade all worlds, no arguments
				.withSubcommand(new CommandAPICommand("upgradeallworlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.upgradeworlds"))
					// This command can only be called via the console to protect against accidental use
					.executesConsole((sender, args) -> {
						// Get a list of all worldNames in alphabetical order
						List<String> worldNames = Arrays.asList(MonumentaWorldManagementAPI.getAvailableWorlds());
						worldNames.sort(String::compareTo);

						// Upgrade them
						upgradeWorlds(worldNames);
					}))
				// Upgrade all worlds, starting with the one specified
				.withSubcommand(new CommandAPICommand("upgradeallworlds")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.upgradeworlds"))
					.withArguments(cachedWorldNameArg)
					// This command can only be called via the console to protect against accidental use
					.executesConsole((sender, args) -> {
						String startWorld = args.getByArgument(cachedWorldNameArg);

						// Get a list of all worldNames in alphabetical order
						List<String> worldNames = Arrays.asList(MonumentaWorldManagementAPI.getAvailableWorlds());
						worldNames.sort(String::compareTo);

						// Trim off any that come before the user-supplied argument
						int startIndex = worldNames.indexOf(startWorld);
						if (startIndex < 0) {
							sender.sendMessage("Invalid world '" + startWorld + "'");
							return;
						}
						worldNames = worldNames.subList(startIndex, worldNames.size());

						// Upgrade them
						upgradeWorlds(worldNames);
					}))
				// Upgrade a specific world
				.withSubcommand(new CommandAPICommand("upgradeworld")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.upgradeworld"))
					.withArguments(cachedWorldNameArg)
					// This command can only be called via the console to protect against accidental use
					.executesConsole((sender, args) -> {
						String worldName = args.getByArgument(cachedWorldNameArg);
						if (!MonumentaWorldManagementAPI.isWorldAvailable(worldName)) {
							sender.sendMessage("Invalid world '" + worldName + "'");
							return;
						}

						// Upgrade
						upgradeWorlds(List.of(worldName));
					}))
				.withSubcommand(new CommandAPICommand("reload")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.reload"))
					.executes((sender, args) -> {
						WorldManagementPlugin.getInstance().reload();
						sender.sendMessage(Component.text("Finished reloading config; world generation restarted if possible."));
					}))
				.withSubcommand(new CommandAPICommand("stopgeneration")
					.withPermission(CommandPermission.fromString("monumenta.worldmanagement.stopgeneration"))
					.executes((sender, args) -> {
						WorldManagementPlugin.getInstance().getWorldGenerator().cancelGeneration(true);
						sender.sendMessage(Component.text("World generation stopped. Reload config to restart."));
					}))
			).register();


		// Register a copy of "/monumenta worldmanagement forceworld @s world" as "/world <world>" for convenience
		new CommandAPICommand("world")
			.withPermission(CommandPermission.fromString("monumenta.worldmanagement.forceworld"))
			.withArguments(cachedWorldNameArg)
			.executesPlayer((player, args) -> {
				forceWorld(player, player, args.getByArgument(cachedWorldNameArg));
			})
			.register();

		// Register a copy of "/monumenta worldmanagement listworlds" as "/worlds" for convenience
		new CommandAPICommand("worlds")
			.withPermission(CommandPermission.fromString("monumenta.worldmanagement.listworlds"))
			.executes((sender, args) -> {
				sender.sendMessage("Currently loaded worlds:");
				for (World world : Bukkit.getWorlds()) {
					List<String> listOfPlayerNames = world.getPlayers().stream().map(HumanEntity::getName).collect(Collectors.toList());
					sender.sendMessage("  " + world.getName() + ": " + String.join(", ", listOfPlayerNames));
				}
				if (sender instanceof Player player) {
					sender.sendMessage(Component.text("Current world: ").append(Component.text(player.getWorld().getName(), NamedTextColor.AQUA)));
				}
			})
			.register();

		new CommandAPICommand("tptoworld")
			.withPermission(CommandPermission.fromString("monumenta.worldmanagement.tptoworld"))
			.withArguments(targetsArg)
			.withArguments(cachedWorldNameArg)
			.withArguments(locationArg)
			.withOptionalArguments(yawArg)
			.withOptionalArguments(pitchArg)
			.executes((sender, args) -> {
				try {
					teleportToWorld(args.getByArgument(targetsArg), args.getByArgument(cachedWorldNameArg), args.getByArgument(locationArg), args.getByArgumentOrDefault(yawArg, 0f), args.getByArgumentOrDefault(pitchArg, 0f));
				} catch (Exception e) {
					e.printStackTrace();
				}
			})
			.register();
	}

	public static void teleportToWorld(Collection<Entity> targets, String world, Location loc, float yaw, float pitch) throws Exception {
		World actualWorld = MonumentaWorldManagementAPI.ensureWorldLoaded(world, null);
		Location newLoc = new Location(actualWorld, loc.getX(), loc.getY(), loc.getZ(), yaw, pitch);
		for (Entity player : targets) {
			player.teleport(newLoc);
		}
	}

	private static void forceWorld(CommandSender sender, Player player, String worldName) {
		// Important - need to save the player's location data on the existing world
		player.saveData();
		Bukkit.getScheduler().runTaskLater(WorldManagementPlugin.getInstance(), () -> {
			try {
				World newWorld = MonumentaWorldManagementAPI.ensureWorldLoaded(worldName, null);

				MonumentaRedisSyncAPI.getPlayerWorldData(player, newWorld).applyToPlayer(player);
			} catch (Exception ex) {
				sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED));
				ex.printStackTrace();
			}
		}, 1);

		player.sendMessage("Loaded world '" + worldName + "' and moved to it");
	}

	/**
	 * Loads all chunks of input world names.
	 * <p>
	 * For each specified world name:
	 * - Load world,
	 * - Wait for completion
	 * - Unload world (continue on failure)
	 * <p>
	 * Operates on one world at a time. Input world names should be validated beforehand
	 */
	private static void upgradeWorlds(List<String> worldNamesIn) {
		// Copy the input list so it can be modified locally
		List<String> worldNames = new ArrayList<>(worldNamesIn);

		Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> {
			Logger log = WorldManagementPlugin.getInstance().getLogger();
			int numUpgraded = worldNames.size();
			log.info("Started upgrading " + numUpgraded + " worlds...");
			log.info("NOTE: This will only do something useful if server was started with -Ddisable.watchdog=true -jar <jarname> --forceUpgrade --eraseCache");

			new BukkitRunnable() {
				@Nullable String mLastWorldName = null;
				long mStartTime = 0;

				@Override
				public void run() {
					// If there was a last world, unload it
					if (mLastWorldName != null) {
						log.info("Finished upgrading world '" + mLastWorldName + "'");
						log.fine(() -> "Upgrading took " + (System.currentTimeMillis() - mStartTime) + " milliseconds");
						MonumentaWorldManagementAPI.unloadWorld(mLastWorldName).whenComplete((unused, ex) -> {
							if (ex != null) {
								log.severe("Failed to unload world '" + mLastWorldName + "': " + ex.getMessage());
							} else {
								log.info("Unloaded world '" + mLastWorldName + "'");
							}
						});
					}

					if (worldNames.isEmpty()) {
						// Done, exit
						log.info("Completed upgrading " + numUpgraded + " worlds");
						this.cancel();
						return;
					}

					// Not done, keep processing
					mLastWorldName = worldNames.remove(0);
					mStartTime = System.currentTimeMillis();
					log.info("Started upgrading world '" + mLastWorldName + "'");

					// Load world
					try {
						MonumentaWorldManagementAPI.ensureWorldLoaded(mLastWorldName, null);
					} catch (Exception ex) {
						// Severe error, fail and stop loading new worlds so it doesn't get missed
						log.severe("Failed to load world '" + mLastWorldName + "': " + ex.getMessage());
						this.cancel();
					}
				}
			}.runTaskTimer(WorldManagementPlugin.getInstance(), 0, 5);
		});
	}
}
