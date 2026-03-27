package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.RedisAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
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

	private static void playerHistory(Plugin plugin, CommandSender sender, Player target) {
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.lrange(MonumentaRedisSyncAPI.getRedisHistoryPath(target), 0, -1).toCompletableFuture().whenComplete((history, ex) -> {
				if (ex != null) {
					sender.sendMessage(Component.text("Failed to load player history: " + ex.getMessage(), NamedTextColor.RED));
					return;
				}
				Bukkit.getScheduler().runTask(plugin, () -> {
					int idx = 0;
					for (String hist : history) {
						String[] split = hist.split("\\|");
						if (split.length != 3) {
							sender.sendMessage(Component.text("Got corrupted history with " + split.length + " entries: " + hist, NamedTextColor.RED));
							continue;
						}

						sender.sendMessage(
							Component.text(idx, NamedTextColor.AQUA)
								.append(Component.space())
								.append(Component.text(split[0], NamedTextColor.GOLD))
								.append(Component.space())
								.append(Component.text(MonumentaRedisSyncAPI.getTimeDifferenceSince(Long.parseLong(split[1])), NamedTextColor.WHITE))
								.append(Component.text(" ago"))
						);
						idx += 1;
					}
				});
		});
		}
	}
}
