package com.playmonumenta.common.commands;

import com.playmonumenta.common.utils.DateUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

public class TimeQueryCommand {
	public static final String COMMAND = "timequery";
	public static final CommandPermission PERMISSION = CommandPermission.fromString("monumenta.timequery");

	public static void register() {
		for (
			ChronoUnit unit : ChronoUnit.values()) {
			if (ChronoUnit.SECONDS.compareTo(unit) > 0) {
				continue;
			}

			new CommandAPICommand(COMMAND)
				.withPermission(PERMISSION)
				.withArguments(new MultiLiteralArgument("unit", unit.name().toLowerCase(Locale.getDefault())))
				.executes((sender, args) -> {
					String unitName = args.getUnchecked("unit");
					ChronoUnit chronoUnit = unitName == null ? ChronoUnit.SECONDS : ChronoUnit.valueOf(unitName.toUpperCase(Locale.getDefault()));
					return (int) DateUtils.getTimeSinceUTCEpoch(chronoUnit); // Will overflow in the year 6055 for minutes, and 2038 for seconds
				})
				.register();
		}
	}
}
