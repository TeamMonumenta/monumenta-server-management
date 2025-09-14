package com.playmonumenta.networkrelay;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import java.util.logging.Level;

public class ChangeLogLevelCommand {
	public static void register(NetworkRelay relayPlugin) {
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("networkRelay")
				.withSubcommand(new CommandAPICommand("changeLogLevel")
					.withPermission(CommandPermission.fromString("monumenta.networkrelay.changeloglevel"))
					.withSubcommand(new CommandAPICommand("INFO")
						.executes((sender, args) -> {
							relayPlugin.setLogLevel(Level.INFO);
						}))
					.withSubcommand(new CommandAPICommand("FINE")
						.executes((sender, args) -> {
							relayPlugin.setLogLevel(Level.FINE);
						}))
					.withSubcommand(new CommandAPICommand("FINER")
						.executes((sender, args) -> {
							relayPlugin.setLogLevel(Level.FINER);
						}))
					.withSubcommand(new CommandAPICommand("FINEST")
						.executes((sender, args) -> {
							relayPlugin.setLogLevel(Level.FINEST);
						}))
			)).register();
	}
}
