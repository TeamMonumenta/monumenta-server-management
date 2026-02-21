package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import net.kyori.adventure.text.Component;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;

public class Stash {
	public static void register() {
		String command = "stash";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.stash");

		/* ******************* stash help ******************* */

		new CommandAPICommand(command)
			.withPermission(perms)
			.executesPlayer((player, args) -> {
				player.sendMessage(Component.text("Usage: "));
				player.sendMessage(Component.text("/stash: Shows this help text."));
				player.sendMessage(Component.text("/stash get [name]: Replaces your current playerdata with the data from the stash. If [name] is unspecified, defaults to your UUID."));
				player.sendMessage(Component.text("/stash put [name]: Saves your current playerdata to the stash, overwriting any existing stash in its place. If [name] is unspecified, defaults to your UUID. Stashes are shared between players!"));
				player.sendMessage(Component.text("/stash info [name]: Lists the player, last saved date, and shard associated with the stash."));
				player.sendMessage(Component.text("/stash list: Lists all existing stashes."));
				player.sendMessage(Component.text("/stash list user [username]: Lists all existing stashes saved by a user. Defaults to your username if not specified. This is checked by username at time of saving, not by UUID, so username changes may affect the result."));
			})
			.register();

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

		/* ******************* stash list ******************* */

		StringArgument usernameArg = new StringArgument("username");

		new CommandAPICommand(command)
			.withArguments(new LiteralArgument("list"))
			.withPermission(perms)
			.executesPlayer((player, args) -> {
				try {
					MonumentaRedisSyncAPI.stashList(player, null);
				} catch (Exception ex) {
					throw CommandAPI.failWithString(ex.getMessage());
				}
			}).register();

		new CommandAPICommand(command)
			.withArguments(new LiteralArgument("list"), new LiteralArgument("user"))
			.withOptionalArguments(usernameArg)
			.withPermission(perms)
			.executesPlayer((player, args) -> {
				try {
					String username = args.getByArgument(usernameArg);
					MonumentaRedisSyncAPI.stashList(player, username != null ? username : player.getName());
				} catch (Exception ex) {
					throw CommandAPI.failWithString(ex.getMessage());
				}
			}).register();
	}
}
