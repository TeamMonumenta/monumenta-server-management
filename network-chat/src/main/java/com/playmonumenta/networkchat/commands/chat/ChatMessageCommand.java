package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.NetworkChatProperties;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.UUID;
import javax.annotation.Nullable;
import org.bukkit.command.CommandSender;

public class ChatMessageCommand {
	public static void register() {
		if (NetworkChatProperties.getChatCommandModifyEnabled()) {
			StringArgument idArg = new StringArgument("Message ID");
			Argument<String> nameArg = new StringArgument("name").replaceSuggestions(ChatCommand.ALL_CACHED_PLAYER_NAMES_SUGGESTIONS);

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("message"))
				.withArguments(new LiteralArgument("delete"))
				.withArguments(idArg)
				.executesNative((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.delete.message")) {
						throw CommandUtils.fail(sender, "You do not have permission to run this command.");
					}

					return deleteMessage(sender, args.getByArgument(idArg));
				})
				.register();

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("message"))
				.withArguments(new LiteralArgument("deletefromsender"))
				.withArguments(nameArg)
				.executes((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.message.deletefromsender")) {
						throw CommandUtils.fail(sender, "You do not have permission to run this command.");
					}
					String targetName = args.getByArgument(nameArg);
					@Nullable UUID targetId = MonumentaRedisSyncAPI.cachedNameToUuid(targetName);
					if (targetId == null) {
						throw CommandUtils.fail(sender, "The player " + targetName + " has not joined this server before. Double check capitalization and spelling.");
					}

					MessageManager.deleteMessagesFromSender(targetId);
					return 1;
				})
				.register();

			ChatCommand.getBaseCommand()
				.withArguments(new LiteralArgument("message"))
				.withArguments(new LiteralArgument("clearchat"))
				.executes((sender, args) -> {
					if (!CommandUtils.hasPermission(sender, "networkchat.message.clearchat")) {
						throw CommandUtils.fail(sender, "You do not have permission to run this command.");
					}

					MessageManager.clearChat();
					return 1;
				})
				.register();
		}
	}

	private static int deleteMessage(CommandSender sender, String messageIdStr) throws WrapperCommandSyntaxException {
		UUID messageId;
		try {
			messageId = UUID.fromString(messageIdStr);
		} catch (Exception e) {
			throw CommandUtils.fail(sender, "Invalid message ID. Click a channel name to open the message GUI.");
		}

		MessageManager.deleteMessage(sender, messageId);
		return 1;
	}
}
