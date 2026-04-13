package com.playmonumenta.common;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import org.apache.logging.log4j.Level;

/**
 * Paper-side helper for registering the {@code /changeLogLevel} command.
 *
 * <p>This class is intentionally separate from {@link MMLog} so that loading {@link MMLog} on
 * Velocity does not trigger resolution of CommandAPI classes, which are absent on that platform.
 *
 * <p>Call from the Paper plugin entry point after constructing {@link MMLog}:
 * <pre>{@code
 * MMLog log = new MMLog("MyPlugin");
 * MMLogPaper.registerCommand(log, "myPlugin");
 * }</pre>
 */
public final class MMLogPaper {
	private MMLogPaper() {
	}

	/**
	 * Registers {@code /changeLogLevel <label> TRACE|DEBUG|INFO|WARN|ERROR} via CommandAPI.
	 *
	 * <p>The required permission is {@code <label>.changeloglevel}
	 * (e.g. {@code networkRelay.changeloglevel}).
	 *
	 * @param log   the {@link MMLog} instance whose level will be changed
	 * @param label identifier shown as the first argument (e.g. {@code "networkRelay"})
	 */
	public static void registerCommand(MMLog log, String label) {
		String permission = label + ".changeloglevel";
		new CommandAPICommand("changeLogLevel")
			.withPermission(CommandPermission.fromString(permission))
			.withSubcommand(new CommandAPICommand(label)
				.withSubcommand(new CommandAPICommand("TRACE")
					.executes((sender, args) -> {
						log.setLevel(Level.TRACE);
					}))
				.withSubcommand(new CommandAPICommand("DEBUG")
					.executes((sender, args) -> {
						log.setLevel(Level.DEBUG);
					}))
				.withSubcommand(new CommandAPICommand("INFO")
					.executes((sender, args) -> {
						log.setLevel(Level.INFO);
					}))
				.withSubcommand(new CommandAPICommand("WARN")
					.executes((sender, args) -> {
						log.setLevel(Level.WARN);
					}))
				.withSubcommand(new CommandAPICommand("ERROR")
					.executes((sender, args) -> {
						log.setLevel(Level.ERROR);
					})))
			.register();
	}
}
