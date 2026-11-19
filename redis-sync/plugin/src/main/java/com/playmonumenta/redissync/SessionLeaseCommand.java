package com.playmonumenta.redissync;

import com.playmonumenta.redissync.utils.MMLog;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

/**
 * {@code /sessionlease <player> [clear]}: shows who holds a player's session lease (see
 * {@link SessionLease}), or deletes it. Clearing is for a lease known to be stale; it expires on its
 * own within {@link SessionLease#TTL_MILLIS} anyway.
 */
final class SessionLeaseCommand {
	private SessionLeaseCommand() {
	}

	static void register() {
		CommandPermission perms = CommandPermission.fromString("monumenta.command.sessionlease");
		StringArgument playerArg = new StringArgument("player");
		playerArg.replaceSuggestions(MonumentaRedisSyncAPI.SUGGESTIONS_ALL_CACHED_PLAYER_NAMES);

		new CommandAPICommand("sessionlease")
			.withPermission(perms)
			.withArguments(playerArg)
			.executes((sender, args) -> {
				String name = args.getByArgument(playerArg);
				withUuid(sender, name, uuid -> SessionLease.describe(uuid).thenApply(holder -> name + " (" + uuid + ") session lease: " + holder));
			})
			.register();

		new CommandAPICommand("sessionlease")
			.withPermission(perms)
			.withArguments(playerArg, new LiteralArgument("clear"))
			.executes((sender, args) -> {
				String name = args.getByArgument(playerArg);
				withUuid(sender, name, uuid -> SessionLease.describe(uuid).thenCompose(holder -> SessionLease.forceClear(uuid).thenApply(deleted -> {
					MMLog.warning(sender.getName() + " cleared the session lease of " + name + " (" + uuid + "), which was: " + holder);
					return "Cleared " + name + "'s session lease, which was: " + holder;
				})));
			})
			.register();
	}

	private interface Action {
		CompletableFuture<String> run(UUID uuid);
	}

	private static void withUuid(CommandSender sender, String name, Action action) {
		UUID cached = MonumentaRedisSyncAPI.cachedNameToUuid(name);
		CompletableFuture<UUID> uuidFuture = cached != null ? CompletableFuture.completedFuture(cached) : MonumentaRedisSyncAPI.nameToUUID(name);
		uuidFuture.thenCompose(uuid -> {
			if (uuid == null) {
				return CompletableFuture.completedFuture("Unknown player " + name);
			}
			return action.run(uuid);
		}).whenComplete((message, ex) -> Bukkit.getScheduler().runTask(DataEventListener.getPlugin(), () -> {
			if (ex != null) {
				sender.sendMessage(Component.text("Failed: " + ex.getMessage(), NamedTextColor.RED));
			} else {
				sender.sendMessage(Component.text(message, NamedTextColor.GOLD));
			}
		}));
	}
}
