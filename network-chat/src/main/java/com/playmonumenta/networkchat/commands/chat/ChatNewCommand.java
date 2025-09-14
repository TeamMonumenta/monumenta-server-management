package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.channel.ChannelAnnouncement;
import com.playmonumenta.networkchat.channel.ChannelGlobal;
import com.playmonumenta.networkchat.channel.ChannelLocal;
import com.playmonumenta.networkchat.channel.ChannelParty;
import com.playmonumenta.networkchat.channel.ChannelTeam;
import com.playmonumenta.networkchat.channel.ChannelWhisper;
import com.playmonumenta.networkchat.channel.ChannelWorld;
import com.playmonumenta.networkchat.commands.ChatCommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import java.util.ArrayList;
import java.util.List;

public class ChatNewCommand {
	public static void register() {
		if (NetworkChatProperties.getChatCommandCreateEnabled()) {
			LiteralArgument newArg = new LiteralArgument("new");
			Argument<String> channelArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN
				.and(ChannelPredicate.not(ChannelPredicate.channelType(ChannelWhisper.CHANNEL_CLASS_ID))));
			ChannelAnnouncement.registerNewChannelCommands(newArg, channelArg);
			ChannelLocal.registerNewChannelCommands(newArg, channelArg);
			ChannelGlobal.registerNewChannelCommands(newArg, channelArg);
			ChannelParty.registerNewChannelCommands(newArg, channelArg);
			ChannelWorld.registerNewChannelCommands(newArg, channelArg);
		}
		ChannelTeam.registerNewChannelCommands();
		ChannelWhisper.registerNewChannelCommands();
	}
}
