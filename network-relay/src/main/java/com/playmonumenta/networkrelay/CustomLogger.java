package com.playmonumenta.networkrelay;

import java.util.Optional;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

public class CustomLogger extends Logger {
	private static @MonotonicNonNull CustomLogger INSTANCE = null;

	private final Logger mLogger;
	private Level mLevel;

	public CustomLogger(Logger logger, Level level) {
		super(logger.getName(), logger.getResourceBundleName());
		INSTANCE = this;
		mLogger = logger;
		mLevel = level;
	}

	public static Optional<CustomLogger> getInstance() {
		return Optional.ofNullable(INSTANCE);
	}

	@Override
	public void setLevel(Level level) {
		mLevel = level;
	}

	@Override
	public void finest(Supplier<String> msg) {
		if (mLevel.equals(Level.FINEST)) {
			mLogger.info(msg);
		}
	}

	@Override
	public void finest(String msg) {
		if (mLevel.equals(Level.FINEST)) {
			mLogger.info(msg);
		}
	}

	@Override
	public void finer(Supplier<String> msg) {
		if (mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
			mLogger.info(msg);
		}
	}

	@Override
	public void finer(String msg) {
		if (mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
			mLogger.info(msg);
		}
	}

	@Override
	public void fine(Supplier<String> msg) {
		if (mLevel.equals(Level.FINE) || mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
			mLogger.info(msg);
		}
	}

	@Override
	public void fine(String msg) {
		if (mLevel.equals(Level.FINE) || mLevel.equals(Level.FINER) || mLevel.equals(Level.FINEST)) {
			mLogger.info(msg);
		}
	}

	@Override
	public void info(Supplier<String> msg) {
		mLogger.info(msg);
	}

	@Override
	public void info(String msg) {
		mLogger.info(msg);
	}

	@Override
	public void warning(Supplier<String> msg) {
		mLogger.warning(msg);
	}

	@Override
	public void warning(String msg) {
		mLogger.warning(msg);
	}

	@Override
	public void severe(Supplier<String> msg) {
		mLogger.severe(msg);
	}

	@Override
	public void severe(String msg) {
		mLogger.severe(msg);
	}
}
