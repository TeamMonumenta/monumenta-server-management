package com.playmonumenta.networkchat.commands.chat.server;

import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.ChannelAnnouncement;
import com.playmonumenta.networkchat.channel.ChannelGlobal;
import com.playmonumenta.networkchat.channel.ChannelLocal;
import com.playmonumenta.networkchat.channel.ChannelParty;
import com.playmonumenta.networkchat.channel.ChannelTeam;
import com.playmonumenta.networkchat.channel.ChannelWhisper;
import com.playmonumenta.networkchat.channel.ChannelWorld;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;

public class ChatServerColorCommand {
	public static void register() {
		MultiLiteralArgument channelTypeArg = new MultiLiteralArgument("Channel Type",
			ChannelAnnouncement.CHANNEL_CLASS_ID,
			ChannelGlobal.CHANNEL_CLASS_ID,
			ChannelLocal.CHANNEL_CLASS_ID,
			ChannelParty.CHANNEL_CLASS_ID,
			ChannelTeam.CHANNEL_CLASS_ID,
			ChannelWhisper.CHANNEL_CLASS_ID,
			ChannelWorld.CHANNEL_CLASS_ID
		);
		Argument<String> colorArg = new GreedyStringArgument("color").replaceSuggestions(ChatCommand.COLOR_SUGGESTIONS);

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("server"))
			.withArguments(new LiteralArgument("color"))
			.withArguments(channelTypeArg)
			.executesNative((sender, args) -> {
				String id = args.getByArgument(channelTypeArg);
				TextColor color = NetworkChatPlugin.messageColor(id);
				sender.sendMessage(Component.text(id + " is " + MessagingUtils.colorToString(color), color));
				return 1;
			})
			.register();

		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("server"))
				.withArguments(new LiteralArgument("color"))
				.withArguments(channelTypeArg)
				.withArguments(colorArg)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.format.default")) {
						throw CommandUtils.fail(sender, "You do not have permission to change channel colors server-wide.");
					}

					String id = args.getByArgument(channelTypeArg);
					String colorString = args.getByArgument(colorArg);
					TextColor color = MessagingUtils.colorFromString(colorString);
					if (color == null) {
						throw CommandUtils.fail(sender, "No such color " + colorString);
					}
					NetworkChatPlugin.messageColor(id, color);
					sender.sendMessage(Component.text(id + " set to " + MessagingUtils.colorToString(color), color));
					return 1;
				})
				.register();
		}
	}
}
