package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSync;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import java.util.logging.Level;

public class ChangeLogLevel {
	public static void register(MonumentaRedisSync plugin) {
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("redissync")
				.withSubcommand(new CommandAPICommand("changeloglevel")
					.withPermission(CommandPermission.fromString("monumenta.redissync.changeloglevel"))
					.withSubcommand(new CommandAPICommand("INFO")
						.executes((sender, args) -> {
							plugin.setLogLevel(Level.INFO);
						}))
					.withSubcommand(new CommandAPICommand("FINE")
						.executes((sender, args) -> {
							plugin.setLogLevel(Level.FINE);
						}))
					.withSubcommand(new CommandAPICommand("FINER")
						.executes((sender, args) -> {
							plugin.setLogLevel(Level.FINER);
						}))
					.withSubcommand(new CommandAPICommand("FINEST")
						.executes((sender, args) -> {
							plugin.setLogLevel(Level.FINEST);
						}))
				)).register();
	}
}
