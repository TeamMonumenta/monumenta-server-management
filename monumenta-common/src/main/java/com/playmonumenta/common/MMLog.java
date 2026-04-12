package com.playmonumenta.common;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.config.Configurator;
import org.bukkit.plugin.java.JavaPlugin;

public class MMLog {
	private final Logger mLogger;

	public MMLog(JavaPlugin plugin, String commandName) {
		mLogger = LogManager.getLogger(plugin.getName());
		new CommandAPICommand(commandName)
			.withSubcommand(new CommandAPICommand("changeloglevel")
				.withPermission(CommandPermission.fromString(commandName + ".changeloglevel"))
				.withSubcommand(new CommandAPICommand("INFO")
					.executes((sender, args) -> {
						Configurator.setLevel(mLogger.getName(), Level.INFO);
					}))
				.withSubcommand(new CommandAPICommand("DEBUG")
					.executes((sender, args) -> {
						Configurator.setLevel(mLogger.getName(), Level.DEBUG);
					}))
				.withSubcommand(new CommandAPICommand("TRACE")
					.executes((sender, args) -> {
						Configurator.setLevel(mLogger.getName(), Level.TRACE);
					}))
			).register();
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
