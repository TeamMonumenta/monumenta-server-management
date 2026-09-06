package com.playmonumenta.common.commands;

import com.playmonumenta.common.utils.DateUtils;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;


public class RefreshTimeCommand {
	public static final String COMMAND = "refreshtime";
	public static final CommandPermission PERMISSION = CommandPermission.fromString("monumentacommon.refreshtime");

	public static void register() {
		new CommandAPICommand(COMMAND)
			.withPermission(PERMISSION)
			.executes((sender, args) -> {
				DateUtils.refreshTime();
			}).register();
	}
}
