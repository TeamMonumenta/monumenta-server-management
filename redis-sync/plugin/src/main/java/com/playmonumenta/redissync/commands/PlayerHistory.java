package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.RedisAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class PlayerHistory {
	public static void register(Plugin plugin) {
		EntitySelectorArgument.OnePlayer playerArg = new EntitySelectorArgument.OnePlayer("player");

		new CommandAPICommand("playerhistory")
			.withArguments(playerArg)
			.withPermission(CommandPermission.fromString("monumenta.command.playerhistory"))
			.executesPlayer((sender, args) -> {
					try {
						playerHistory(plugin, sender, args.getByArgument(playerArg));
					} catch (Exception ex) {
						throw CommandAPI.failWithString(ex.getMessage());
					}
				}
			).register();
	}
}
