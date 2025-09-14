package com.playmonumenta.networkrelay;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.Collection;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public abstract class RemotePlayerAPICommand {
	public static final ArgumentSuggestions<CommandSender> VISIBLE_PLAYER_ONLINE_SUGGESTIONS = ArgumentSuggestions.strings((unused) -> NetworkRelayAPI.getVisiblePlayerNames().toArray(String[]::new));

	@SuppressWarnings("unchecked")
	public static void register() {
		// we use all here for moderators
		Argument<String> remotePlayerArg = new StringArgument("player").replaceSuggestions(VISIBLE_PLAYER_ONLINE_SUGGESTIONS);
		Argument<?> localPlayerArg = new EntitySelectorArgument.ManyPlayers("player");

		// moderator command
		new CommandAPICommand("remoteplayerapi")
			.withPermission("monumenta.networkrelay.remoteplayerapi")
			.withSubcommand(new CommandAPICommand("get")
				.withArguments(remotePlayerArg)
				.executes((sender, args) -> {
					String playerName = args.getByArgument(remotePlayerArg);
					RemotePlayerData data = RemotePlayerAPI.getRemotePlayer(playerName);
					if (data == null) {
						sender.sendMessage("No data found: " + playerName);
						return;
					}
					sender.sendMessage(data.toString());
				})
			)
			.withSubcommand(new CommandAPICommand("refreshlocalplayer")
				.withArguments(localPlayerArg)
				.executes((sender, args) -> {
					Collection<Player> targetPlayers = (Collection<Player>) args.getByArgument(localPlayerArg);
					for (Player targetPlayer : targetPlayers) {
						RemotePlayerAPI.refreshPlayer(targetPlayer.getUniqueId());
					}
					return targetPlayers.size();
				})
			)
			.register();
	}
}
