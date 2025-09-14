package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.RemotePlayerListener;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.arguments.LiteralArgument;

public class ChatListPlayersCommand {
	public static void register() {
		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("listplayers"))
			.withPermission("networkchat.listplayers")
			.executesNative((sender, args) -> {
				if (!CommandUtils.hasPermission(sender, "networkchat.listplayers")) {
					throw CommandUtils.fail(sender, "You do not have permission to run this command.");
				}

				RemotePlayerListener.showOnlinePlayers(CommandUtils.getCallee(sender));
				return 1;
			})
			.register();
	}
}
