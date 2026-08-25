package com.playmonumenta.common.commands;

import com.playmonumenta.common.utils.DateUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.TextArgument;
import java.time.temporal.ChronoUnit;
import javax.annotation.Nullable;
import org.bukkit.command.CommandSender;

public class GetDateCommand {
	private static final ArgumentSuggestions<CommandSender> SUGGESTIONS_FIELDS = ArgumentSuggestions.strings(
		"Year", "Month", "DayOfMonth", "DayOfWeek", "IsPm",
		"HourOfDay", "HourOfTwelve", "Minute", "Second", "Ms",
		"WeeklyVersion", "DailyVersion",
		"DaysSinceUTCEpoch", "HoursSinceUTCEpoch", "MinutesSinceUTCEpoch", "SecondsSinceUTCEpoch");

	public static void register() {
		Argument<String> fieldArg = new TextArgument("field").replaceSuggestions(SUGGESTIONS_FIELDS);

		new CommandAPICommand("getdate")
			.withPermission(CommandPermission.fromString("monumentacommon.getdate"))
			.withArguments(fieldArg)
			.executes((sender, args) -> {
				return getField(args.getByArgument(fieldArg));
			})
			.register();
	}

	private static int getField(@Nullable String field) {
		if (field == null) {
			return -2;
		}
		return switch (field) {
			case "Year" -> DateUtils.getYear();
			case "Month" -> DateUtils.getMonth();
			case "DayOfMonth" -> DateUtils.getDayOfMonth();
			case "DayOfWeek" -> DateUtils.getDayOfWeek();
			case "IsPm" -> DateUtils.getAmPm() ? 1 : 0;
			case "HourOfDay" -> DateUtils.getHourOfDay();
			case "HourOfTwelve" -> DateUtils.getHourOfTwelve();
			case "Minute" -> DateUtils.getMinute();
			case "Second" -> DateUtils.getSecond();
			case "Ms" -> DateUtils.getMs();

			case "WeeklyVersion" -> (int) DateUtils.getWeeklyVersion();
			case "DailyVersion" -> (int) DateUtils.getDailyVersion();
			case "DaysSinceUTCEpoch" -> (int) DateUtils.getTimeSinceUTCEpoch(ChronoUnit.DAYS);
			case "HoursSinceUTCEpoch" -> (int) DateUtils.getTimeSinceUTCEpoch(ChronoUnit.HOURS);
			case "MinutesSinceUTCEpoch" -> (int) DateUtils.getTimeSinceUTCEpoch(ChronoUnit.MINUTES);
			case "SecondsSinceUTCEpoch" -> (int) DateUtils.getTimeSinceUTCEpoch(ChronoUnit.SECONDS);
			default -> -1;
		};
	}
}
