package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.data.ContentData;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.TextArgument;
import java.util.Objects;
import java.util.UUID;

public class Content {
	public static void register() {
		CommandPermission perms = CommandPermission.fromString("monumenta.command.testcontent");

		Argument<String> playerArg = new TextArgument("player").replaceSuggestions(MonumentaRedisSyncAPI.SUGGESTIONS_ALL_CACHED_PLAYER_NAMES);
		TextArgument valueArg = new TextArgument("value");

		new CommandAPICommand("content")
			.withPermission(perms)
			.withSubcommand(new CommandAPICommand("get")
				.withArguments(playerArg)
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = args.getByArgument(playerArg);
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(Objects.requireNonNull(playerNameOrUUID));
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							throw CommandAPI.failWithString("Argument must be a player name or a UUID");
						}
					}
					String contentId = MonumentaRedisSyncAPI.getPlayerContentData(uuid).getId();
					sender.sendMessage(contentId.isBlank() ? "Content not set" : contentId);
				}))
			.withSubcommand(new CommandAPICommand("set")
				.withArguments(playerArg)
				.withArguments(valueArg)
				.executesPlayer((sender, args) -> {
					String playerNameOrUUID = args.getByArgument(playerArg);
					String value = args.getByArgument(valueArg);
					UUID uuid = MonumentaRedisSyncAPI.cachedNameToUuid(Objects.requireNonNull(playerNameOrUUID));
					if (uuid == null) {
						try {
							uuid = UUID.fromString(playerNameOrUUID);
						} catch (Exception ex) {
							throw CommandAPI.failWithString("Argument must be a player name with correct capitalization or a UUID");
						}
					}
					MonumentaRedisSyncAPI.requestPlayerContentDataChange(uuid, new ContentData(value));
				}))
			.register();

	}
}
