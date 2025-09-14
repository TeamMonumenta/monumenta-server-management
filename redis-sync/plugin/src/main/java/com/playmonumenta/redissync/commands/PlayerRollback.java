package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.IntegerArgument;

public class PlayerRollback {
	@SuppressWarnings("DataFlowIssue")
	public static void register() {
		EntitySelectorArgument.OnePlayer playerArg = new EntitySelectorArgument.OnePlayer("player");
		IntegerArgument indexArg = new IntegerArgument("index", 0);

		new CommandAPICommand("playerrollback")
			.withPermission(CommandPermission.fromString("monumenta.command.playerrollback"))
			.withArguments(playerArg)
			.withArguments(indexArg)
			.executesPlayer((sender, args) -> {
					try {
						MonumentaRedisSyncAPI.playerRollback(sender, args.getByArgument(playerArg), args.getByArgument(indexArg));
					} catch (Exception ex) {
						throw CommandAPI.failWithString(ex.getMessage());
					}
				}
			).register();
	}
}
