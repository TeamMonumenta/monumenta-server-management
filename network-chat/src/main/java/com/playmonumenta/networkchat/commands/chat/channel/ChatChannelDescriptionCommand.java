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

public class ChatChannelDescriptionCommand {
	public static void register() {
		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			Argument<String> newChannelDescription = ChannelManager.getChannelDescriptionArgument("New Channel Description");
			Argument<String> channelName = ChannelManager.getChannelNameArgument("Channel Name", ChannelPredicate.MAY_MANAGE);

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("description"))
				.withArguments(channelName)
				.withArguments(newChannelDescription)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.channel.description")) {
						throw CommandUtils.fail(sender, "You do not have permission to rename channels.");
					}

					return changeChannelDescription(sender, args.getByArgument(newChannelDescription), args.getByArgument(channelName));
				})
				.register();
		}
	}

	private static int changeChannelDescription(CommandSender sender, String newChannelDescription, String channelName) throws WrapperCommandSyntaxException {
		ChannelManager.changeChannelDescription(newChannelDescription, channelName);
		sender.sendMessage(Component.text("Channel " + channelName + " description updated to " + newChannelDescription, NamedTextColor.GRAY));
		return 1;
	}
}
