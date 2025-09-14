package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.property.ChannelSettings;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.FloatArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.SoundArgument;

public class ChatChannelSettingsCommand {
	public static void register() {
		Argument<String> channelArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_MANAGE);
		MultiLiteralArgument keyArg = new MultiLiteralArgument("key", ChannelSettings.getFlagKeys());
		MultiLiteralArgument valueArg = new MultiLiteralArgument("value", ChannelSettings.getFlagValues());
		SoundArgument soundArg = new SoundArgument("Notification sound");
		FloatArgument volumeArg = new FloatArgument("Volume", 0.0f, 1.0f);
		FloatArgument pitchArg = new FloatArgument("Pitch", 0.5f, 2.0f);

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("channel"))
			.withArguments(new LiteralArgument("settings"))
			.withArguments(channelArg)
			.withArguments(keyArg)
			.executesNative((sender, args) -> {
				String channelName = args.getByArgument(channelArg);
				Channel channel = ChannelManager.getChannel(channelName);
				if (channel == null) {
					throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
				}

				ChannelSettings settings = channel.channelSettings();
				return settings.commandFlag(sender, args.getByArgument(keyArg));
			})
			.register();

		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			ChatCommand.getBaseCommand().withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("settings"))
				.withArguments(channelArg)
				.withArguments(keyArg)
				.withArguments(valueArg)
				.executesNative((sender, args) -> {
					String channelName = args.getByArgument(channelArg);
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					if (!channel.mayManage(sender)) {
						throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
					}

					ChannelSettings settings = channel.channelSettings();
					int result = settings.commandFlag(sender, args.getByArgument(keyArg), args.getByArgument(valueArg));
					ChannelManager.saveChannel(channel);
					return result;
				})
				.register();

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("settings"))
				.withArguments(channelArg)
				.withArguments(new LiteralArgument("sound"))
				.withArguments(new LiteralArgument("add"))
				.withArguments(soundArg)
				.withOptionalArguments(volumeArg)
				.withOptionalArguments(pitchArg)
				.executesNative((sender, args) -> {
					String channelName = args.getByArgument(channelArg);
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					if (!channel.mayManage(sender)) {
						throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
					}

					ChannelSettings settings = channel.channelSettings();
					settings.addSound(args.getByArgument(soundArg), args.getByArgumentOrDefault(volumeArg, 1.0f), args.getByArgumentOrDefault(pitchArg, 1.0f));
					ChannelManager.saveChannel(channel);

					return 1;
				})
				.register();

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("settings"))
				.withArguments(channelArg)
				.withArguments(new LiteralArgument("sound"))
				.withArguments(new LiteralArgument("clear"))
				.executesNative((sender, args) -> {
					String channelName = args.getByArgument(channelArg);
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					if (!channel.mayManage(sender)) {
						throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
					}

					ChannelSettings settings = channel.channelSettings();
					settings.clearSound();
					ChannelManager.saveChannel(channel);

					return 1;
				})
				.register();
		}
	}
}
