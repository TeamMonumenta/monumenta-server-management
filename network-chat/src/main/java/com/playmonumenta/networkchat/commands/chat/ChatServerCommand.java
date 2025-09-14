package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.commands.chat.server.ChatServerColorCommand;
import com.playmonumenta.networkchat.commands.chat.server.ChatServerFormatCommand;
import com.playmonumenta.networkchat.commands.chat.server.ChatServerMessageVisibilityCommand;
import com.playmonumenta.networkchat.commands.chat.server.ChatServerSetDefaultChannelCommand;

public class ChatServerCommand {
	public static void register() {
		ChatServerColorCommand.register();
		ChatServerFormatCommand.register();
		ChatServerMessageVisibilityCommand.register();
		ChatServerSetDefaultChannelCommand.register();
	}
}
