package com.playmonumenta.redissync.commands;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkit;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

public class ContentCommand {
	public static void register() {
		new CommandAPICommand("content")
			.withPermission("monumenta.command.content")
			.withArguments(
				new StringArgument("content"),
				new EntitySelectorArgument.OnePlayer("player"),
				new EntitySelectorArgument.ManyPlayers("others")
			)
			.withOptionalArguments(
				new GreedyStringArgument("optionals").replaceSuggestions(ContentCommand::optionalSuggestions)
			)
			.executesNative(ContentCommand::execute)
			.register();
	}

	private static void execute(NativeProxyCommandSender sender, CommandArguments args) throws WrapperCommandSyntaxException {
		CommandSender callee = sender.getCallee();

		String content = Objects.requireNonNull(args.getUnchecked("content"));
		Player player = Objects.requireNonNull(args.getUnchecked("player"));
		Collection<Player> others = Objects.requireNonNull(args.getUnchecked("others"));
		ContentOptionals optionals = parseOptionals(args.getUnchecked("optionals"));

		callee.sendPlainMessage("content: " + content);
		callee.sendPlainMessage("player: " + player.getName());
		callee.sendPlainMessage("others: " + others.size());
		if (optionals != null) {
			callee.sendPlainMessage("returnto: " + optionals.returnTo);
			callee.sendPlainMessage("arriveat: " + optionals.arriveAt);
			callee.sendPlainMessage("onarrival: " + optionals.onArrival);
		} else {
			callee.sendPlainMessage("optionals: null");
		}
	}

	private static @Nullable ContentOptionals parseOptionals(@Nullable String input) throws WrapperCommandSyntaxException {
		if (input == null) {
			return null;
		}
		ContentOptionalsState state = scan(input);

		// input malformed or still expecting another required token
		int index = state.index + 4; // offset index: command + 3 required arguments = 4, improve this later
		if (state.corrupted) {
			throw CommandAPI.failWithString("malformed token at index " + index);
		} else if (state.expecting != null && !state.optional) {
			throw CommandAPI.failWithString("missing required token at index " + index);
		}

		return new ContentOptionals(state.returnTo, state.arriveAt, state.onArrival);
	}

	private static CompletableFuture<Suggestions> optionalSuggestions(SuggestionInfo<CommandSender> info, SuggestionsBuilder builder) {
		String input = info.currentArg();
		int offset = input.lastIndexOf(" ") + 1;
		// offset start position of suggestions by previous fields
		builder = builder.createOffset(builder.getStart() + offset);
		String prefix = input.substring(0, offset);
		ContentOptionalsState state = scan(prefix);

		// input malformed, stop suggestions
		if (state.corrupted) {
			return builder.buildFuture();
		}

		// suggest all options if expecting option or if next token is optional
		if (state.expecting == null || state.optional) {
			state.unused.stream()
				.map(s -> s.toString().toLowerCase(Locale.ROOT))
				.forEach(builder::suggest);
		}

		if (state.expecting == ContentOption.ONARRIVAL) {
			// function suggestion logic, probably should cache, unsure how expensive this is
			CommandAPIBukkit.get().getFunctions().stream()
				.map(NamespacedKey::asString)
				.forEach(builder::suggest);
		} else if (state.expecting == ContentOption.RETURNTO || state.expecting == ContentOption.ARRIVEAT) {
			// location/rotation suggestion logic
			if (info.sender() instanceof Entity sender) {
				Location location = sender.getLocation();
				int suggestion = switch (state.count) {
					case 0 -> location.getBlockX();
					case 1 -> location.getBlockY();
					case 2 -> location.getBlockZ();
					case 3 -> Math.round(location.getYaw() / 45) * 45;
					case 4 -> Math.round(location.getPitch() / 45) * 45;
					default -> 0;
				};
				builder.suggest(suggestion);
			} else {
				builder.suggest(0);
			}
		}

		return builder.buildFuture();
	}

	private static ContentOptionalsState scan(String input) {
		ContentOptionalsState state = new ContentOptionalsState();
		if (input.isEmpty()) {
			return state;
		}
		String[] tokens = input.split("\\s+");

		while (state.index < tokens.length) {
			// reset count, optional, and expecting
			state.reset();
			// parse option
			ContentOption option;
			try {
				option = ContentOption.valueOf(tokens[state.index].toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				state.corrupted = true;
				break;
			}
			// check if option already used
			if (!state.unused.remove(option)) {
				state.corrupted = true;
				break;
			}
			state.index++;
			if (option == ContentOption.ONARRIVAL) {
				// expecting a string
				if (state.index == tokens.length) {
					state.expecting = option;
					break;
				}
				// set onArrival
				NamespacedKey key = NamespacedKey.fromString(tokens[state.index]);
				if (key == null) {
					state.corrupted = true;
					break;
				}
				state.index++;
				state.onArrival = key;
			} else {
				double[] values = new double[5];
				// expecting up to 5 doubles
				while (state.count < 5 && state.index < tokens.length) {
					try {
						values[state.count] = Double.parseDouble(tokens[state.index]);
						state.count++;
						state.index++;
					} catch (NumberFormatException e) {
						break;
					}
				}

				if (state.count < 5) {
					// expecting more doubles
					state.expecting = option;
				}

				if (state.count >= 3) {
					// future tokens can be either numbers or options
					state.optional = true;
					Vector3d location = new Vector3d(values[0], values[1], values[2]);
					Vector2d rotation = null;
					// rotation if count is 4 or 5
					if (state.count > 3) {
						rotation = new Vector2d(values[3], values[4]);
					}
					ContentLocation contentLocation = new ContentLocation(location, rotation);
					// set returnTo or arriveAt
					if (option == ContentOption.RETURNTO) {
						state.returnTo = contentLocation;
					} else if (option == ContentOption.ARRIVEAT) {
						state.arriveAt = contentLocation;
					}
				} else {
					break;
				}
			}
		}
		return state;
	}

	private static class ContentOptionalsState {
		private int index = 0;
		private int count = 0;
		private boolean corrupted = false;
		private boolean optional = false;
		private final Set<ContentOption> unused = EnumSet.allOf(ContentOption.class);
		private @Nullable ContentOption expecting = null;
		private @Nullable ContentLocation returnTo = null;
		private @Nullable ContentLocation arriveAt = null;
		private @Nullable NamespacedKey onArrival = null;

		private void reset() {
			count = 0;
			optional = false;
			expecting = null;
		}
	}

	private record ContentOptionals(@Nullable ContentLocation returnTo, @Nullable ContentLocation arriveAt, @Nullable NamespacedKey onArrival) {}

	private record ContentLocation(Vector3d location, @Nullable Vector2d rotation) {}

	private enum ContentOption {
		RETURNTO,
		ARRIVEAT,
		ONARRIVAL
	}
}
