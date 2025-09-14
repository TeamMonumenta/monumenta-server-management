package com.playmonumenta.networkchat.utils;

import com.playmonumenta.networkchat.NetworkChatProperties;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.jetbrains.annotations.Nullable;

public class CommandUtils {
	private static final Pattern RE_ALLOWED_WITHOUT_QUOTES = Pattern.compile("[0-9A-Za-z_.+-]+");

	public static CommandSender getCallee(CommandSender sender) {
		if (sender instanceof ProxiedCommandSender) {
			return ((ProxiedCommandSender)sender).getCallee();
		}
		return sender;
	}

	public static CommandSender getCaller(CommandSender sender) {
		if (sender instanceof ProxiedCommandSender) {
			return ((ProxiedCommandSender)sender).getCaller();
		}
		return sender;
	}

	public static boolean hasPermission(Permissible permissionHolder, String permission) {
		return hasPermission(permissionHolder, new Permission(permission));
	}

	public static boolean hasPermission(Permissible permissionHolder, Permission permission) {
		return permissionHolder.hasPermission(permission);
	}

	public static boolean checkSudoCommandDisallowed(CommandSender sender) {
		if (sender instanceof ProxiedCommandSender) {
			CommandSender callee = ((ProxiedCommandSender) sender).getCallee();
			CommandSender caller = ((ProxiedCommandSender) sender).getCaller();

			return !callee.equals(caller) && !NetworkChatProperties.getSudoEnabled();
		}
		return false;
	}

	public static WrapperCommandSyntaxException fail(CommandSender sender, String message) {
		if (sender instanceof ProxiedCommandSender) {
			CommandSender caller = ((ProxiedCommandSender) sender).getCaller();
			if (!sender.getName().equals(caller.getName())) {
				caller.sendMessage(Component.text(message, NamedTextColor.RED));
				return CommandAPI.failWithString("");
			} else {
				CommandSender callee = ((ProxiedCommandSender) sender).getCallee();
				callee.sendMessage(Component.text(message, NamedTextColor.RED));
				return CommandAPI.failWithString(message);
			}
		}
		return CommandAPI.failWithString(message);
	}

	public static boolean requiresQuotes(String arg) {
		if (arg == null) {
			return true;
		}
		return !RE_ALLOWED_WITHOUT_QUOTES.matcher(arg).matches();
	}

	public static String quoteIfNeeded(@Nullable String arg) {
		if (arg == null) {
			return "null";
		}
		if (requiresQuotes(arg)) {
			return "\"" + arg.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		} else {
			return arg;
		}
	}

	public static List<String> quoteIfNeeded(Collection<String> args) {
		List<String> result = new ArrayList<>();
		for (String arg :args) {
			result.add(quoteIfNeeded(arg));
		}
		return result;
	}
}
