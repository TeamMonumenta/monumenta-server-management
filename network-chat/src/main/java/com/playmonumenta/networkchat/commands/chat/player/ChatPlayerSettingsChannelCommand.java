package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.property.ChannelSettings;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerSettingsChannelCommand {
	public static void register() {
		Argument<String> channelArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN);
		MultiLiteralArgument keyArg = new MultiLiteralArgument("key", ChannelSettings.getFlagKeys());
		MultiLiteralArgument valueArg = new MultiLiteralArgument("value", ChannelSettings.getFlagValues());

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("player"))
			.withArguments(new LiteralArgument("settings"))
			.withArguments(new LiteralArgument("channel"))
			.withArguments(channelArg)
			.withArguments(keyArg)
			.executesNative((sender, args) -> {
				CommandSender callee = CommandUtils.getCallee(sender);
				if (!(callee instanceof Player target)) {
					throw CommandUtils.fail(sender, "This command can only be run as a player.");
				} else {
					PlayerState state = PlayerStateManager.getPlayerState(target);
					if (state == null) {
						throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
					}

					String channelName = args.getByArgument(channelArg);
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					ChannelSettings settings = state.channelSettings(channel);
					return settings.commandFlag(sender, args.getByArgument(keyArg));
				}
			})
			.register();

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("player"))
			.withArguments(new LiteralArgument("settings"))
			.withArguments(new LiteralArgument("channel"))
			.withArguments(channelArg)
			.withArguments(keyArg)
			.withArguments(valueArg)
			.executesNative((sender, args) -> {
				CommandSender callee = CommandUtils.getCallee(sender);
				if (!(callee instanceof Player target)) {
					throw CommandUtils.fail(sender, "This command can only be run as a player.");
				} else {
					if (CommandUtils.checkSudoCommandDisallowed(sender)) {
						throw CommandUtils.fail(sender, "You may not edit other player's settings on this shard.");
					}

					PlayerState state = PlayerStateManager.getPlayerState(target);
					if (state == null) {
						throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
					}

					String channelName = args.getByArgument(channelArg);
					Channel channel = ChannelManager.getChannel(channelName);
					if (channel == null) {
						throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
					}

					ChannelSettings settings = state.channelSettings(channel);
					return settings.commandFlag(sender, args.getByArgument(keyArg), args.getByArgument(valueArg));
				}
			})
			.register();
	}
}
