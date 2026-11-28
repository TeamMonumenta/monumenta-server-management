package com.playmonumenta.redissync.commands;

import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.playmonumenta.redissync.BukkitConfigAPI;
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
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
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
					.replaceSuggestions(ArgumentSuggestions.stringCollection(info -> MonumentaRedisSyncAPI.availableContentIds()))
			)
			.withOptionalArguments(
				new EntitySelectorArgument.OnePlayer("player"),
				new EntitySelectorArgument.ManyPlayers("others"),
				new GreedyStringArgument("optionals")
					.replaceSuggestions(ContentCommand::optionalSuggestions)
			)
			.executesNative(ContentCommand::execute)
			.register();

		new CommandAPICommand("contentdebug")
			.withPermission(PERMISSION)
			.withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
			.executesNative((sender, args) -> {
				CommandSender caller = sender.getCaller();
				CommandSender callee = sender.getCallee();
				Player player = args.getUnchecked("player");
				if (player == null) {
					if (callee instanceof Player calleePlayer) {
						player = calleePlayer;
					} else {
						throw CommandAPI.failWithString("Either the player argument must be specified, or the command must run as a player");
					}
				}

				String content = MonumentaRedisSyncAPI.getPlayerContentData(player).getId();
				caller.sendMessage(Component.text("Player's content is ", NamedTextColor.BLUE)
					.append(Component.text(content.isEmpty() ? "not set" : content, NamedTextColor.GREEN)));
			})
			.register();

		new CommandAPICommand("contenttest")
			.withPermission(PERMISSION)
			.withArguments(
				new StringArgument("content")
					.replaceSuggestions(ArgumentSuggestions.stringCollection(info -> MonumentaRedisSyncAPI.availableContentIds()))
			)
			.withOptionalArguments(new EntitySelectorArgument.OnePlayer("player"))
			.executesNative(ContentCommand::executeTest)
			.register();
	}

	private static void execute(NativeProxyCommandSender sender, CommandArguments args) throws WrapperCommandSyntaxException {
		String content = Objects.requireNonNull(args.getUnchecked("content"));
		Player player = args.getUnchecked("player");
		Collection<Player> others = args.getUnchecked("others");
		ContentOptionals optionals = parseOptionals(args.getUnchecked("optionals"));

		if (player == null) {
			if (!(sender.getCallee() instanceof Player callee)) {
				throw CommandAPI.failWithString("Player must be specified, or command must be run as a player");
			}
			player = callee;
		} else {
			restrictedArgumentsCheck();
		}

		if (others == null) {
			others = List.of();
		} else {
			restrictedArgumentsCheck();
		}

		ContentData data = new ContentData(content);
		if (optionals != null) {
			restrictedArgumentsCheck();

			data.setReturnLocation(optionals.returnTo);
			data.setArrivalLocation(optionals.arriveAt);
			data.setMcfunctionOnArrival(optionals.onArrival);
		}
		MonumentaRedisSyncAPI.requestPlayerContentDataChange(player, others, data);
	}

	private static int executeTest(NativeProxyCommandSender sender, CommandArguments args) throws WrapperCommandSyntaxException {
		String content = Objects.requireNonNull(args.getUnchecked("content"));
		Player player = args.getUnchecked("player");

		if (player == null) {
			if (!(sender.getCallee() instanceof Player callee)) {
				throw CommandAPI.failWithString("Player must be specified, or command must be run as a player");
			}
			player = callee;
		}

		return MonumentaRedisSyncAPI.getPlayerContentData(player).getId().equals(content) ? 1 : 0;
	}

	private static void restrictedArgumentsCheck() throws WrapperCommandSyntaxException {
		if (BukkitConfigAPI.getContentArgumentsRestricted()) {
			throw CommandAPI.failWithString("Optional arguments for /content are disabled on this shard.");
		}
	}

	private static @Nullable ContentOptionals parseOptionals(@Nullable String input) throws WrapperCommandSyntaxException {
		if (input == null) {
			return null;
		}
		ContentOptionalsState state = scan(input);

		// input malformed or still expecting another required token
		int index = state.mIndex + 4; // offset index: command + 3 required arguments = 4, improve this later
		if (state.mCorrupted) {
			throw CommandAPI.failWithString("malformed token at index " + index);
		} else if (state.mExpecting != null && !state.mOptional) {
			throw CommandAPI.failWithString("missing required token at index " + index);
		}

		return new ContentOptionals(state.mReturnTo, state.mArriveAt, state.mOnArrival);
	}

	private static CompletableFuture<Suggestions> optionalSuggestions(SuggestionInfo<CommandSender> info, SuggestionsBuilder builder) {
		String input = info.currentArg();
		int offset = input.lastIndexOf(" ") + 1;
		// offset start position of suggestions by previous fields
		builder = builder.createOffset(builder.getStart() + offset);
		String prefix = input.substring(0, offset);
		ContentOptionalsState state = scan(prefix);

		// input malformed, stop suggestions
		if (state.mCorrupted) {
			return builder.buildFuture();
		}

		// suggest all options if expecting option or if next token is optional
		if (state.mExpecting == null || state.mOptional) {
			state.mUnused.stream()
				.map(s -> s.toString().toLowerCase(Locale.ROOT))
				.forEach(builder::suggest);
		}

		if (state.mExpecting == ContentOption.ONARRIVAL) {
			// function suggestion logic, probably should cache, unsure how expensive this is
			CommandAPIBukkit.get().getFunctions().stream()
				.map(NamespacedKey::asString)
				.forEach(builder::suggest);
		} else if (state.mExpecting == ContentOption.RETURNTO || state.mExpecting == ContentOption.ARRIVEAT) {
			// location/rotation suggestion logic
			if (info.sender() instanceof Entity sender) {
				Location location = sender.getLocation();
				double suggestion = switch (state.mCount) {
					case 0 -> Math.round(location.getX() * 2.0) / 2.0;
					case 1 -> Math.round(location.getY() * 2.0) / 2.0;
					case 2 -> Math.round(location.getZ() * 2.0) / 2.0;
					case 3 -> Math.round(location.getYaw() / 45) * 45;
					case 4 -> Math.round(location.getPitch() / 45) * 45;
					default -> 0;
				};
				builder.suggest(state.mCount > 2 ? Integer.toString((int) suggestion) : Double.toString(suggestion));
			} else {
				builder.suggest("0");
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

		while (state.mIndex < tokens.length) {
			// reset count, optional, and expecting
			state.reset();
			// parse option
			ContentOption option;
			try {
				option = ContentOption.valueOf(tokens[state.mIndex].toUpperCase(Locale.ROOT));
			} catch (IllegalArgumentException e) {
				state.mCorrupted = true;
				break;
			}
			// check if option already used
			if (!state.mUnused.remove(option)) {
				state.mCorrupted = true;
				break;
			}
			state.mIndex++;
			if (option == ContentOption.ONARRIVAL) {
				// expecting a string
				if (state.mIndex == tokens.length) {
					state.mExpecting = option;
					break;
				}
				// set onArrival
				NamespacedKey key = NamespacedKey.fromString(tokens[state.mIndex]);
				if (key == null) {
					state.mCorrupted = true;
					break;
				}
				state.mIndex++;
				state.mOnArrival = key;
			} else if (option == ContentOption.RETURNTO || option == ContentOption.ARRIVEAT) {
				double[] values = new double[5];
				// expecting up to 5 doubles
				while (state.mCount < 5 && state.mIndex < tokens.length) {
					try {
						values[state.mCount] = Double.parseDouble(tokens[state.mIndex]);
						// x and z round up by 0.5 if integer values, otherwise parse as usual
						if ((state.mCount == 0 || state.mCount == 2) && !tokens[state.mIndex].contains(".")) {
							values[state.mCount] += 0.5;
						}
						state.mCount++;
						state.mIndex++;
					} catch (NumberFormatException e) {
						break;
					}
				}

				if (state.mCount < 5) {
					// expecting more doubles
					state.mExpecting = option;
				}

				if (state.mCount >= 3) {
					// future tokens can be either numbers or options
					state.mOptional = true;
					OptionalLocation location = new OptionalLocation(values[0], values[1], values[2]);
					// rotation if count is 4 or 5
					if (state.mCount >= 4) {
						location.yaw((float) values[3]);
						if (state.mCount >= 5) {
							location.pitch((float) values[4]);
						}
					}
					// set returnTo or arriveAt
					if (option == ContentOption.RETURNTO) {
						state.mReturnTo = location;
					} else {
						state.mArriveAt = location;
					}
				} else {
					break;
				}
			}
		}
		return state;
	}

	private static class ContentOptionalsState {
		private int mIndex = 0;
		private int mCount = 0;
		private boolean mCorrupted = false;
		private boolean mOptional = false;
		private final Set<ContentOption> mUnused = EnumSet.allOf(ContentOption.class);
		private @Nullable ContentOption mExpecting = null;
		private @Nullable OptionalLocation mReturnTo = null;
		private @Nullable OptionalLocation mArriveAt = null;
		private @Nullable NamespacedKey mOnArrival = null;

		private void reset() {
			mCount = 0;
			mOptional = false;
			mExpecting = null;
		}
	}

	private record ContentOptionals(@Nullable OptionalLocation returnTo, @Nullable OptionalLocation arriveAt, @Nullable NamespacedKey onArrival) {}

	private enum ContentOption {
		RETURNTO,
		ARRIVEAT,
		ONARRIVAL
	}
}
