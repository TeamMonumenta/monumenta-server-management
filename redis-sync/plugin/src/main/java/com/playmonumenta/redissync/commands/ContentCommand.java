package com.playmonumenta.redissync.commands;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import com.playmonumenta.redissync.data.ContentData;
import com.playmonumenta.redissync.data.OptionalLocation;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPIBukkit;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.SuggestionInfo;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.EntitySelectorArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
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

public class ContentCommand {
	private static final String PERMISSION = "monumenta.command.content";

	public static void register() {
		new CommandAPICommand("content")
			.withPermission(PERMISSION)
			.withArguments(
				new StringArgument("content")
					.replaceSuggestions(ArgumentSuggestions.stringCollection(info -> MonumentaRedisSyncAPI.availableContentIds())),
				new EntitySelectorArgument.OnePlayer("player"),
				new EntitySelectorArgument.ManyPlayers("others")
			)
			.withOptionalArguments(
				new GreedyStringArgument("optionals")
					.replaceSuggestions(ContentCommand::optionalSuggestions)
			)
			.executesNative((sender, args) -> {
				execute(args);
			})
			.register();

		new CommandAPICommand("contentdebug")
			.withPermission(PERMISSION)
			.withArguments(new EntitySelectorArgument.OnePlayer("player"))
			.executesNative((sender, args) -> {
				CommandSender callee = sender.getCallee();
				Player player = Objects.requireNonNull(args.getUnchecked("player"));

				String content = MonumentaRedisSyncAPI.getPlayerContentData(player).getId();
				callee.sendMessage(content.isEmpty() ? "content not set" : content);
			})
			.register();
	}

	private static void execute(CommandArguments args) throws WrapperCommandSyntaxException {
		String content = Objects.requireNonNull(args.getUnchecked("content"));
		Player player = Objects.requireNonNull(args.getUnchecked("player"));
		ContentOptionals optionals = parseOptionals(args.getUnchecked("optionals"));

		ContentData data = new ContentData(content);
		if (optionals != null) {
			data.setReturnLocation(optionals.returnTo);
			data.setArrivalLocation(optionals.arriveAt);
			data.setMcfunctionOnArrival(optionals.onArrival);
		}
		MonumentaRedisSyncAPI.requestPlayerContentDataChange(player, data);
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
			} else if (option == ContentOption.RETURNTO || option == ContentOption.ARRIVEAT) {
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
					OptionalLocation location = new OptionalLocation(values[0], values[1], values[2]);
					// rotation if count is 4 or 5
					if (state.count >= 4) {
						location.yaw((float) values[3]);
						if (state.count >= 5) {
							location.pitch((float) values[4]);
						}
					}
					// set returnTo or arriveAt
					if (option == ContentOption.RETURNTO) {
						state.returnTo = location;
					} else {
						state.arriveAt = location;
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
		private @Nullable OptionalLocation returnTo = null;
		private @Nullable OptionalLocation arriveAt = null;
		private @Nullable NamespacedKey onArrival = null;

		private void reset() {
			count = 0;
			optional = false;
			expecting = null;
		}
	}

	private record ContentOptionals(@Nullable OptionalLocation returnTo, @Nullable OptionalLocation arriveAt, @Nullable NamespacedKey onArrival) {}

	private enum ContentOption {
		RETURNTO,
		ARRIVEAT,
		ONARRIVAL
	}
}
