package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerResetNickCommand {
	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();
		arguments.add(new LiteralArgument("player"));
		arguments.add(new LiteralArgument("resetnick"));

		ChatCommand.getBaseCommand()
			.withArguments(arguments)
			.executesNative((sender, args) -> {
				CommandSender callee = CommandUtils.getCallee(sender);
				if (!(callee instanceof Player target)) {
					throw CommandUtils.fail(sender, "This command can only be run as a player.");
				} else {
					target.displayName(null);
					target.playerListName(null);
				}
				return 1;
			})
			.register();
	}
}
