package com.playmonumenta.networkchat.commands.chat.server;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.DefaultChannels;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.ChannelWorld;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;

public class ChatServerSetDefaultChannelCommand {
	public static void register() {
		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			for (String channelType : DefaultChannels.CHANNEL_TYPES) {
				ChatCommand.getBaseCommand()
					.withArguments(new LiteralArgument("server"))
					.withArguments(new LiteralArgument("setdefaultchannel"))
					.withArguments(new MultiLiteralArgument("Channel Type", channelType))
					.executesNative((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.setdefaultchannel")) {
							throw CommandUtils.fail(sender, "You do not have permission to change server-wide default channels.");
						}

						return ChannelManager.getDefaultChannels().command(sender, channelType, true);
					})
					.register();

				Argument<String> channelNameArg;
				if (channelType.equals("default")) {
					channelNameArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN);
				} else if (channelType.equals(DefaultChannels.WORLD_CHANNEL)) {
					channelNameArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN
						.and(ChannelPredicate.channelType(ChannelWorld.CHANNEL_CLASS_ID)));
				} else {
					channelNameArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN
						.and(ChannelPredicate.channelType(channelType)));
				}

				ChatCommand.getBaseCommand()
					.withArguments(new LiteralArgument("server"))
					.withArguments(new LiteralArgument("setdefaultchannel"))
					.withArguments(new MultiLiteralArgument("Channel Type", channelType))
					.withArguments(channelNameArg)
					.executesNative((sender, args) -> {
						if (!CommandUtils.hasPermission(sender, "networkchat.setdefaultchannel")) {
							throw CommandUtils.fail(sender, "You do not have permission to change server-wide default channels.");
						}

						int result = ChannelManager.getDefaultChannels().command(sender, channelType, args.getByArgument(channelNameArg));
						ChannelManager.saveDefaultChannels();
						return result;
					})
					.register();
			}
		}
	}
}
