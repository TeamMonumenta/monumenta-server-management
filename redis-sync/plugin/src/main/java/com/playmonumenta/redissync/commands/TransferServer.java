package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.NetworkRelayIntegration;
import com.playmonumenta.redissync.player.ReturnParams;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.RotationArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.wrappers.Rotation;
import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class TransferServer {
	@SuppressWarnings({"unchecked", "DataFlowIssue"})
	public static void register() {
		String command = "transferserver";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.transferserver");

		EntitySelectorArgument.ManyPlayers playersArg = new EntitySelectorArgument.ManyPlayers("players");
		Argument<String> serverArg = new StringArgument("server").replaceSuggestions(ArgumentSuggestions.strings((sender) -> NetworkRelayIntegration.getOnlineTransferTargets()));
		LocationArgument locationArg = new LocationArgument("location");
		RotationArgument rotationArg = new RotationArgument("rotation");

		new CommandAPICommand(command)
			.withArguments(playersArg)
			.withArguments(serverArg)
			.withOptionalArguments(locationArg)
			.withOptionalArguments(rotationArg)
			.withPermission(perms)
			.executes((sender, args) -> {
					Collection<Player> players = args.getByArgument(playersArg);
					String server = args.getByArgument(serverArg);
					Location location = args.getByArgument(locationArg);
					Rotation rotation = args.getByArgument(rotationArg);
					for (Player player : players) {
						try {
							MonumentaRedisSync
								.getInstance()
								.getPlayerDataManager()
								.getLocalPlayerData(player)
								// TODO
								.transfer(server, ReturnParams.create());
						} catch (Exception ex) {
							throw CommandAPI.failWithString(ex.getMessage());
						}
					}
				}
			).register();

		/* Single player alias */
		new CommandAPICommand(command)
			.withArguments(serverArg)
			.withPermission(perms)
			.withAliases("s")
			.executesPlayer((player, args) -> {
					try {
						MonumentaRedisSync
							.getInstance()
							.getPlayerDataManager()
							.getLocalPlayerData(player)
							// TODO
							.transfer(args.getByArgument(serverArg), ReturnParams.create());
					} catch (Exception ex) {
						throw CommandAPI.failWithString(ex.getMessage());
					}
				}
			).register();
	}
}
