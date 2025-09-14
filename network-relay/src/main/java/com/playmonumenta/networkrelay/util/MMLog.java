package com.playmonumenta.networkrelay.util;

import com.playmonumenta.networkrelay.CustomLogger;
import java.util.function.Supplier;
import java.util.logging.Level;

public class MMLog {
	public static void setLevel(Level level) {
		CustomLogger.getInstance()
			.ifPresent(value -> value.setLevel(level));
	}

	public static boolean isLevelEnabled(Level level) {
		return CustomLogger.getInstance()
			.map(customLogger -> level.intValue() >= customLogger.getLevel().intValue())
			.orElse(true);
	}

	public static void finest(Supplier<String> msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.finest(msg));
	}

	public static void finest(String msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.finest(msg));
	}

	public static void finer(Supplier<String> msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.finer(msg));
	}

	public static void finer(String msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.finer(msg));
	}

	public static void fine(Supplier<String> msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.fine(msg));
	}

	public static void fine(String msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.fine(msg));
	}

	public static void info(String msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.info(msg));
	}

	public static void info(Supplier<String> msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.info(msg));
	}

	public static void warning(Supplier<String> msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.warning(msg));
	}

	public static void warning(String msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.warning(msg));
	}

	public static void warning(String msg, Throwable t) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.log(Level.WARNING, msg, t));
	}

	public static void severe(Supplier<String> msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.severe(msg));
	}

	public static void severe(String msg) {
		CustomLogger.getInstance().ifPresent(customLogger -> customLogger.severe(msg));
	}
}
