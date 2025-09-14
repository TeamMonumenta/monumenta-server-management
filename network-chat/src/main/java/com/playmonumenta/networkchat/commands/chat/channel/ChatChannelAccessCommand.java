package com.playmonumenta.networkchat.commands.chat.channel;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.UUID;

public class ChatChannelAccessCommand {
	public static void register() {
		Argument<String> channelArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_MANAGE);
		MultiLiteralArgument keyArg = new MultiLiteralArgument("key", ChannelAccess.getFlagKeys());
		MultiLiteralArgument valueArg = new MultiLiteralArgument("flag", ChannelAccess.getFlagValues());
		Argument<String> nameArg = new StringArgument("name").replaceSuggestions(ChatCommand.ALL_CACHED_PLAYER_NAMES_SUGGESTIONS);

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("channel"))
			.withArguments(new LiteralArgument("access"))
			.withArguments(channelArg)
			.withArguments(new LiteralArgument("default"))
			.withArguments(keyArg)
			.executesNative((sender, args) -> {
				String channelName = args.getByArgument(channelArg);
				Channel channel = ChannelManager.getChannel(channelName);
				if (channel == null) {
					throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
				}

				ChannelAccess perms = channel.channelAccess();
				return perms.commandFlag(sender, args.getByArgument(keyArg));
			})
			.register();

		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("access"))
				.withArguments(channelArg)
				.withArguments(new LiteralArgument("default"))
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

					ChannelAccess perms = channel.channelAccess();
					int result = perms.commandFlag(sender, args.getByArgument(keyArg), args.getByArgument(valueArg));
					ChannelManager.saveChannel(channel);
					return result;
				})
				.register();
		}

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("channel"))
			.withArguments(new LiteralArgument("access"))
			.withArguments(channelArg)
			.withArguments(new LiteralArgument("player"))
			.withArguments(nameArg)
			.withArguments(keyArg)
			.executesNative((sender, args) -> {
				String channelName = args.getByArgument(channelArg);
				Channel channel = ChannelManager.getChannel(channelName);
				if (channel == null) {
					throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
				}
				if (!channel.mayManage(sender)) {
					throw CommandUtils.fail(sender, "You do not have permission to manage channel " + channel.getName() + ".");
				}

				String playerName = args.getByArgument(nameArg);
				UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
				if (playerId == null) {
					throw CommandUtils.fail(sender, "No such player " + playerName + ".");
				}
				ChannelAccess perms = channel.playerAccess(playerId);
				return perms.commandFlag(sender, args.getByArgument(keyArg));
			})
			.register();

		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("channel"))
				.withArguments(new LiteralArgument("access"))
				.withArguments(channelArg)
				.withArguments(new LiteralArgument("player"))
				.withArguments(nameArg)
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

					String playerName = args.getByArgument(nameArg);
					UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
					if (playerId == null) {
						throw CommandUtils.fail(sender, "No such player " + playerName + ".");
					}
					ChannelAccess perms = channel.playerAccess(playerId);
					int result = perms.commandFlag(sender, args.getByArgument(keyArg), args.getByArgument(valueArg));
					if (perms.isDefault()) {
						channel.resetPlayerAccess(playerId);
					}
					ChannelManager.saveChannel(channel);
					return result;
				})
				.register();
		}
	}
}
