package com.playmonumenta.networkchat.commands.chat;

import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPauseCommand {
	public static void register() {
		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("pause"))
			.executesNative((sender, args) -> {
				return pause(sender);
			})
			.register();

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("unpause"))
			.executesNative((sender, args) -> {
				return unpause(sender);
			})
			.register();

		new CommandAPICommand("pausechat")
			.withAliases("pc")
			.executesNative((sender, args) -> {
				return togglePause(sender);
			})
			.register();
	}

	private static int pause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			if (CommandUtils.checkSudoCommandDisallowed(sender)) {
				throw CommandUtils.fail(sender, "You may not pause chat for other players on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
			}

			playerState.pauseChat();
			target.sendMessage(Component.text("Chat paused.", NamedTextColor.GRAY));
			return 1;
		}
	}

	private static int unpause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			if (CommandUtils.checkSudoCommandDisallowed(sender)) {
				throw CommandUtils.fail(sender, "You may not unpause chat for other players on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
			}

			target.sendMessage(Component.text("Unpausing chat.", NamedTextColor.GRAY));
			playerState.unpauseChat();
			return 1;
		}
	}

	private static int togglePause(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player target)) {
			throw CommandUtils.fail(sender, "This command can only be run as a player.");
		} else {
			if (CommandUtils.checkSudoCommandDisallowed(sender)) {
				throw CommandUtils.fail(sender, "You may not pause chat for other players on this shard.");
			}

			PlayerState playerState = PlayerStateManager.getPlayerState(target);
			if (playerState == null) {
				throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(target));
			}

			if (playerState.isPaused()) {
				target.sendMessage(Component.text("Unpausing chat.", NamedTextColor.GRAY));
				playerState.unpauseChat();
			} else {
				playerState.pauseChat();
				target.sendMessage(Component.text("Chat paused.", NamedTextColor.GRAY));
			}
			return 1;
		}
	}
}
