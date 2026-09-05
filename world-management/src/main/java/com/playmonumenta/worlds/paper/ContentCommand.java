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
		ContentScanner scanner = scan(input);
		if (scanner.corrupted) {
			return null;
		}
		return new ContentOptionals(scanner.returnTo, scanner.arriveAt, scanner.onArrival);
	}

	private static CompletableFuture<String[]> suggestions(SuggestionInfo<CommandSender> info) {
		return CompletableFuture.supplyAsync(() -> {
			String input = info.currentArg();
			ContentScanner scanner = scan(input);
			List<String> suggestions = new ArrayList<>();

			if (scanner.current == null) {
				suggestions.addAll(scanner.unused.stream().map(s -> s.toString().toLowerCase(Locale.ROOT)).toList());
			}
			if (scanner.current == ContentOption.ONARRIVAL) {
				// function suggestion logic
				suggestions.addAll(List.of("function:test", "test:function"));
			}

			String prefix = input.substring(0, input.lastIndexOf(" ") + 1);
			return suggestions.stream()
				.map(s -> prefix + s)
				.filter(s -> s.startsWith(input))
				.toArray(String[]::new);
		});
	}

	private static ContentScanner scan(String input) {
		ContentScanner scanner = new ContentScanner();
		if (input.isEmpty()) {
			return scanner;
		}
		String[] tokens = input.split("\\s+");

		int i = 0;
		while (i < tokens.length) {
			ContentOption option;
			try {
				option = ContentOption.valueOf(tokens[i++].toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				scanner.corrupted = true;
				break;
			}
			if (!scanner.unused.remove(option)) {
				scanner.corrupted = true;
				break;
			}
			if (option == ContentOption.ONARRIVAL) {
				if (i == tokens.length) {
					scanner.current = option;
					break;
				}
				NamespacedKey key = NamespacedKey.fromString(tokens[i++]);
				if (key == null) {
					scanner.corrupted = true;
					break;
				}
				scanner.onArrival = key;
			} else {
				int count = 0;
				double[] values = new double[5];
				while (count < 5 && i < tokens.length) {
					try {
						values[count] = Double.parseDouble(tokens[i]);
						count++;
						i++;
					} catch (NumberFormatException e) {
						break;
					}
				}

				if (count == 5) {
					Vector3d location = new Vector3d(values[0], values[1], values[2]);
					Vector2d rotation = new Vector2d(values[3], values[4]);
					ContentLocation contentLocation = new ContentLocation(location, rotation);
					if (option == ContentOption.RETURNTO) {
						scanner.returnTo = contentLocation;
					} else if (option == ContentOption.ARRIVEAT) {
						scanner.arriveAt = contentLocation;
					}
				} else if (count == 3) {
					Vector3d location = new Vector3d(values[0], values[1], values[2]);
					ContentLocation contentLocation = new ContentLocation(location, null);
					if (option == ContentOption.RETURNTO) {
						scanner.returnTo = contentLocation;
					} else if (option == ContentOption.ARRIVEAT) {
						scanner.arriveAt = contentLocation;
					}
				} else {
					scanner.current = option;
					scanner.corrupted = true;
					break;
				}
			}
		}
		return scanner;
	}

	private static class ContentScanner {
		private @Nullable ContentLocation returnTo = null;
		private @Nullable ContentLocation arriveAt = null;
		private @Nullable NamespacedKey onArrival = null;
		private @Nullable ContentOption current = null;
		private boolean corrupted = false;
		private final Set<ContentOption> unused = EnumSet.allOf(ContentOption.class);
	}

	private record ContentOptionals(@Nullable ContentLocation returnTo, @Nullable ContentLocation arriveAt, @Nullable NamespacedKey onArrival) {}

	private record ContentLocation(Vector3d location, @Nullable Vector2d rotation) {}

	private enum ContentOption {
		RETURNTO,
		ARRIVEAT,
		ONARRIVAL
	}
}
