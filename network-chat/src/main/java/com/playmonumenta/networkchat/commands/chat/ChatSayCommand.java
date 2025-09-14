package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.DefaultChannels;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.ChannelWhisper;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatSayCommand {
	public static void register() {
		Argument<String> channelArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_CHAT
			.and(ChannelPredicate.not(ChannelPredicate.channelType(ChannelWhisper.CHANNEL_CLASS_ID))));
		GreedyStringArgument messageArg = new GreedyStringArgument("message");

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("say"))
			.withArguments(channelArg)
			.executesNative((sender, args) -> {
				return setActiveChannel(sender, args.getByArgument(channelArg));
			})
			.register();

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("say"))
			.withArguments(channelArg)
			.withArguments(messageArg)
			.executesNative((sender, args) -> {
				return sendMessage(sender, args.getByArgument(channelArg), args.getByArgument(messageArg));
			})
			.register();

		Set<String> usedShortcuts = new HashSet<>();
		for (String channelType : DefaultChannels.CHANNEL_TYPES) {
			if (channelType.equals(DefaultChannels.DEFAULT_CHANNEL)) {
				continue;
			}

			new CommandAPICommand(channelType)
				.executesNative((sender, args) -> {
					return setActiveToDefault(sender, channelType);
				})
				.register();

			new CommandAPICommand(channelType)
				.withArguments(messageArg)
				.executesNative((sender, args) -> {
					return sendMessageInDefault(sender, channelType, args.getByArgument(messageArg));
				})
				.register();

			String shortcut;
			if (channelType.equals(DefaultChannels.GUILD_CHANNEL)) {
				shortcut = "gc";
			} else if (channelType.equals(DefaultChannels.WORLD_CHANNEL)) {
				shortcut = "wc";
			} else {
				shortcut = channelType.substring(0, 1);
			}
			if (!usedShortcuts.add(shortcut)) {
				continue;
			}

			new CommandAPICommand(shortcut)
				.executesNative((sender, args) -> {
					return setActiveToDefault(sender, channelType);
				})
				.register();

			new CommandAPICommand(shortcut)
				.withArguments(messageArg)
				.executesNative((sender, args) -> {
					return sendMessageInDefault(sender, channelType, args.getByArgument(messageArg));
				})
				.register();
		}
	}

	private static int setActiveChannel(CommandSender sender, String channelName) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			throw CommandUtils.fail(sender, "Only players have an active channel.");
		} else {
			if (CommandUtils.checkSudoCommandDisallowed(sender)) {
				throw CommandUtils.fail(sender, "You may not change other players' active channel on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				throw CommandUtils.fail(sender, "You have no chat state. Please report this bug and reconnect to the server.");
			}

			Channel channel = ChannelManager.getChannel(channelName);
			if (channel == null) {
				throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
			}

			playerState.setActiveChannel(channel);
			player.sendMessage(Component.text("You are now typing in " + channelName + ".", NamedTextColor.GRAY));
			return 1;
		}
	}

	private static int sendMessage(CommandSender sender, String channelName, String message) throws WrapperCommandSyntaxException {
		@Nullable PlayerState playerState = null;
		CommandSender caller = CommandUtils.getCaller(sender);
		CommandSender callee = CommandUtils.getCallee(sender);
		if (NetworkChatProperties.getChatRequiresPlayer()) {
			if (!(caller instanceof Player)) {
				throw CommandUtils.fail(sender, "Only players may chat on this shard.");
			}
		}
		if (callee instanceof Player player) {
			if (callee != caller) {
				throw CommandUtils.fail(sender, "Hey! It's not nice to put words in people's mouths! Where are your manners?");
			}

			playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				throw CommandUtils.fail(player, MessagingUtils.noChatStateStr(player));
			} else if (playerState.isPaused()) {
				throw CommandUtils.fail(player, "You cannot chat with chat paused (/chat unpause)");
			}
		}

		Channel channel = ChannelManager.getChannel(channelName);
		if (channel == null) {
			throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
		}

		if (playerState != null) {
			playerState.joinChannel(channel);
		}
		channel.sendMessage(sender, message);
		return 1;
	}

	private static int setActiveToDefault(CommandSender sender, String channelType) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			throw CommandUtils.fail(sender, "Only players have an active channel.");
		} else {
			if (CommandUtils.checkSudoCommandDisallowed(sender)) {
				throw CommandUtils.fail(sender, "You may not change other players' active channel on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				throw CommandUtils.fail(sender, "You have no chat state. Please report this bug and reconnect to the server.");
			}

			Channel channel = playerState.getDefaultChannel(channelType);
			if (channel == null) {
				throw CommandUtils.fail(sender, "No default for " + channelType + " channel type.");
			}

			playerState.setActiveChannel(channel);
			player.sendMessage(Component.text("You are now typing in " + channel.getName() + ".", NamedTextColor.GRAY));
			return 1;
		}
	}

	private static int sendMessageInDefault(CommandSender sender, String channelType, String message) throws WrapperCommandSyntaxException {
		@Nullable PlayerState playerState = null;
		CommandSender caller = CommandUtils.getCaller(sender);
		CommandSender callee = CommandUtils.getCallee(sender);
		Channel channel;
		if (NetworkChatProperties.getChatRequiresPlayer()) {
			if (!(caller instanceof Player)) {
				throw CommandUtils.fail(sender, "Only players may chat on this shard.");
			}
		}
		if (callee instanceof Player player) {
			if (callee != caller) {
				throw CommandUtils.fail(sender, "Hey! It's not nice to put words in people's mouths! Where are your manners?");
			}

			playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				throw CommandUtils.fail(player, MessagingUtils.noChatStateStr(player));
			} else if (playerState.isPaused()) {
				throw CommandUtils.fail(player, "You cannot chat with chat paused (/chat unpause)");
			} else {
				channel = playerState.getDefaultChannel(channelType);
			}
		} else {
			channel = ChannelManager.getDefaultChannel(channelType);
		}
		if (channel == null) {
			throw CommandUtils.fail(sender, "No default for " + channelType + " channel type.");
		}

		if (playerState != null) {
			playerState.joinChannel(channel);
		}
		channel.sendMessage(sender, message);
		return 1;
	}
}
