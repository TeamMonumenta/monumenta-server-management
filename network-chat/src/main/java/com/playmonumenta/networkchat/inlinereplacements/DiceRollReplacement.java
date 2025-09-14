package com.playmonumenta.networkchat.inlinereplacements;

import com.playmonumenta.networkchat.utils.MMLog;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.commons.lang3.tuple.Pair;
import org.bukkit.command.CommandSender;
import org.intellij.lang.annotations.RegExp;

public class DiceRollReplacement extends InlineReplacement {

	@RegExp
	private static final String mDiceDataGivenRegex = ".* [1-9]?[0-9]*d[1-9][0-9]*.*";
	@RegExp
	private static final String mMaxDataGivenRegex = ".* [1-9][0-9]*.*";

	public DiceRollReplacement() {
		super("Dice Roll",
			"(?<=^|[^\\\\])<roll(( [1-9]?[0-9]*d[1-9][0-9]*)|( [1-9][0-9]*))?>",
			"roll");
	}

	@Override
	public Component replace(CommandSender sender, Component input) {
		MMLog.info("DRR: replacing string " + input);
		Component output = input;
		output = output.replaceText(matcher -> matcher.match(mRegex)
			.once()
			.replacement(matchResult -> {
				MMLog.info("DRR: matched string '" + matchResult.content() + "' with regex:" + mRegex);
				String regexResult = matchResult.content();
				Pair<Integer, Integer> diceData = Pair.of(1, 100);
				boolean isDiceFormat = false;
				if (regexResult.matches(mDiceDataGivenRegex)) {
					MMLog.info("DRR: matched dice regex with:" + matchResult.content());
					try {
						String[] getDiceInfo = regexResult.replaceAll("[<>]", "")
							.split(" ")[1].split("d");
						if (!getDiceInfo[0].isEmpty()) {
							diceData = Pair.of(Math.min(Integer.parseInt(getDiceInfo[0]), 8),
								Math.min(Integer.parseInt(getDiceInfo[1]), 20));
						} else {
							diceData = Pair.of(1, Math.min(Integer.parseInt(getDiceInfo[1]), 100));
						}
						isDiceFormat = true;
					} catch (Exception e) {
						return input;
					}
				} else if (regexResult.matches(mMaxDataGivenRegex)) {
					MMLog.info("DRR: matched max number regex with:" + regexResult);
					try {
						String maxInfo = regexResult.replaceAll("[<>]", "")
							.split(" ")[1];
						MMLog.info("DRR: maxInfo:" + maxInfo);
						diceData = Pair.of(1, Math.min(Integer.parseInt(maxInfo), 100));
					} catch (Exception e) {
						return input;
					}
				}

				int rollValue = 0;
				if (isDiceFormat) {
					for (int i = 0; i < diceData.getLeft(); i++) {
						rollValue += (int) (Math.random() * diceData.getRight()) + 1;
					}
					return Component.text("ROLL").decoration(TextDecoration.BOLD, true).hoverEvent(Component.text(rollValue + " out of " + diceData.getLeft()
						+ "d" + diceData.getRight() + " (" + diceData.getLeft() * diceData.getRight() + ")"));
				} else {
					rollValue = (int) (Math.random() * diceData.getRight()) + 1;
					return Component.text("ROLL").decoration(TextDecoration.BOLD, true)
						.hoverEvent(Component.text(rollValue + " out of " + diceData.getRight()));
				}
			}));
		return output;
	}
}
