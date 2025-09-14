package com.playmonumenta.networkchat.commands.chat.server;

import com.playmonumenta.networkchat.MessageVisibility;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;

public class ChatServerMessageVisibilityCommand {
	public static void register() {
		MultiLiteralArgument keysArg = new MultiLiteralArgument("key", MessageVisibility.getVisibilityKeys());
		MultiLiteralArgument valuesArg = new MultiLiteralArgument("values", MessageVisibility.getVisibilityValues());

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("server"))
			.withArguments(new LiteralArgument("messagevisibility"))
			.withArguments(new LiteralArgument("visibility"))
			.withArguments(keysArg)
			.executesNative((sender, args) -> {
				return PlayerStateManager.getDefaultMessageVisibility().commandVisibility(sender, args.getByArgument(keysArg));
			})
			.register();

		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("server"))
				.withArguments(new LiteralArgument("messagevisibility"))
				.withArguments(keysArg)
				.withArguments(valuesArg)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.visibility.default")) {
						throw CommandUtils.fail(sender, "You do not have permission to change server-wide message visibility.");
					}

					int result = PlayerStateManager.getDefaultMessageVisibility().commandVisibility(sender, args.getByArgument(keysArg), args.getByArgument(valuesArg));
					PlayerStateManager.saveSettings();
					return result;
				})
				.register();
		}
	}
}
