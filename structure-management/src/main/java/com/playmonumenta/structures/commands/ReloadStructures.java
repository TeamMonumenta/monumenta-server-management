package com.playmonumenta.structures.commands;

import com.playmonumenta.structures.StructuresPlugin;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class ReloadStructures {
	public static void register() {
		new CommandAPICommand("reloadstructures")
				.withPermission(CommandPermission.fromString("monumenta.structures"))
				.executes((sender, args) -> {
					StructuresPlugin.getInstance().reloadConfig();
					sender.sendMessage(Component.text("Structures reloaded", NamedTextColor.GREEN));
				})
				.register();
	}
}
