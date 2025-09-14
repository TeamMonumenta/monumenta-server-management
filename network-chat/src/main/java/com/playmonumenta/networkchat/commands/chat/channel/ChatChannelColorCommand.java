package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class ChatChannelColorCommand {
	public static void register() {
		Argument<String> channelListenArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN);
		Argument<String> channelManageArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_MANAGE);
		Argument<String> colorArg = new GreedyStringArgument("color").replaceSuggestions(ChatCommand.COLOR_SUGGESTIONS);

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("channel"))
			.withArguments(new LiteralArgument("color"))
			.withArguments(channelListenArg)
			.withArguments(new LiteralArgument("get"))
			.executesNative((sender, args) -> {
				String channelName = args.getByArgument(channelListenArg);
				Channel channel = ChannelManager.getChannel(channelName);

				if (channel == null) {
					throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
				}

				@Nullable TextColor color = channel.color();
				sender.sendMessage(Component.text(channelName + " is " + MessagingUtils.colorToString(color), color));
				return 1;
			})
			.register();

		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("color"))
				.withArguments(channelManageArg)
				.withArguments(new LiteralArgument("clear"))
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.channel.color")) {
						throw CommandUtils.fail(sender, "You do not have permission to change channel colors.");
					}

					String channelName = args.getByArgument(channelManageArg);
					Channel channel = ChannelManager.getChannel(channelName);

					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					if (!channel.mayManage(sender)) {
						throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
					}

					TextColor color = NetworkChatPlugin.messageColor(channel.getClassId());
					channel.color(sender, null);
					ChannelManager.saveChannel(channel);
					sender.sendMessage(Component.text(channelName + " reset to.", color));
					return 1;
				})
				.register();

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("color"))
				.withArguments(channelManageArg)
				.withArguments(new LiteralArgument("set"))
				.withArguments(colorArg)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.channel.color")) {
						throw CommandUtils.fail(sender, "You do not have permission to change channel colors.");
					}

					String channelName = args.getByArgument(channelManageArg);
					Channel channel = ChannelManager.getChannel(channelName);

					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					if (!channel.mayManage(sender)) {
						throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
					}

					String colorString = args.getByArgument(colorArg);
					@Nullable TextColor color = MessagingUtils.colorFromString(colorString);
					if (color == null) {
						throw CommandUtils.fail(sender, "No such color " + colorString);
					}
					channel.color(sender, color);
					ChannelManager.saveChannel(channel);
					sender.sendMessage(Component.text(channelName + " set to " + MessagingUtils.colorToString(color), color));
					return 1;
				})
				.register();
		}
	}
}
