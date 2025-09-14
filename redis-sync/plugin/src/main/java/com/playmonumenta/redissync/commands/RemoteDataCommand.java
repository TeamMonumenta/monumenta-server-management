package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.RemoteDataAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.TextArgument;
import java.util.Map;
import java.util.UUID;
import org.bukkit.plugin.Plugin;

public class RemoteDataCommand {
	@SuppressWarnings("DataFlowIssue")
	public static void register(Plugin plugin) {
		Argument<String> playerArg = new TextArgument("player").replaceSuggestions(MonumentaRedisSyncAPI.SUGGESTIONS_ALL_CACHED_PLAYER_NAMES);
		TextArgument keyArg = new TextArgument("key");
		TextArgument valueArg = new TextArgument("value");

		new CommandAPICommand("remotedata")
			.withPermission(CommandPermission.fromString("monumenta.command.remotedata"))
			.withSubcommand(new CommandAPICommand("getall")
				.withArguments(playerArg)
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = args.getByArgument(playerArg);
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							throw CommandAPI.failWithString("Argument must be a player name with correct capitalization or a UUID");
						}
					}

					String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
					UUID finalUUID = uuid;
					if (name == null) {
						throw CommandAPI.failWithString("Got uuid '" + uuid + "' that matches no known player");
					}

					MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin, RemoteDataAPI.getAll(finalUUID), (data, ex) -> {
						if (ex != null) {
							sender.sendMessage("remoteDataGetAll exception: " + ex.getMessage());
						} else {
							if (data.isEmpty()) {
								sender.sendMessage("Player '" + name + "' has no remote data");
							} else {
								sender.sendMessage("Remote data for player '" + name + "':");
								for (Map.Entry<String, String> entry : data.entrySet()) {
									sender.sendMessage("  '" + entry.getKey() + "':'" + entry.getValue() + "'");
								}
							}
						}
					});
				}))
			.withSubcommand(new CommandAPICommand("get")
				.withArguments(playerArg)
				.withArguments(keyArg)
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = args.getByArgument(playerArg);
					String key = args.getByArgument(keyArg);
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							throw CommandAPI.failWithString("Argument must be a player name with correct capitalization or a UUID");
						}
					}

					String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
					UUID finalUUID = uuid;
					if (name == null) {
						throw CommandAPI.failWithString("Got uuid '" + uuid + "' that matches no known player");
					}

					MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin, RemoteDataAPI.get(finalUUID, key), (data, ex) -> {
						if (ex != null) {
							sender.sendMessage("remoteDataGet exception: " + ex.getMessage());
						} else {
							if (data == null) {
								sender.sendMessage("Key '" + key + "' not set for player '" + name + "'");
							} else {
								sender.sendMessage("Remote data for player '" + name + "'  '" + key + "':'" + data + "'");
							}
						}
					});
				}))
			.withSubcommand(new CommandAPICommand("set")
				.withArguments(playerArg)
				.withArguments(keyArg)
				.withArguments(valueArg)
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = args.getByArgument(playerArg);
					String key = args.getByArgument(keyArg);
					String value = args.getByArgument(valueArg);
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							throw CommandAPI.failWithString("Argument must be a player name with correct capitalization or a UUID");
						}
					}

					String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
					UUID finalUUID = uuid;
					if (name == null) {
						throw CommandAPI.failWithString("Got uuid '" + uuid + "' that matches no known player");
					}

					MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin, RemoteDataAPI.set(finalUUID, key, value), (data, ex) -> {
						if (ex != null) {
							sender.sendMessage("remoteDataSet exception: " + ex.getMessage());
						} else {
							sender.sendMessage("Set remote data for player '" + name + "':");
							sender.sendMessage("  '" + key + "':'" + data + "'");
						}
					});
				}))
			.withSubcommand(new CommandAPICommand("del")
				.withArguments(playerArg)
				.withArguments(keyArg)
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = args.getByArgument(playerArg);
					String key = args.getByArgument(keyArg);
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							throw CommandAPI.failWithString("Argument must be a player name with correct capitalization or a UUID");
						}
					}

					String name = MonumentaRedisSyncAPI.cachedUuidToName(uuid);
					UUID finalUUID = uuid;
					if (name == null) {
						throw CommandAPI.failWithString("Got uuid '" + uuid + "' that matches no known player");
					}

					MonumentaRedisSyncAPI.runOnMainThreadWhenComplete(plugin, RemoteDataAPI.del(finalUUID, key), (data, ex) -> {
						if (ex != null) {
							sender.sendMessage("remoteDataDel exception: " + ex.getMessage());
						} else {
							if (data == null || !data) {
								sender.sendMessage("Key '" + key + "' not set for player '" + name + "'");
							} else {
								sender.sendMessage("Key '" + key + "' deleted for player '" + name + "'");
							}
						}
					});
				}))
			.register();
	}
}
