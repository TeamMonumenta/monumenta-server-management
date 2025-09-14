package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.interfaces.ChannelAutoJoin;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;

public class ChatChannelAutojoinCommand {
	public static void register() {
		Argument<String> channelArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_MANAGE
			.and(ChannelPredicate.INSTANCE_OF_AUTOJOIN));
		MultiLiteralArgument enableDisableArg = new MultiLiteralArgument("enable/disable", "enable", "disable");

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("channel"))
			.withArguments(new LiteralArgument("autojoin"))
			.withArguments(channelArg)
			.withArguments(new LiteralArgument("get"))
			.executesNative((sender, args) -> {
				String channelName = args.getByArgument(channelArg);
				Channel channel = ChannelManager.getChannel(channelName);

				if (channel == null) {
					throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
				}

				if (!channel.mayManage(sender)) {
					throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
				}

				if (!(channel instanceof ChannelAutoJoin autoJoin)) {
					throw CommandUtils.fail(sender, "This channel has no auto join setting.");
				}

				sender.sendMessage("Channel " + channelName + " auto join: " + (autoJoin.getAutoJoin() ? "enabled." : "disabled."));

				return 1;
			})
			.register();

		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("autojoin"))
				.withArguments(channelArg)
				.withArguments(enableDisableArg)
				.executesNative((sender, args) -> {
					String channelName = args.getByArgument(channelArg);
					Channel channel = ChannelManager.getChannel(channelName);

					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					if (!channel.mayManage(sender)) {
						throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
					}

					if (!(channel instanceof ChannelAutoJoin channelAutoJoin)) {
						throw CommandUtils.fail(sender, "This channel does not support auto join settings.");
					}

					boolean newAutoJoin = args.getByArgument(enableDisableArg).equals("enable");

					channelAutoJoin.setAutoJoin(newAutoJoin);
					ChannelManager.saveChannel(channel);

					sender.sendMessage("Channel " + channelName + " set auto join to " + (newAutoJoin ? "enabled." : "disabled."));

					return 1;
				})
				.register();
		}
	}
}
