package com.playmonumenta.networkchat.commands.chat.player;

import com.playmonumenta.networkchat.RemotePlayerListener;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import java.util.Collection;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ChatPlayerRefreshCommand {
	public static void register() {
		EntitySelectorArgument.ManyPlayers playersArg = new EntitySelectorArgument.ManyPlayers("players");

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("player"))
			.withArguments(new LiteralArgument("refresh"))
			.withPermission("networkchat.player.refresh")
			.executesNative((sender, args) -> {
				CommandSender callee = CommandUtils.getCallee(sender);
				if (!(callee instanceof Player target)) {
					throw CommandUtils.fail(sender, "This command can only be run as a player.");
				} else {
					RemotePlayerListener.refreshLocalPlayer(target);
				}
				return 1;
			})
			.register();

		ChatCommand.getBaseCommand()
			.withArguments(new LiteralArgument("player"))
			.withArguments(new LiteralArgument("refresh"))
			.withArguments(playersArg)
			.withPermission("networkchat.player.refresh")
			.executesNative((sender, args) -> {
				@SuppressWarnings("unchecked")
				Collection<Player> players = args.getByArgument(playersArg);

				for (Player player : players) {
					RemotePlayerListener.refreshLocalPlayer(player);
				}
				return 1;
			})
			.register();
	}
}
