package com.playmonumenta.worlds.paper;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.executors.CommandArguments;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector2d;
import org.joml.Vector3d;

public class ContentCommand {
	public static void register() {
		new CommandAPICommand("newcontent")
			.withPermission("monumenta.command.newcontent")
			.withArguments(
				new StringArgument("content"),
				new EntitySelectorArgument.OnePlayer("player"),
				new EntitySelectorArgument.ManyPlayers("others")
			)
			.withOptionalArguments(
				new GreedyStringArgument("optionals").replaceSuggestions(ArgumentSuggestions.stringsAsync(ContentCommand::suggestions))
			)
			.executesNative(ContentCommand::execute)
			.register();
	}

	private static void execute(NativeProxyCommandSender sender, CommandArguments args) {
		CommandSender callee = sender.getCallee();

		String content = Objects.requireNonNull(args.getUnchecked("content"));
		Player player = Objects.requireNonNull(args.getUnchecked("player"));
		Collection<Player> others = Objects.requireNonNull(args.getUnchecked("others"));
		ContentOptionals optionals = parse(args.getUnchecked("optionals"));

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

	private static @Nullable ContentOptionals parse(@Nullable String input) {
		if (input == null) {
			return null;
		}
		ContentScannerState state = scan(input);
		// input malformed or still expecting another token
		if (state.corrupted || state.current != null) {
			return null;
		}
		return new ContentOptionals(state.returnTo, state.arriveAt, state.onArrival);
	}

	private static CompletableFuture<String[]> suggestions(SuggestionInfo<CommandSender> info) {
		return CompletableFuture.supplyAsync(() -> {
			String input = info.currentArg();
			String prefix = input.substring(0, input.lastIndexOf(" ") + 1);
			ContentScannerState state = scan(prefix);
			List<String> suggestions = new ArrayList<>();

			// input malformed, stop suggestions
			if (state.corrupted) {
				return new String[] {};
			}

			// suggest all options if expecting option or if next field is optional
			if (state.current == null || state.optional) {
				suggestions.addAll(state.unused.stream().map(s -> s.toString().toLowerCase(Locale.ROOT)).toList());
			}

			// function suggestion logic
			if (state.current == ContentOption.ONARRIVAL) {
				suggestions.addAll(List.of("function:test", "test:function"));
			}

			if (suggestions.isEmpty()) {
				return new String[] {};
			}

			return suggestions.stream()
				.map(s -> prefix + s) // suggestions start from beginning of greedy string, must append typed prefix to all suggestions
				.toArray(String[]::new);
		});
	}

	private static ContentScannerState scan(String input) {
		ContentScannerState state = new ContentScannerState();
		String[] tokens = input.split("\\s+");

		int i = 0;
		while (i < tokens.length) {
			// reset current, count, and optional
			state.reset();
			try {
				state.current = ContentOption.valueOf(tokens[i].toUpperCase(Locale.ROOT));
				i++;
			} catch (IllegalArgumentException e) {
				state.corrupted = true;
				break;
			}
			// check if option already used
			if (!state.unused.remove(state.current)) {
				state.corrupted = true;
				break;
			}
			if (state.current == ContentOption.ONARRIVAL) {
				// check ahead for 1 string
				if (i == tokens.length) {
					break;
				}
				// set onArrival field
				NamespacedKey key = NamespacedKey.fromString(tokens[i++]);
				if (key == null) {
					state.corrupted = true;
					break;
				}
				state.onArrival = key;
			} else {
				double[] values = new double[5];
				// check ahead up to 5 doubles
				while (state.count < 5 && i < tokens.length) {
					try {
						values[state.count] = Double.parseDouble(tokens[i]);
						state.count++;
						i++;
					} catch (NumberFormatException e) {
						break;
					}
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
					if (state.current == ContentOption.RETURNTO) {
						state.returnTo = contentLocation;
					} else if (state.current == ContentOption.ARRIVEAT) {
						state.arriveAt = contentLocation;
					}
				} else {
					break;
				}
			}
		}
		return state;
	}

	private static class ContentScannerState {
		private int count = 0;
		private boolean corrupted = false;
		private boolean optional = false;
		private final Set<ContentOption> unused = EnumSet.allOf(ContentOption.class);
		private @Nullable ContentOption current = null;
		private @Nullable ContentLocation returnTo = null;
		private @Nullable ContentLocation arriveAt = null;
		private @Nullable NamespacedKey onArrival = null;

		private void reset() {
			count = 0;
			optional = false;
			current = null;
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
