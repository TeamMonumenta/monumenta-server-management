package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.TextArgument;
import java.util.UUID;

public class TestContent {
	public static void register() {
		CommandPermission perms = CommandPermission.fromString("monumenta.command.testcontent");

		Argument<String> playerArg = new TextArgument("player").replaceSuggestions(MonumentaRedisSyncAPI.SUGGESTIONS_ALL_CACHED_PLAYER_NAMES);
		TextArgument valueArg = new TextArgument("value");

		new CommandAPICommand("testcontent")
			.withPermission(perms)
			.withSubcommand(new CommandAPICommand("get")
				.withArguments(playerArg)
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = args.getByArgument(playerArg);
					if (playerNameOrUUID == null) {
						throw CommandAPI.failWithString("Argument must be a player name with correct capitalization or a UUID instead of null");
					}
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							throw CommandAPI.failWithString("Argument must be a player name with correct capitalization or a UUID");
						}
					}
					sender.sendMessage(MonumentaRedisSyncAPI.getPlayerContentDataFromUUID(uuid));
				}))
			.withSubcommand(new CommandAPICommand("set")
				.withArguments(playerArg)
				.withArguments(valueArg)
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = args.getByArgument(playerArg);
					String value = args.getByArgument(valueArg);
					if (playerNameOrUUID == null) {
						throw CommandAPI.failWithString("Argument must be a player name with correct capitalization or a UUID instead of null");
					}
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(playerNameOrUUID);
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							throw CommandAPI.failWithString("Argument must be a player name with correct capitalization or a UUID");
						}
					}
					MonumentaRedisSyncAPI.setPlayerContentDataFromUUID(uuid, value);

				}))
			.register();

	}
}
