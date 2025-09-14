package com.playmonumenta.networkchat.commands;

import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.commands.chat.ChatChannelCommand;
import com.playmonumenta.networkchat.commands.chat.ChatGuiCommand;
import com.playmonumenta.networkchat.commands.chat.ChatHelpCommand;
import com.playmonumenta.networkchat.commands.chat.ChatJoinCommand;
import com.playmonumenta.networkchat.commands.chat.ChatLeaveCommand;
import com.playmonumenta.networkchat.commands.chat.ChatListPlayersCommand;
import com.playmonumenta.networkchat.commands.chat.ChatMessageCommand;
import com.playmonumenta.networkchat.commands.chat.ChatNewCommand;
import com.playmonumenta.networkchat.commands.chat.ChatPauseCommand;
import com.playmonumenta.networkchat.commands.chat.ChatPlayerCommand;
import com.playmonumenta.networkchat.commands.chat.ChatReloadCommand;
import com.playmonumenta.networkchat.commands.chat.ChatSayCommand;
import com.playmonumenta.networkchat.commands.chat.ChatServerCommand;
import com.playmonumenta.networkrelay.RemotePlayerAPI;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import org.bukkit.command.CommandSender;

public class ChatCommand {
	public static final String COMMAND = "chat";
	public static final String[] ALIASES = {"ch", "networkchat"};
	public static final String LIST_OFFLINE_PLAYERS_PERM = "networkchat.listoffline";
	public static final ArgumentSuggestions<CommandSender> COLOR_SUGGESTIONS = ArgumentSuggestions.strings("aqua", "dark_purple", "#0189af");
	public static final ArgumentSuggestions<CommandSender> ALL_CACHED_PLAYER_NAMES_SUGGESTIONS = ArgumentSuggestions.strings((info) -> {
		if (info.sender().hasPermission(LIST_OFFLINE_PLAYERS_PERM)) {
			return MonumentaRedisSyncAPI.getAllCachedPlayerNames().toArray(String[]::new);
		}
		return RemotePlayerAPI.getVisiblePlayerNames().toArray(new String[0]);
	});
	public static final ArgumentSuggestions<CommandSender> SUGGESTIONS_VISIBLE_PLAYER_NAMES
		= ArgumentSuggestions.strings(info -> RemotePlayerAPI.getVisiblePlayerNames().toArray(new String[0]));

	public static void register(NetworkChatPlugin plugin, final @Nullable ZipFile zip) {
		ChatHelpCommand.register(plugin, zip);
		ChatNewCommand.register();
		ChatChannelCommand.register();
		ChatGuiCommand.register();
		ChatJoinCommand.register();
		ChatLeaveCommand.register();
		ChatListPlayersCommand.register();
		ChatMessageCommand.register();
		ChatPauseCommand.register();
		ChatPlayerCommand.register();
		ChatReloadCommand.register();
		ChatSayCommand.register();
		ChatServerCommand.register();
	}

	public static CommandAPICommand getBaseCommand() {
		return new CommandAPICommand(COMMAND).withAliases(ALIASES);
	}
}
