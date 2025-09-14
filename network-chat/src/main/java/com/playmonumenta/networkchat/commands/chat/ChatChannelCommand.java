package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.commands.chat.channel.ChatChannelAccessCommand;
import com.playmonumenta.networkchat.commands.chat.channel.ChatChannelAutojoinCommand;
import com.playmonumenta.networkchat.commands.chat.channel.ChatChannelColorCommand;
import com.playmonumenta.networkchat.commands.chat.channel.ChatChannelDeleteCommand;
import com.playmonumenta.networkchat.commands.chat.channel.ChatChannelDescriptionCommand;
import com.playmonumenta.networkchat.commands.chat.channel.ChatChannelPermissionCommand;
import com.playmonumenta.networkchat.commands.chat.channel.ChatChannelRenameCommand;
import com.playmonumenta.networkchat.commands.chat.channel.ChatChannelSettingsCommand;

public class ChatChannelCommand {
	public static void register() {
		ChatChannelAccessCommand.register();
		ChatChannelAutojoinCommand.register();
		ChatChannelColorCommand.register();
		ChatChannelDeleteCommand.register();
		ChatChannelDescriptionCommand.register();
		ChatChannelPermissionCommand.register();
		ChatChannelRenameCommand.register();
		ChatChannelSettingsCommand.register();
	}
}
