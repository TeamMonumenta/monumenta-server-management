package com.playmonumenta.common;

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
 * to expose a level-change command in-game.
 *
 * <p>Paper registers {@code /changeloglevel <label> TRACE|DEBUG|INFO|WARN|ERROR}.
 * Velocity registers {@code /changeloglevelvelocity <label> TRACE|DEBUG|INFO|WARN|ERROR}.
 *
 * <p>Paper example — derive the name from the plugin itself so it can never diverge from
 * plugin.yml:
 * <pre>{@code
 * public void onEnable() {
 *     MMLog log = new MMLog(getName());   // getName() == plugin.yml "name:" field
 *     MMLogPaper.registerCommand(log);
 * }
 * }</pre>
 *
 * <p>Velocity example — pass the string directly; it lives right next to the {@code @Plugin}
 * annotation so drift is easy to spot:
 * <pre>{@code
 * // @Plugin(name = "MyPlugin", ...)
 * public void onProxyInit(ProxyInitializeEvent event) {
 *     MMLog log = new MMLog("MyPlugin");
 *     MMLogVelocity.registerCommand(log, mServer.getCommandManager(), this);
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
	 * Creates a logger backed by log4j2 using {@code pluginName} as the logger name.
	 *
	 * <p>Use the same {@code pluginName} on both Paper and Velocity so that log4j2 level
	 * changes (e.g. via config or the {@code changeloglevel} command) apply regardless of
	 * which platform the plugin is running on. The value must match {@code JavaPlugin.getName()}
	 * on Paper (e.g. {@code "MonumentaNetworkRelay"}) and must be passed as-is to
	 * {@code MMLogPaper.registerCommand} / {@code MMLogVelocity.registerCommand} so that
	 * in-game commands and {@code log4j2.xml} {@code <Logger name="...">} entries all refer
	 * to the same logger.
	 *
	 * @param pluginName log4j2 logger name; should match across Paper and Velocity deployments
	 */
	public MMLog(String pluginName) {
		mLogger = LogManager.getLogger(pluginName);
	}

	public Logger asLogger() {
		return mLogger;
	}

	/** Returns the log4j2 logger name (the {@code pluginName} passed to the constructor). */
	public String getName() {
		return mLogger.getName();
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
