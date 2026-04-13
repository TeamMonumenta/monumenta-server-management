package com.playmonumenta.networkrelay.util;

import java.util.function.Supplier;
import java.util.logging.Logger;
import org.apache.logging.log4j.Level;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public class MMLog {
	private static @Nullable com.playmonumenta.common.MMLog INSTANCE = null;

	/** Called from {@link com.playmonumenta.networkrelay.NetworkRelay#onLoad()} on Paper. */
	public static void init(JavaPlugin plugin) {
		if (INSTANCE == null) {
			INSTANCE = new com.playmonumenta.common.MMLog("MonumentaNetworkRelay");
			INSTANCE.registerPaperCommand("networkRelay");
		}
	}

	/**
	 * Called on Velocity. Registers the changeLogLevel command via Brigadier.
	 */
	public static void initVelocity(com.velocitypowered.api.proxy.ProxyServer server, Object plugin) {
		if (INSTANCE == null) {
			INSTANCE = new com.playmonumenta.common.MMLog("MonumentaNetworkRelay");
			INSTANCE.registerVelocityCommand(server.getCommandManager(), plugin, "networkRelay");
		}
	}

	/**
	 * Called in Generic (non-Paper, non-Velocity) mode. No changeLogLevel command is registered.
	 * The {@code logger} parameter is unused; log output goes through log4j2 as on other platforms.
	 */
	public static void initFallback(Logger logger) {
		if (INSTANCE == null) {
			INSTANCE = new com.playmonumenta.common.MMLog("MonumentaNetworkRelay");
		}
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
