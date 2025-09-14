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

public class ChatChannelDeleteCommand {
	public static void register() {
		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			Argument<String> channelArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_MANAGE);

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("delete"))
				.withArguments(channelArg)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.delete.channel")) {
						throw CommandUtils.fail(sender, "You do not have permission to delete channels.");
					}

					return deleteChannel(sender, args.getByArgument(channelArg));
				})
				.register();
		}
	}

	private static int deleteChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		// May call throw CommandUtils.fail(sender, )
		ChannelManager.deleteChannel(channelName);
		sender.sendMessage(Component.text("Channel " + channelName + " deleted.", NamedTextColor.GRAY));
		return 1;
	}
}
