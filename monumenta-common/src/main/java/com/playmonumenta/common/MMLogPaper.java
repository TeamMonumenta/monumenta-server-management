package com.playmonumenta.common;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import org.apache.logging.log4j.Level;

/**
 * Paper-side helper for registering the {@code /changeloglevel} command.
 *
 * <p>This class is intentionally separate from {@link MMLog} so that loading {@link MMLog} on
 * Velocity does not trigger resolution of CommandAPI classes, which are absent on that platform.
 *
 * <p>Call from the Paper plugin entry point after constructing {@link MMLog}:
 * <pre>{@code
 * MMLog log = new MMLog("MonumentaMyPlugin");
 * MMLogPaper.registerCommand(log);
 * }</pre>
 */
public final class MMLogPaper {
	private MMLogPaper() {
	}

	/**
	 * Registers {@code /changeloglevel <name> TRACE|DEBUG|INFO|WARN|ERROR} via CommandAPI,
	 * where {@code <name>} is {@link MMLog#getName()} — the same {@code pluginName} passed to the
	 * constructor (e.g. {@code "MonumentaNetworkRelay"}).
	 *
	 * <p>The required permission is {@code <name>.changeloglevel} (all lowercase).
	 * Using the logger name directly ensures the command argument and any
	 * {@code log4j2.xml} {@code <Logger name="...">} entries refer to the same logger.
	 *
	 * @param log the {@link MMLog} instance whose level will be changed
	 */
	public static void registerCommand(MMLog log) {
		String label = log.getName();
		String permission = label + ".changeloglevel";
		new CommandAPICommand("changeloglevel")
			.withPermission(CommandPermission.fromString(permission))
			.withSubcommand(new CommandAPICommand(label)
				.withSubcommand(new CommandAPICommand("TRACE")
					.executes((sender, args) -> {
						log.setLevel(Level.TRACE);
						sender.sendMessage("Log level for " + label + " set to TRACE");
					}))
				.withSubcommand(new CommandAPICommand("DEBUG")
					.executes((sender, args) -> {
						log.setLevel(Level.DEBUG);
						sender.sendMessage("Log level for " + label + " set to DEBUG");
					}))
				.withSubcommand(new CommandAPICommand("INFO")
					.executes((sender, args) -> {
						log.setLevel(Level.INFO);
						sender.sendMessage("Log level for " + label + " set to INFO");
					}))
				.withSubcommand(new CommandAPICommand("WARN")
					.executes((sender, args) -> {
						log.setLevel(Level.WARN);
						sender.sendMessage("Log level for " + label + " set to WARN");
					}))
				.withSubcommand(new CommandAPICommand("ERROR")
					.executes((sender, args) -> {
						log.setLevel(Level.ERROR);
						sender.sendMessage("Log level for " + label + " set to ERROR");
					})))
			.register();
	}
}
