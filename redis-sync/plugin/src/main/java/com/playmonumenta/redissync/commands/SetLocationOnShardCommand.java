package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.NetworkRelayIntegration;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.LocationArgument;
import dev.jorel.commandapi.arguments.RotationArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.wrappers.Rotation;
import java.util.Collection;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public class SetLocationOnShardCommand {
	@SuppressWarnings({"unchecked"})
	public static void register() {
		String command = "setlocationonshard";
		CommandPermission perms = CommandPermission.fromString("monumenta.command.setlocationonshard");

		EntitySelectorArgument.ManyPlayers playersArg = new EntitySelectorArgument.ManyPlayers("players");
		Argument<String> serverArg = new StringArgument("shard").replaceSuggestions(ArgumentSuggestions.strings((sender) -> NetworkRelayIntegration.getOnlineTransferTargets()));
		Argument<String> worldArg = new StringArgument("world");
		LocationArgument locationArg = new LocationArgument("location"); // technically this doesn't really make sense, but only the vector is used from the location
		RotationArgument rotationArg = new RotationArgument("rotation");
		BooleanArgument transferArg = new BooleanArgument("transfer");

		new CommandAPICommand(command)
			.withArguments(playersArg)
			.withArguments(serverArg)
			.withArguments(worldArg)
			.withArguments(locationArg)
			.withArguments(rotationArg)
			.withOptionalArguments(transferArg)
			.withPermission(perms)
			.executes((sender, args) -> {
					Collection<Player> players = args.getByArgument(playersArg);
					String shard = args.getByArgument(serverArg);
					String world = args.getByArgument(worldArg);
					Location location = args.getByArgument(locationArg);
					Rotation rotation = args.getByArgument(rotationArg);
					boolean transfer = args.getByArgumentOrDefault(transferArg, false);
					for (Player player : players) {
						MonumentaRedisSyncAPI.setPlayerWorldAndLocationOnShard(player, shard, world, location.toVector(), rotation.getYaw(), rotation.getPitch(), transfer);
					}
				}
			).register();
	}
}
