package com.playmonumenta.networkrelay.commands;

import com.google.gson.JsonObject;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.TextArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class DebugHeartbeatCommand {
	private static final TextArgument SHARD_ARGUMENT = (TextArgument) new TextArgument("shard")
		.replaceSuggestions(ArgumentSuggestions.strings(
			info -> NetworkRelayAPI.getOnlineShardNames().toArray(String[]::new)
		));
	private static final TextArgument HEARTBEAT_PLUGIN_ARGUMENT = new TextArgument("plugin_identifier");

	public static void registerCommands() {
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("networkRelay")
				.withSubcommand(new CommandAPICommand("debugHeartbeat")
					.withPermission(CommandPermission.fromString("monumenta.networkrelay.debugheartbeat"))
					.withArguments(SHARD_ARGUMENT, HEARTBEAT_PLUGIN_ARGUMENT)
					.executes((sender, args) -> {
						String shardName = args.getByArgument(SHARD_ARGUMENT);
						String pluginIdentifier = args.getByArgument(HEARTBEAT_PLUGIN_ARGUMENT);

						Component header = Component.text("Heartbeat data for " + pluginIdentifier + " on " + shardName + ": ", NamedTextColor.GOLD);

						JsonObject pluginData = NetworkRelayAPI.getHeartbeatPluginData(shardName, pluginIdentifier);
						if (pluginData == null) {
							sender.sendMessage(header.append(Component.text("null", NamedTextColor.RED)));
						} else {
							sender.sendMessage(header.append(Component.text(pluginData.toString(), NamedTextColor.GREEN)));
						}
					})
				)).register();
	}
}
