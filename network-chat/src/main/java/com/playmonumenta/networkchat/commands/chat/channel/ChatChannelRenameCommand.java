package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;

public class ChatChannelRenameCommand {
	public static void register() {
		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			Argument<String> oldChannelArg = ChannelManager.getChannelNameArgument("Old Channel Name", ChannelPredicate.MAY_MANAGE);
			Argument<String> newChannelArg = ChannelManager.getChannelNameArgument("New Channel Name", ChannelPredicate.MAY_MANAGE);

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("rename"))
				.withArguments(oldChannelArg)
				.withArguments(newChannelArg)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.rename")) {
						throw CommandUtils.fail(sender, "You do not have permission to rename channels.");
					}

					return renameChannel(sender, args.getByArgument(oldChannelArg), args.getByArgument(newChannelArg));
				})
				.register();
		}
	}

	private static int renameChannel(CommandSender sender, String oldChannelName, String newChannelName) throws WrapperCommandSyntaxException {
		// May call throw CommandUtils.fail(sender, )
		ChannelManager.renameChannel(oldChannelName, newChannelName);
		sender.sendMessage(Component.text("Channel " + oldChannelName + " renamed to " + newChannelName + ".", NamedTextColor.GRAY));
		return 1;
	}
}
