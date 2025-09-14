package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

public class Stash {
	public static void register() {
		String command = "stash";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.stash");

		/* ******************* stash put ******************* */

		StringArgument nameArg = new StringArgument("name");

		new CommandAPICommand(command)
			.withArguments(new LiteralArgument("put"))
			.withOptionalArguments(nameArg)
			.withPermission(perms)
			.executes((sender, args) -> {
					if (sender instanceof ProxiedCommandSender proxiedCommandSender) {
						sender = proxiedCommandSender.getCallee();
					}
					if (!(sender instanceof Player player)) {
						throw CommandAPI.failWithString("This command can only be run by/as players");
					}
					try {
						MonumentaRedisSyncAPI.stashPut(player, args.getByArgument(nameArg));
					} catch (Exception ex) {
						throw CommandAPI.failWithString(ex.getMessage());
					}
				}
			).register();


		/* ******************* stash get ******************* */

		new CommandAPICommand(command)
			.withArguments(new LiteralArgument("get"))
			.withPermission(perms)
			.withOptionalArguments(nameArg)
			.executes((sender, args) -> {
					if (sender instanceof ProxiedCommandSender proxiedCommandSender) {
						sender = proxiedCommandSender.getCallee();
					}
					if (!(sender instanceof Player player)) {
						throw CommandAPI.failWithString("This command can only be run by/as players");
					}
					try {
						MonumentaRedisSyncAPI.stashGet(player, args.getByArgument(nameArg));
					} catch (Exception ex) {
						throw CommandAPI.failWithString(ex.getMessage());
					}
				}
			).register();

		/* ******************* stash info ******************* */

		new CommandAPICommand(command)
			.withArguments(new LiteralArgument("info"))
			.withOptionalArguments(nameArg)
			.withPermission(perms)
			.executesPlayer((player, args) -> {
					try {
						MonumentaRedisSyncAPI.stashInfo(player, args.getByArgument(nameArg));
					} catch (Exception ex) {
						throw CommandAPI.failWithString(ex.getMessage());
					}
				}
			).register();
	}
}
