package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.commands.chat.player.ChatPlayerIgnoreCommand;
import com.playmonumenta.networkchat.commands.chat.player.ChatPlayerProfileMessageCommand;
import com.playmonumenta.networkchat.commands.chat.player.ChatPlayerRefreshCommand;
import com.playmonumenta.networkchat.commands.chat.player.ChatPlayerResetNickCommand;
import com.playmonumenta.networkchat.commands.chat.player.ChatPlayerSetDefaultChannelCommand;
import com.playmonumenta.networkchat.commands.chat.player.ChatPlayerSettingsChannelCommand;
import com.playmonumenta.networkchat.commands.chat.player.ChatPlayerSettingsDefaultCommand;

public class ChatPlayerCommand {
	public static void register() {
		ChatPlayerIgnoreCommand.register();
		ChatPlayerProfileMessageCommand.register();
		ChatPlayerRefreshCommand.register();
		ChatPlayerResetNickCommand.register();
		ChatPlayerSetDefaultChannelCommand.register();
		ChatPlayerSettingsChannelCommand.register();
		ChatPlayerSettingsDefaultCommand.register();
	}
}
