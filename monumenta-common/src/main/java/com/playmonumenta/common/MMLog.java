package com.playmonumenta.common;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.tree.LiteralCommandNode;
import com.velocitypowered.api.command.BrigadierCommand;
import com.velocitypowered.api.command.CommandManager;
import com.velocitypowered.api.command.CommandSource;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;

/**
 * Per-plugin log4j2 logger with runtime level control.
 *
 * <p>Each plugin creates one instance and holds it as a public field so a static facade can
 * delegate to it. Construct in {@code onLoad()} / {@code onEnable()} (Paper) or
 * {@code onProxyInit()} (Velocity), then call the appropriate {@code register*Command} method
 * to expose a {@code changeLogLevel} command in-game.
 *
 * <p>Paper example (plugin entry point):
 * <pre>{@code
 * public MMLog mLog;
 *
 * public void onEnable() {
 *     mLog = new MMLog("MyPlugin");
 *     mLog.registerPaperCommand("myPlugin");
 * }
 * }</pre>
 *
 * <p>Velocity example (plugin entry point):
 * <pre>{@code
 * public MMLog mLog;
 *
 * public void onProxyInit(ProxyInitializeEvent event) {
 *     mLog = new MMLog("MyPlugin");
 *     mLog.registerVelocityCommand(mServer.getCommandManager(), this, "myPlugin");
 * }
 * }</pre>
 *
 * <p>Static facade (shared across both platforms):
 * <pre>{@code
 * public class MMLog {
 *     public static void info(String msg) {
 *         MyPlugin.getInstance().mLog.info(msg);
 *     }
 * }
 * }</pre>
 */
public class MMLog {
	private final Logger mLogger;

	/**
	 * Creates a logger backed by log4j2 using {@code pluginId} as the logger name.
	 *
	 * <p>Use the same {@code pluginId} on both Paper and Velocity so that log4j2 level
	 * changes (e.g. via config or the {@code changeLogLevel} command) apply regardless of
	 * which platform the plugin is running on. The conventional value is the plugin's display
	 * name as returned by {@code JavaPlugin.getName()} on Paper (e.g. {@code "MyPlugin"}).
	 *
	 * @param pluginId log4j2 logger name; should match across Paper and Velocity deployments
	 */
	public MMLog(String pluginId) {
		mLogger = LogManager.getLogger(pluginId);
	}

	/**
	 * Registers a CommandAPI {@code changeLogLevel} subcommand under the given command path.
	 *
	 * <p>Call once from the Paper plugin's {@code onEnable()}. The path elements become nested
	 * CommandAPI subcommands, with {@code changeLogLevel TRACE|DEBUG|INFO|WARN|ERROR} appended.
	 * For example, {@code registerPaperCommand("monumenta", "myPlugin")} registers
	 * {@code /monumenta myPlugin changeLogLevel INFO}.
	 *
	 * <p>The required permission is {@code <path.joined.with.dots>.changeloglevel}
	 * (e.g. {@code monumenta.myPlugin.changeloglevel}).
	 *
	 * @param commandPath one or more command names forming the path above {@code changeLogLevel}
	 */
	public void registerPaperCommand(String... commandPath) {
		String permission = String.join(".", commandPath) + ".changeloglevel";
		CommandAPICommand changeloglevel = new CommandAPICommand("changeLogLevel")
			.withPermission(CommandPermission.fromString(permission))
			.withSubcommand(new CommandAPICommand("TRACE")
				.executes((sender, args) -> {
					setLevel(Level.TRACE);
				}))
			.withSubcommand(new CommandAPICommand("DEBUG")
				.executes((sender, args) -> {
					setLevel(Level.DEBUG);
				}))
			.withSubcommand(new CommandAPICommand("INFO")
				.executes((sender, args) -> {
					setLevel(Level.INFO);
				}))
			.withSubcommand(new CommandAPICommand("WARN")
				.executes((sender, args) -> {
					setLevel(Level.WARN);
				}))
			.withSubcommand(new CommandAPICommand("ERROR")
				.executes((sender, args) -> {
					setLevel(Level.ERROR);
				}));

		CommandAPICommand current = changeloglevel;
		for (int i = commandPath.length - 1; i >= 1; i--) {
			current = new CommandAPICommand(commandPath[i]).withSubcommand(current);
		}
		new CommandAPICommand(commandPath[0]).withSubcommand(current).register();
	}

	/**
	 * Registers a Brigadier {@code changeLogLevel} subcommand under the given command path.
	 *
	 * <p>Call once from the Velocity plugin's {@code onProxyInit()}. Behaves identically to
	 * {@link #registerPaperCommand} in structure and permission naming, but uses the Velocity
	 * {@link CommandManager} and BrigadierCommand API. Pass {@code this} (the Velocity plugin
	 * instance) as {@code plugin} so Velocity can attribute the command to the correct plugin.
	 *
	 * @param commandManager the Velocity proxy's command manager
	 * @param plugin         the Velocity plugin instance registering the command
	 * @param commandPath    one or more command names forming the path above {@code changeLogLevel}
	 */
	public void registerVelocityCommand(CommandManager commandManager, Object plugin, String... commandPath) {
		String permission = String.join(".", commandPath) + ".changeloglevel";
		LiteralArgumentBuilder<CommandSource> changeloglevel = BrigadierCommand.literalArgumentBuilder("changeLogLevel")
			.requires(source -> source.hasPermission(permission))
			.then(BrigadierCommand.literalArgumentBuilder("TRACE")
				.executes(ctx -> {
					setLevel(Level.TRACE);
					return 1;
				}))
			.then(BrigadierCommand.literalArgumentBuilder("DEBUG")
				.executes(ctx -> {
					setLevel(Level.DEBUG);
					return 1;
				}))
			.then(BrigadierCommand.literalArgumentBuilder("INFO")
				.executes(ctx -> {
					setLevel(Level.INFO);
					return 1;
				}))
			.then(BrigadierCommand.literalArgumentBuilder("WARN")
				.executes(ctx -> {
					setLevel(Level.WARN);
					return 1;
				}))
			.then(BrigadierCommand.literalArgumentBuilder("ERROR")
				.executes(ctx -> {
					setLevel(Level.ERROR);
					return 1;
				}));

		LiteralArgumentBuilder<CommandSource> current = changeloglevel;
		for (int i = commandPath.length - 1; i >= 1; i--) {
			current = BrigadierCommand.literalArgumentBuilder(commandPath[i]).then(current);
		}
		LiteralCommandNode<CommandSource> rootNode =
			BrigadierCommand.literalArgumentBuilder(commandPath[0]).then(current).build();
		BrigadierCommand cmd = new BrigadierCommand(rootNode);
		commandManager.register(commandManager.metaBuilder(cmd).plugin(plugin).build(), cmd);
	}

	public Logger asLogger() {
		return mLogger;
	}

	public void setLevel(Level level) {
		Configurator.setLevel(mLogger.getName(), level);
	}

	public boolean isLevelEnabled(Level level) {
		return mLogger.isEnabled(level);
	}

	/** @deprecated Use {@link #trace(Supplier)} instead. */
	@Deprecated
	public void finest(Supplier<String> msg) {
		if (mLogger.isTraceEnabled()) {
			mLogger.trace(msg.get());
		}
	}

	/** @deprecated Use {@link #trace(String)} instead. */
	@Deprecated
	public void finest(String msg) {
		mLogger.trace(msg);
	}

	/** @deprecated Use {@link #trace(String, Throwable)} instead. */
	@Deprecated
	public void finest(String msg, Throwable throwable) {
		mLogger.trace(msg, throwable);
	}

	/** @deprecated Use {@link #trace(Supplier)} instead. */
	@Deprecated
	public void finer(Supplier<String> msg) {
		if (mLogger.isTraceEnabled()) {
			mLogger.trace(msg.get());
		}
	}

	/** @deprecated Use {@link #trace(String)} instead. */
	@Deprecated
	public void finer(String msg) {
		mLogger.trace(msg);
	}

	/** @deprecated Use {@link #trace(String, Throwable)} instead. */
	@Deprecated
	public void finer(String msg, Throwable throwable) {
		mLogger.trace(msg, throwable);
	}

	/** @deprecated Use {@link #debug(Supplier)} instead. */
	@Deprecated
	public void fine(Supplier<String> msg) {
		if (mLogger.isDebugEnabled()) {
			mLogger.debug(msg.get());
		}
	}

	/** @deprecated Use {@link #debug(String)} instead. */
	@Deprecated
	public void fine(String msg) {
		mLogger.debug(msg);
	}

	/** @deprecated Use {@link #debug(String, Throwable)} instead. */
	@Deprecated
	public void fine(String msg, Throwable throwable) {
		mLogger.debug(msg, throwable);
	}

	public void trace(Supplier<String> msg) {
		if (mLogger.isTraceEnabled()) {
			mLogger.trace(msg.get());
		}
	}

	public void trace(String msg) {
		mLogger.trace(msg);
	}

	public void trace(String msg, Throwable throwable) {
		mLogger.trace(msg, throwable);
	}

	public void debug(Supplier<String> msg) {
		if (mLogger.isDebugEnabled()) {
			mLogger.debug(msg.get());
		}
	}

	public void debug(String msg) {
		mLogger.debug(msg);
	}

	public void debug(String msg, Throwable throwable) {
		mLogger.debug(msg, throwable);
	}

	public void info(String msg) {
		mLogger.info(msg);
	}

	public void info(Supplier<String> msg) {
		if (mLogger.isInfoEnabled()) {
			mLogger.info(msg.get());
		}
	}

	public void info(String msg, Throwable throwable) {
		mLogger.info(msg, throwable);
	}

	public void warning(Supplier<String> msg) {
		if (mLogger.isWarnEnabled()) {
			mLogger.warn(msg.get());
		}
	}

	public void warning(String msg) {
		mLogger.warn(msg);
	}

	public void warning(String msg, Throwable throwable) {
		mLogger.warn(msg, throwable);
	}

	public void severe(Supplier<String> msg) {
		if (mLogger.isErrorEnabled()) {
			mLogger.error(msg.get());
		}
	}

	public void severe(String msg) {
		mLogger.error(msg);
	}

	public void severe(String msg, Throwable throwable) {
		mLogger.error(msg, throwable);
	}
}
