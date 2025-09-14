package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerProfileMessageCommand {
	public static void register() {
		GreedyStringArgument messageArg = new GreedyStringArgument("message");

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("player"))
			.withArguments(new LiteralArgument("profilemessage"))
			.withArguments(new LiteralArgument("get"))
			.executesNative((sender, args) -> {
				String profileMessage;
				CommandSender callee = CommandUtils.getCallee(sender);
				if (!(callee instanceof Player target)) {
					throw CommandUtils.fail(sender, "This command can only be run as a player.");
				} else {
					PlayerState state = PlayerStateManager.getPlayerState(target);
					if (state == null) {
						throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
					}
					profileMessage = state.profileMessage();
					if (profileMessage.isEmpty()) {
						target.sendMessage(Component.text("Your profile message is blank.", NamedTextColor.GRAY));
					} else {
						target.sendMessage(Component.text("Your profile message is:", NamedTextColor.GRAY));
						target.sendMessage(state.profileMessageComponent()
							.clickEvent(ClickEvent.suggestCommand("/" + ChatCommand.COMMAND + " profilemessage set " + profileMessage)));
					}
				}
				return profileMessage.length();
			})
			.register();

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("player"))
			.withArguments(new LiteralArgument("profilemessage"))
			.withArguments(new LiteralArgument("set"))
			.executesNative((sender, args) -> {
				CommandSender callee = CommandUtils.getCallee(sender);
				if (!(callee instanceof Player target)) {
					throw CommandUtils.fail(sender, "This command can only be run as a player.");
				} else {
					PlayerState state = PlayerStateManager.getPlayerState(target);
					if (state == null) {
						throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
					}
					target.sendMessage(Component.text("Your profile message has been cleared.", NamedTextColor.GRAY));
					state.profileMessage("");
				}
				return 0;
			})
			.register();

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("player"))
			.withArguments(new LiteralArgument("profilemessage"))
			.withArguments(new LiteralArgument("set"))
			.withArguments(messageArg)
			.executesNative((sender, args) -> {
				if (!CommandUtils.hasPermission(sender, "networkchat.setprofilemessage")) {
					throw CommandUtils.fail(sender, "You do not have permission to run this command.");
				}

				String profileMessage = args.getByArgument(messageArg);
				CommandSender callee = CommandUtils.getCallee(sender);
				if (!(callee instanceof Player target)) {
					throw CommandUtils.fail(sender, "This command can only be run as a player.");
				} else {
					if (CommandUtils.checkSudoCommandDisallowed(sender)) {
						throw CommandUtils.fail(sender, "You may not change other player's profile messages on this shard.");
					}

					PlayerState state = PlayerStateManager.getPlayerState(target);
					if (state == null) {
						throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
					}
					target.sendMessage(Component.text("Your profile message has been set to:", NamedTextColor.GRAY));
					state.profileMessage(profileMessage);
					target.sendMessage(state.profileMessageComponent()
						.clickEvent(ClickEvent.suggestCommand("/" + ChatCommand.COMMAND + " profilemessage set " + profileMessage)));
				}
				return profileMessage.length();
			})
			.register();
	}
}
