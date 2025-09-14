package com.playmonumenta.networkchat.utils;

import com.google.gson.JsonElement;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.RemotePlayerListener;
import com.playmonumenta.networkchat.tagresolvers.OptSpace;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.UUID;
import java.util.regex.Pattern;
import javax.annotation.Nullable;
import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import net.kyori.adventure.text.minimessage.tag.standard.StandardTags;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scoreboard.Team;

public class MessagingUtils {
	public static final GsonComponentSerializer GSON_SERIALIZER = GsonComponentSerializer.gson();
	public static final LegacyComponentSerializer LEGACY_SERIALIZER = LegacyComponentSerializer.legacySection();
	public static final PlainTextComponentSerializer PLAIN_SERIALIZER = PlainTextComponentSerializer.plainText();
	public static final Pattern REGEX_LEGACY_PREFIX = Pattern.compile("[&§]");
	public static final Pattern REGEX_LEGACY_RGB_1 = Pattern.compile("(?<!<)[&§]?#([0-9a-fA-F]{6})(?!>)");
	public static final Pattern REGEX_LEGACY_RGB_2 = Pattern.compile("(?<!<)[&§]#(([&§][0-9a-fA-F]){6})(?!>)");
	public static final MiniMessage CHANNEL_HEADER_FMT_MINIMESSAGE = MiniMessage.builder()
		.tags(
			TagResolver.builder().resolvers(StandardTags.color(),
				StandardTags.decorations()
			).build()
		)
		.build();
	public static @Nullable MiniMessage SENDER_FMT_MINIMESSAGE = null;

	public static MiniMessage getSenderFmtMinimessage() {
		if (SENDER_FMT_MINIMESSAGE == null) {
			SENDER_FMT_MINIMESSAGE = MiniMessage.builder()
				.tags(
					TagResolver.builder().resolvers(StandardTags.color(),
						StandardTags.reset(),
						StandardTags.decorations(),
						StandardTags.hoverEvent(),
						StandardTags.clickEvent(),
						StandardTags.keybind(),
						StandardTags.translatable(),
						StandardTags.insertion(),
						StandardTags.font(),
						StandardTags.gradient(),
						StandardTags.rainbow(),
						OptSpace.optPrependSpace(),
						OptSpace.optAppendSpace()
					).build()
				)
				.build();
		}
		return SENDER_FMT_MINIMESSAGE;
	}

	public static String legacyToMiniMessage(String legacy) {
		String result = legacy;

		result = REGEX_LEGACY_RGB_1.matcher(result).replaceAll("<#$1>");
		result = REGEX_LEGACY_RGB_2.matcher(result).replaceAll(mr -> "<#" + REGEX_LEGACY_PREFIX.matcher(mr.group(1)).replaceAll("") + ">");

		for (ChatColor legacyFormat : ChatColor.values()) {
			if (ChatColor.MAGIC.equals(legacyFormat)) {
				result = result.replace("&" + legacyFormat.getChar(), "<obfuscated>");
				result = result.replace(legacyFormat.toString(), "<obfuscated>");
			} else if (ChatColor.UNDERLINE.equals(legacyFormat)) {
				result = result.replace("&" + legacyFormat.getChar(), "<underlined>");
				result = result.replace(legacyFormat.toString(), "<underlined>");
			} else {
				result = result.replace("&" + legacyFormat.getChar(), "<" + legacyFormat.name() + ">");
				result = result.replace(legacyFormat.toString(), "<" + legacyFormat.name() + ">");
			}
		}
		return result;
	}

	public static MiniMessage getAllowedMiniMessage(CommandSender sender) {
		TagResolver.Builder tags = TagResolver.builder();

		CommandSender caller = CommandUtils.getCaller(sender);
		if (caller instanceof Player player) {
			if (CommandUtils.hasPermission(player, "networkchat.transform.color")) {
				tags.resolver(StandardTags.color());
			}
			if (CommandUtils.hasPermission(player, "networkchat.transform.decoration")) {
				tags.resolver(StandardTags.decorations());
			}
			if (CommandUtils.hasPermission(player, "networkchat.transform.keybind")) {
				tags.resolver(StandardTags.keybind());
			}
			if (CommandUtils.hasPermission(player, "networkchat.transform.font")) {
				tags.resolver(StandardTags.font());
			}
			if (CommandUtils.hasPermission(player, "networkchat.transform.gradient")) {
				tags.resolver(StandardTags.gradient());
			}
			if (CommandUtils.hasPermission(player, "networkchat.transform.rainbow")) {
				tags.resolver(StandardTags.rainbow());
			}
		} else {
			tags.resolvers(StandardTags.color(),
				StandardTags.decorations(),
				StandardTags.keybind(),
				StandardTags.font(),
				StandardTags.gradient(),
				StandardTags.rainbow());
		}

		return MiniMessage.builder()
			.tags(tags.build())
			.build();
	}

	public static String translatePlayerName(Player player, String message) {
		return message.replaceAll("@S", player.getName());
	}

	public static Component senderComponent(CommandSender sender) {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (callee instanceof Entity entity) {
			return entityComponent(entity);
		}
		return getSenderFmtMinimessage().deserialize(PlaceholderAPI.setPlaceholders(null, NetworkChatPlugin.messageFormat("sender")),
			Placeholder.unparsed("sender_name", sender.getName()));
	}

	public static Component entityComponent(Entity entity) {
		return entityComponent(entity.getType().getKey(), entity.getUniqueId(), entity.customName());
	}

	public static Component entityComponent(NamespacedKey type, UUID id, @Nullable Component name) {
		if (type.toString().equals("minecraft:player")) {
			return RemotePlayerListener.getPlayerComponent(id);
		}

		Component entityName = name;
		if (name == null) {
			entityName = Component.translatable("entity." + type.toString().replace(":", "."));
		}

		@Nullable Team entityTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(id.toString());
		@Nullable TextColor color;
		Component teamPrefix;
		Component teamDisplayName;
		Component teamSuffix;
		if (entityTeam == null) {
			color = null;
			teamPrefix = Component.empty();
			teamDisplayName = Component.empty();
			teamSuffix = Component.empty();
		} else {
			try {
				color = entityTeam.color();
				teamPrefix = entityTeam.prefix();
				teamDisplayName = entityTeam.displayName();
				teamSuffix = entityTeam.suffix();
			} catch (Exception e) {
				color = null;
				teamPrefix = Component.empty();
				teamDisplayName = Component.translatable("minecraft:chat.square_brackets", Component.text(entityTeam.getName()));
				teamSuffix = Component.empty();
			}
		}

		return getSenderFmtMinimessage().deserialize(PlaceholderAPI.setPlaceholders(null, NetworkChatPlugin.messageFormat("entity")),
			Placeholder.parsed("entity_type", type.toString()),
			Placeholder.parsed("entity_uuid", id.toString()),
			Placeholder.component("entity_name", entityName),
			Placeholder.parsed("team_color", (color == null) ? "" : "<" + color.asHexString() + ">"),
			Placeholder.component("team_prefix", teamPrefix),
			Placeholder.component("team_displayname", teamDisplayName),
			Placeholder.component("team_suffix", teamSuffix));
	}

	public static Component playerComponent(Player player) {
		if (player == null) {
			return Component.empty();
		}
		@Nullable Team playerTeam = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
		String colorMiniMessage;
		Component teamPrefix;
		Component teamDisplayName;
		Component teamSuffix;
		if (playerTeam == null) {
			colorMiniMessage = "";
			teamPrefix = Component.empty();
			teamDisplayName = Component.empty();
			teamSuffix = Component.empty();
		} else {
			@Nullable TextColor color;
			try {
				/*
				 * Team.color() claims to return RESET if color is not set, but TextColor has no RESET value.
				 * Instead, null is the appropriate return value.
				 */
				color = playerTeam.color();
				colorMiniMessage = "<" + color.asHexString() + ">";
				teamPrefix = playerTeam.prefix();
				teamDisplayName = playerTeam.displayName();
				teamSuffix = playerTeam.suffix();
			} catch (Exception e) {
				colorMiniMessage = "";
				teamPrefix = Component.empty();
				teamDisplayName = Component.translatable("chat.square_brackets", Component.text(playerTeam.getName()));
				teamSuffix = Component.empty();
			}
		}

		@Nullable PlayerState state = PlayerStateManager.getPlayerState(player);
		if (state == null) {
			return Component.text(player.getName(), NamedTextColor.RED)
				.hoverEvent(Component.text("Invalid player state, please relog.", NamedTextColor.RED));
		}
		Component profileMessage = state.profileMessageComponent();
		String postPapiProcessing = PlaceholderAPI.setPlaceholders(player, NetworkChatPlugin.messageFormat("player"))
			.replaceAll("[§&][Rr]", "§r" + colorMiniMessage)
			// https://github.com/KyoriPowered/adventure-text-minimessage/issues/166
			.replace("<hover:show_text:\"\"></hover>", "");
		postPapiProcessing = legacyToMiniMessage(postPapiProcessing);
		return getSenderFmtMinimessage().deserialize(postPapiProcessing,
			Placeholder.component("team_prefix", teamPrefix),
			Placeholder.component("team_displayname", teamDisplayName),
			Placeholder.component("team_suffix", teamSuffix),
			Placeholder.component("profile_message", profileMessage),
			Placeholder.component("item", () -> {
				if (!CommandUtils.hasPermission(player, "networkchat.transform.item")) {
					return Component.empty();
				}

				ItemStack item = player.getInventory().getItemInMainHand();
				if (item.getType() != Material.AIR) {
					return item.displayName().hoverEvent(item);
				}
				return Component.empty();
			}));
	}

	public static boolean containsPlayerMention(String text) {
		if (!text.contains("@")) {
			return false;
		}

		int start = 0;
		boolean hasMention = false;
		while (true) {
			start = text.indexOf('@', start) + 1;
			if (start == 0) {
				break;
			}

			String substring = text.substring(start);
			String mentionedPlayer = MonumentaRedisSyncAPI.getClosestPlayerName(substring);
			if (!mentionedPlayer.isBlank()) {
				MMLog.finer(() -> "Detected mention for @" + mentionedPlayer);
				hasMention = true;
				break;
			}
		}

		return hasMention;
	}

	public static boolean isPlayerMentioned(String text, String playerName) {
		String mentionText = "@" + playerName;
		int start = 0;
		while (true) {
			start = text.indexOf(mentionText, start) + 1;
			if (start == 0) {
				break;
			}

			String substring = text.substring(start);
			String mentionedPlayer = MonumentaRedisSyncAPI.getClosestPlayerName(substring);
			if (playerName.equals(mentionedPlayer)) {
				return true;
			}
		}
		return false;
	}

	public static void sendStackTrace(CommandSender sender, Exception e) {
		TextComponent formattedMessage;
		String errorMessage = e.getLocalizedMessage();
		if (errorMessage != null) {
			formattedMessage = LEGACY_SERIALIZER.deserialize(errorMessage);
		} else {
			formattedMessage = Component.text("An error occurred without a set message. Hover for stack trace.");
		}
		formattedMessage = formattedMessage.color(NamedTextColor.RED);

		// Get the first 300 characters of the stacktrace and send them to the player
		StringWriter sw = new StringWriter();
		PrintWriter pw = new PrintWriter(sw);
		e.printStackTrace(pw);
		String sStackTrace = sw.toString();
		sStackTrace = sStackTrace.substring(0, Math.min(sStackTrace.length(), 300));

		TextComponent textStackTrace = Component.text(sStackTrace.replace("\t", "  "), NamedTextColor.RED);
		formattedMessage = formattedMessage.hoverEvent(textStackTrace);
		sender.sendMessage(formattedMessage);

		e.printStackTrace();
	}

	public static Component fromJson(JsonElement json) {
		return GSON_SERIALIZER.deserializeFromTree(json);
	}

	public static JsonElement toJson(Component component) {
		return GSON_SERIALIZER.serializeToTree(component);
	}

	public static String plainText(Component formattedText) {
		String legacyText = PLAIN_SERIALIZER.serialize(formattedText);
		return PLAIN_SERIALIZER.serialize(LEGACY_SERIALIZER.deserialize(legacyText));
	}

	public static TextColor colorFromString(String value) {
		if (value.startsWith("#")) {
			return TextColor.fromHexString(value);
		} else {
			return NamedTextColor.NAMES.value(value);
		}
	}

	public static String colorToString(@Nullable TextColor color) {
		if (color == null) {
			return "#deadbe";
		}
		if (color instanceof NamedTextColor) {
			return color.toString();
		}
		return color.asHexString();
	}

	public static String colorToMiniMessage(@Nullable TextColor color) {
		if (color == null) {
			return "";
		}
		return "<" + colorToString(color) + ">";
	}

	public static String noChatStateStr(Player player) {
		return player.getName() + " has no chat state and must relog.";
	}

	public static Component noChatState(Player player) {
		return Component.text(noChatStateStr(player), NamedTextColor.RED);
	}
}
