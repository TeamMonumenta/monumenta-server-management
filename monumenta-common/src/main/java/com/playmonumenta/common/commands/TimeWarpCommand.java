package com.playmonumenta.common.commands;

import com.playmonumenta.common.MonumentaCommonPlugin;
import com.playmonumenta.common.managers.TimeWarpManager;
import com.playmonumenta.common.utils.DateUtils;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.IntegerArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.LongArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.executors.CommandArguments;
import java.time.DateTimeException;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.command.CommandSender;

public class TimeWarpCommand {
	public static final String COMMAND = "timewarp";
	public static final CommandPermission PERMISSION = CommandPermission.fromString("monumentacommon.timewarp");

	public static void register() {
		List<Argument<?>> arguments = new ArrayList<>();

		arguments.add(new LiteralArgument("reset"));
		new CommandAPICommand(COMMAND)
			.withPermission(PERMISSION)
			.withArguments(arguments)
			.executes(TimeWarpCommand::runReset)
			.register();

		arguments.clear();
		arguments.add(new LiteralArgument("set"));
		arguments.add(new LiteralArgument("time"));
		arguments.add(new IntegerArgument("hour", 0, 23));
		new CommandAPICommand(COMMAND)
			.withPermission(PERMISSION)
			.withArguments(arguments)
			.withOptionalArguments(
				new IntegerArgument("minute", 0, 59),
				new IntegerArgument("second", 0, 59)
			)
			.executes(TimeWarpCommand::runTime)
			.register();

		arguments.clear();
		arguments.add(new LiteralArgument("set"));
		arguments.add(new LiteralArgument("date"));
		arguments.add(new IntegerArgument("year", 2000));
		new CommandAPICommand(COMMAND)
			.withPermission(PERMISSION)
			.withArguments(arguments)
			.withOptionalArguments(
				new IntegerArgument("month", 1, 12),
				new IntegerArgument("day", 1, 31),
				new IntegerArgument("hour", 0, 23),
				new IntegerArgument("minute", 0, 59),
				new IntegerArgument("second", 0, 59)
			)
			.executes(TimeWarpCommand::runDateTime)
			.register();

		arguments.clear();
		arguments.add(new LiteralArgument("add"));
		arguments.add(new LongArgument("amount"));
		new CommandAPICommand(COMMAND)
			.withPermission(PERMISSION)
			.withArguments(arguments)
			.executes(TimeWarpCommand::runAdd)
			.register();

		for (ChronoUnit unit : ChronoUnit.values()) {
			if (ChronoUnit.SECONDS.compareTo(unit) > 0) {
				continue;
			}

			new CommandAPICommand(COMMAND)
				.withPermission(PERMISSION)
				.withArguments(arguments)
				.withArguments(new MultiLiteralArgument("unit", unit.name().toLowerCase(Locale.getDefault())))
				.executes(TimeWarpCommand::runAdd)
				.register();
		}

		arguments.clear();
		arguments.add(new LiteralArgument("remove"));
		arguments.add(new LongArgument("amount"));
		new CommandAPICommand(COMMAND)
			.withPermission(PERMISSION)
			.withArguments(arguments)
			.executes(TimeWarpCommand::runRemove)
			.register();

		for (ChronoUnit unit : ChronoUnit.values()) {
			if (ChronoUnit.SECONDS.compareTo(unit) > 0) {
				continue;
			}

			new CommandAPICommand(COMMAND)
				.withPermission(PERMISSION)
				.withArguments(arguments)
				.withArguments(new MultiLiteralArgument("unit", unit.name().toLowerCase(Locale.getDefault())))
				.executes(TimeWarpCommand::runRemove)
				.register();
		}

		arguments.clear();
		new CommandAPICommand(COMMAND)
			.withPermission(PERMISSION)
			.withArguments(new LiteralArgument("query"))
			.executes(TimeWarpCommand::runQuery)
			.register();
	}

	public static void runReset(CommandSender sender, CommandArguments args) throws WrapperCommandSyntaxException {
		if (!MonumentaCommonPlugin.ENABLE_TIME_WARP) {
			throw CommandAPI.failWithString("Time testing is not enabled");
		}

		TimeWarpManager.reset();
		DateUtils.debugDate(sender, DateUtils.localDateTime());
	}

	public static void runTime(CommandSender sender, CommandArguments args) throws WrapperCommandSyntaxException {
		if (!MonumentaCommonPlugin.ENABLE_TIME_WARP) {
			throw CommandAPI.failWithString("Time testing is not enabled");
		}

		LocalDateTime targetTime = DateUtils.localDateTime();

		int hour = args.getOrDefaultUnchecked("hour", 0);
		int minute = args.getOrDefaultUnchecked("minute", 0);
		int second = args.getOrDefaultUnchecked("second", 0);

		targetTime = LocalDateTime.of(targetTime.getYear(),
			targetTime.getMonth(),
			targetTime.getDayOfMonth(),
			hour,
			minute,
			second);

		TimeWarpManager.set(targetTime);
		DateUtils.debugDate(sender, DateUtils.localDateTime());
	}

	public static void runDateTime(CommandSender sender, CommandArguments args) throws WrapperCommandSyntaxException {
		if (!MonumentaCommonPlugin.ENABLE_TIME_WARP) {
			throw CommandAPI.failWithString("Time testing is not enabled");
		}

		LocalDateTime targetTime = DateUtils.localDateTime();

		int year = args.getOrDefaultUnchecked("year", targetTime.getYear());
		int month = args.getOrDefaultUnchecked("month", targetTime.getMonthValue());
		int day = args.getOrDefaultUnchecked("day", targetTime.getDayOfMonth());
		Optional<Integer> hourOptional = args.getOptionalUnchecked("hour");
		int hour = hourOptional.orElse(targetTime.getHour());
		int minute = args.getOrDefaultUnchecked("minute", hourOptional.isPresent() ? 0 : targetTime.getMinute());
		int second = args.getOrDefaultUnchecked("second", hourOptional.isPresent() ? 0 : targetTime.getSecond());

		try {
			targetTime = LocalDateTime.of(year,
				month,
				day,
				hour,
				minute,
				second);
		} catch (DateTimeException ex) {
			throw CommandAPI.failWithString("Could not change time: " + ex);
		}

		TimeWarpManager.set(targetTime);
		DateUtils.debugDate(sender, DateUtils.localDateTime());
	}

	public static void runAdd(CommandSender sender, CommandArguments args) throws WrapperCommandSyntaxException {
		if (!MonumentaCommonPlugin.ENABLE_TIME_WARP) {
			throw CommandAPI.failWithString("Time testing is not enabled");
		}

		long amount = args.getOrDefaultUnchecked("amount", 0L);
		String unitName = args.getUnchecked("unit");
		ChronoUnit unit = unitName == null ? ChronoUnit.SECONDS : ChronoUnit.valueOf(unitName.toUpperCase(Locale.getDefault()));

		TimeWarpManager.add(amount, unit);
		DateUtils.debugDate(sender, DateUtils.localDateTime());
	}

	public static void runRemove(CommandSender sender, CommandArguments args) throws WrapperCommandSyntaxException {
		if (!MonumentaCommonPlugin.ENABLE_TIME_WARP) {
			throw CommandAPI.failWithString("Time testing is not enabled");
		}

		long amount = args.getOrDefaultUnchecked("amount", 0L);
		String unitName = args.getUnchecked("unit");
		ChronoUnit unit = unitName == null ? ChronoUnit.SECONDS : ChronoUnit.valueOf(unitName.toUpperCase(Locale.getDefault()));

		TimeWarpManager.add(-amount, unit);
		DateUtils.debugDate(sender, DateUtils.localDateTime());
	}

	public static void runQuery(CommandSender sender, CommandArguments args) throws WrapperCommandSyntaxException {
		if (!MonumentaCommonPlugin.ENABLE_TIME_WARP) {
			throw CommandAPI.failWithString("Time testing is not enabled");
		}

		long warpSeconds = TimeWarpManager.get();
		Component message;
		if (warpSeconds < 0) {
			message = Component.text("The server is currently ", NamedTextColor.DARK_AQUA, TextDecoration.ITALIC)
				.append(Component.text(-warpSeconds, NamedTextColor.AQUA))
				.append(Component.text(" in the past.", NamedTextColor.DARK_AQUA, TextDecoration.ITALIC));
		} else if (warpSeconds > 0) {
			message = Component.text("The server is currently ", NamedTextColor.DARK_AQUA, TextDecoration.ITALIC)
				.append(Component.text(warpSeconds, NamedTextColor.AQUA))
				.append(Component.text(" in the future.", NamedTextColor.DARK_AQUA, TextDecoration.ITALIC));
		} else {
			message = Component.text("The server has ", NamedTextColor.DARK_AQUA, TextDecoration.ITALIC)
				.append(Component.text("no time warp", NamedTextColor.AQUA))
				.append(Component.text(" applied to it!.", NamedTextColor.DARK_AQUA, TextDecoration.ITALIC));
		}

		sender.sendMessage(message);
		DateUtils.debugDate(sender, DateUtils.localDateTime());
	}
}
