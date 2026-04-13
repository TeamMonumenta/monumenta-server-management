package com.playmonumenta.networkrelay.util;

import java.util.function.Supplier;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;

public class MMLog {
	static final String PLUGIN_ID = "MonumentaNetworkRelay";
	private static @Nullable com.playmonumenta.common.MMLog INSTANCE = null;

	/**
	 * Creates the logger instance. Call once from the platform-specific plugin entry point before
	 * any logging call. After calling this, register the changeLogLevel command via the appropriate
	 * platform helper:
	 * <ul>
	 *   <li>Paper: {@code com.playmonumenta.common.MMLogPaper.registerCommand(MMLog.getLog(), "networkRelay")}
	 *   <li>Velocity: {@code com.playmonumenta.common.MMLogVelocity.registerCommand(MMLog.getLog(), commandManager, plugin, "networkRelay")}
	 * </ul>
	 */
	public static void init() {
		if (INSTANCE == null) {
			INSTANCE = new com.playmonumenta.common.MMLog(PLUGIN_ID);
		}
	}

	/** Returns the underlying {@link com.playmonumenta.common.MMLog} for command registration. */
	public static com.playmonumenta.common.MMLog getLog() {
		return get();
	}

	private static com.playmonumenta.common.MMLog get() {
		if (INSTANCE == null) {
			throw new RuntimeException("NetworkRelay logger invoked before being initialized!");
		}
		return INSTANCE;
	}

	public static void setLevel(Level level) {
		get().setLevel(level);
	}

	public static boolean isLevelEnabled(Level level) {
		return get().isLevelEnabled(level);
	}

	/** @deprecated Use {@link #trace(Supplier)} instead. */
	@Deprecated
	public static void finest(Supplier<String> msg) {
		get().trace(msg);
	}

	/** @deprecated Use {@link #trace(String)} instead. */
	@Deprecated
	public static void finest(String msg) {
		get().trace(msg);
	}

	/** @deprecated Use {@link #trace(Supplier)} instead. */
	@Deprecated
	public static void finer(Supplier<String> msg) {
		get().trace(msg);
	}

	/** @deprecated Use {@link #trace(String)} instead. */
	@Deprecated
	public static void finer(String msg) {
		get().trace(msg);
	}

	/** @deprecated Use {@link #debug(Supplier)} instead. */
	@Deprecated
	public static void fine(Supplier<String> msg) {
		get().debug(msg);
	}

	/** @deprecated Use {@link #debug(String)} instead. */
	@Deprecated
	public static void fine(String msg) {
		get().debug(msg);
	}

	public static void trace(Supplier<String> msg) {
		get().trace(msg);
	}

	public static void trace(String msg) {
		get().trace(msg);
	}

	public static void trace(String msg, Throwable throwable) {
		get().trace(msg, throwable);
	}

	public static void debug(Supplier<String> msg) {
		get().debug(msg);
	}

	public static void debug(String msg) {
		get().debug(msg);
	}

	public static void debug(String msg, Throwable throwable) {
		get().debug(msg, throwable);
	}

	public static void info(String msg) {
		get().info(msg);
	}

	public static void info(Supplier<String> msg) {
		get().info(msg);
	}

	public static void warning(Supplier<String> msg) {
		get().warning(msg);
	}

	public static void warning(String msg) {
		get().warning(msg);
	}

	public static void warning(String msg, Throwable throwable) {
		get().warning(msg, throwable);
	}

	public static void severe(Supplier<String> msg) {
		get().severe(msg);
	}

	public static void severe(String msg) {
		get().severe(msg);
	}

	public static void severe(String msg, Throwable throwable) {
		get().severe(msg, throwable);
	}
}
