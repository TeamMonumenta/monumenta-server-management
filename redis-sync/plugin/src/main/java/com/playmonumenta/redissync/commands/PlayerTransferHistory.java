package com.playmonumenta.redissync.commands;

import com.playmonumenta.redissync.AccountTransferAPI;
import com.playmonumenta.redissync.AccountTransferDetails;
import com.playmonumenta.redissync.MonumentaRedisSync;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.LongArgument;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;

public class PlayerTransferHistory {
	@SuppressWarnings("DataFlowIssue")
	public static void register(MonumentaRedisSync plugin) {
		for (ChronoUnit chronoUnit : ChronoUnit.values()) {
			if (chronoUnit.compareTo(ChronoUnit.SECONDS) < 0 || chronoUnit.compareTo(ChronoUnit.DECADES) > 0) {
				continue;
			}

			LongArgument numberOfUnitsArg = new LongArgument("time ago");
			LiteralArgument unitArg = new LiteralArgument(chronoUnit.name().toLowerCase(Locale.ENGLISH));

			new CommandAPICommand("playertransferhistory")
				.withArguments(new LiteralArgument("effective"))
				.withArguments(numberOfUnitsArg)
				.withArguments(unitArg)
				.withPermission(CommandPermission.fromString("monumenta.command.playertransferhistory"))
				.executes((sender, args) -> {
					LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
					long numberOfUnits = args.getByArgument(numberOfUnitsArg);
					LocalDateTime since = now.minus(numberOfUnits, chronoUnit);

					sender.sendMessage(Component.text("Fetching transfer history...", NamedTextColor.GREEN));
					Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
						List<AccountTransferDetails> transferDetailsList;
						try {
							transferDetailsList = AccountTransferAPI.getEffectiveTransfersSince(since).join();
						} catch (Throwable throwable) {
							sender.sendMessage(Component.text("Unable to fetch transfer history: " + throwable, NamedTextColor.RED));
							plugin.getLogger().log(Level.WARNING,"Unable to fetch transfer history: ", throwable);
							return;
						}

						Bukkit.getScheduler().runTask(plugin, () -> {
							try {
								for (AccountTransferDetails transferDetails : transferDetailsList) {
									String transferStr = String.format(
										"- %s: %s (%s) -> %s (%s)",
										DateTimeFormatter.ISO_INSTANT.format(transferDetails.transferTime().toInstant(ZoneOffset.UTC)),
										transferDetails.oldName(),
										transferDetails.oldId(),
										transferDetails.newName(),
										transferDetails.newId()
									);
									sender.sendMessage(Component.text(transferStr, NamedTextColor.YELLOW));
								}
								sender.sendMessage(Component.text("Done!", NamedTextColor.GREEN));
							} catch (Throwable throwable) {
								sender.sendMessage(Component.text("Unable to display transfer history: " + throwable, NamedTextColor.RED));
								plugin.getLogger().log(Level.WARNING,"Unable to display transfer history: ", throwable);
							}
						});
					});
				})
				.register();

			new CommandAPICommand("playertransferhistory")
				.withArguments(new LiteralArgument("full"))
				.withArguments(numberOfUnitsArg)
				.withArguments(unitArg)
				.withPermission(CommandPermission.fromString("monumenta.command.playertransferhistory"))
				.executes((sender, args) -> {
					LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
					long numberOfUnits = args.getByArgument(numberOfUnitsArg);
					LocalDateTime since = now.minus(numberOfUnits, chronoUnit);

					sender.sendMessage(Component.text("Fetching transfer history...", NamedTextColor.GREEN));
					Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
						List<AccountTransferDetails> transferDetailsList;
						try {
							transferDetailsList = AccountTransferAPI.getAllTransfersSince(since).join();
						} catch (Throwable throwable) {
							sender.sendMessage(Component.text("Unable to fetch transfer history: " + throwable, NamedTextColor.RED));
							plugin.getLogger().log(Level.WARNING,"Unable to fetch transfer history: ", throwable);
							return;
						}

						Bukkit.getScheduler().runTask(plugin, () -> {
							try {
								for (AccountTransferDetails transferDetails : transferDetailsList) {
									String transferStr = String.format(
										"- %s: %s (%s) -> %s (%s)",
										DateTimeFormatter.ISO_INSTANT.format(transferDetails.transferTime().toInstant(ZoneOffset.UTC)),
										transferDetails.oldName(),
										transferDetails.oldId(),
										transferDetails.newName(),
										transferDetails.newId()
									);
									sender.sendMessage(Component.text(transferStr, NamedTextColor.YELLOW));
								}
								sender.sendMessage(Component.text("Done!", NamedTextColor.GREEN));
							} catch (Throwable throwable) {
								sender.sendMessage(Component.text("Unable to display transfer history: " + throwable, NamedTextColor.RED));
								plugin.getLogger().log(Level.WARNING,"Unable to display transfer history: ", throwable);
							}
						});
					});
				})
				.register();
		}
	}
}
