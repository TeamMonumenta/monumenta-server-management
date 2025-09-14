package com.playmonumenta.networkchat.inlinereplacements;

import com.playmonumenta.networkchat.utils.CommandUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.intellij.lang.annotations.RegExp;

public class InlineReplacement {

	@RegExp
	public String mRegex;

	public String mName;

	public String mPermission;

	//List of replacements, each of which is a pair of regex and a function that takes
	private List<Pair<String, Function<CommandSender, Component>>> mReplacements = new ArrayList<>();

	public boolean mRequirePlayer = true;

	public InlineReplacement(String name, @RegExp String regex, String permission) {
		mName = name;
		mRegex = regex;
		mPermission = "networkchat.transform." + permission;
	}

	public Component replace(CommandSender sender, Component input) {
		Component output = input;
		for (Pair<String, Function<CommandSender, Component>> replacement : mReplacements) {
			output = output.replaceText(matcher
				-> matcher.match(replacement.getLeft())
				.replacement(matchResult -> replacement.getRight().apply(sender)));
		}
		return output;
	}

	public boolean check(CommandSender sender, String input) {
		if (mRequirePlayer && sender instanceof Player player && CommandUtils.hasPermission(player, mPermission)) {
			return input.matches(".*" + mRegex + ".*");
		} else if (!mRequirePlayer) {
			return input.matches(".*" + mRegex + ".*");
		}
		return false;
	}

	protected void addHandler(String key, Function<CommandSender, Component> str) {
		mReplacements.add(Pair.of(key, str));
	}
}
