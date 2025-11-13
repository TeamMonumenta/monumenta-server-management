package com.playmonumenta.networkrelay.commands;

import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.util.ArgUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ProxiedCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class SendCommand {
	public SendCommand(Plugin plugin) {
		TextArgument targetArg = new TextArgument("target shard(s)");
		targetArg.replaceSuggestions(ArgumentSuggestions.strings(info -> getTargetSuggestions()));

		GreedyStringArgument commandArg = new GreedyStringArgument("command");

		new CommandAPICommand("sendcommand")
			.withPermission(BroadcastCommand.BROADCAST_PERMISSION)
			.withArguments(targetArg, commandArg)
			.executes((sender, args) -> {
				String target = args.getByArgument(targetArg);
				String command = args.getByArgument(commandArg);

				run(plugin, sender, target, command);
			})
			.register();
	}

	private void run(Plugin plugin, CommandSender sender, String target, String command) {
		if (!BroadcastCommand.isEnabled()) {
			sender.sendMessage("This command is not enabled");
			return;
		}

		/* Get the player's name, if any */
		String name = "";
		if (sender instanceof Player) {
			name = sender.getName();
		} else if (sender instanceof ProxiedCommandSender) {
			CommandSender callee = ((ProxiedCommandSender) sender).getCallee();
			if (callee instanceof Player) {
				name = callee.getName();
			}
		}

		/* Replace all instances of @S with the player's name */
		command = command.replace("@S", name);

		Pattern targetPattern;
		try {
			targetPattern = Pattern.compile(target);
		} catch (PatternSyntaxException ex) {
			sender.sendMessage(Component.text("Invalid target shard regular expression:", NamedTextColor.RED));
			sender.sendMessage(Component.text(ex.getMessage(), NamedTextColor.RED)
				.font(NamespacedKey.fromString("minecraft:uniform")));
			return;
		}

		Set<String> targetShards = getMatchingShards(targetPattern);

		if (!(sender instanceof Player) || sender.isOp()) {
			sender.sendMessage(Component.text("Sending command '" + command + "' to " + targetShards, NamedTextColor.GRAY));
		}
		plugin.getLogger().fine("Broadcasting command '" + command + "' to " + targetShards);

		for (String targetShard : targetShards) {
			try {
				NetworkRelayAPI.sendCommand(targetShard, command, NetworkRelayAPI.ServerType.ALL);
			} catch (Exception e) {
				sender.sendMessage(Component.text("Sending command to " + targetShard + " failed", NamedTextColor.RED));
			}
		}
	}

	private static Set<String> getMatchingShards(Pattern targetPattern) {
		TreeSet<String> result = new TreeSet<>();

		for (String shard : new TreeSet<>(NetworkRelayAPI.getOnlineShardNames())) {
			if (targetPattern.matcher(shard).matches()) {
				result.add(shard);
			}
		}

		return result;
	}

	private static String[] getTargetSuggestions() {
		TreeSet<String> result = new TreeSet<>();
		List<String> onlineShards = new ArrayList<>(new TreeSet<>(NetworkRelayAPI.getOnlineShardNames()));

		for (int outerIndex = 0; outerIndex < onlineShards.size(); outerIndex++) {
			String outer = onlineShards.get(outerIndex);
			result.add(outer);

			for (int innerIndex = outerIndex + 1; innerIndex < onlineShards.size(); innerIndex++) {
				String inner = onlineShards.get(innerIndex);

				result.add(findCommonPrefix(outer, inner) + ".*");
			}
		}

		return ArgUtils.quoteIfNeeded(result);
	}

	private static String findCommonPrefix(String a, String b) {
		int maxLen = Integer.min(a.length(), b.length());
		for (int i = 0; i < maxLen; i++) {
			if (a.charAt(i) != b.charAt(i)) {
				return a.substring(0, i);
			}
		}
		return a.substring(0, maxLen);
	}
}
