package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.arguments.LiteralArgument;

public class ChatReloadCommand {
	public static void register() {
		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("reload"))
			.executesNative((sender, args) -> {
				if (!CommandUtils.hasPermission(sender, "networkchat.reload")) {
					throw CommandUtils.fail(sender, "You do not have permission to reload NetworkChat.");
				}

				NetworkChatPlugin.reload(sender);
			})
			.register();
	}
}
