package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.DefaultChannels;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.ChannelWorld;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerSetDefaultChannelCommand {
	public static void register() {
		for (String channelType : DefaultChannels.CHANNEL_TYPES) {
			Argument<String> channelNameArg;
			if (channelType.equals("default") || channelType.equals(DefaultChannels.GUILD_CHANNEL)) {
				channelNameArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_CHAT);
			} else if (channelType.equals(DefaultChannels.WORLD_CHANNEL)) {
				channelNameArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_CHAT
					.and(ChannelPredicate.channelType(ChannelWorld.CHANNEL_CLASS_ID)));
			} else {
				channelNameArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_CHAT
					.and(ChannelPredicate.channelType(channelType)));
			}

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("player"))
				.withArguments(new LiteralArgument("setdefaultchannel"))
				.withArguments(new LiteralArgument(channelType))
				.executesNative((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						if (CommandUtils.checkSudoCommandDisallowed(sender)) {
							throw CommandUtils.fail(sender, "You may not change other players' default channel on this shard.");
						}

						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}

						return state.defaultChannels().command(sender, channelType);
					}
				})
				.register();

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("player"))
				.withArguments(new LiteralArgument("setdefaultchannel"))
				.withArguments(new LiteralArgument(channelType))
				.withArguments(channelNameArg)
				.executesNative((sender, args) -> {
					CommandSender callee = CommandUtils.getCallee(sender);
					if (!(callee instanceof Player target)) {
						throw CommandUtils.fail(sender, "This command can only be run as a player.");
					} else {
						if (CommandUtils.checkSudoCommandDisallowed(sender)) {
							throw CommandUtils.fail(sender, "You may not change other players' default channel on this shard.");
						}

						PlayerState state = PlayerStateManager.getPlayerState(target);
						if (state == null) {
							throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
						}
						return state.defaultChannels().command(sender, channelType, args.getByArgument(channelNameArg));
					}
				})
				.register();
		}
	}
}
