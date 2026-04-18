package com.playmonumenta.networkchat.utils;

import java.io.File;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.RollingFileAppender;
import org.apache.logging.log4j.core.appender.rolling.CompositeTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.DefaultRolloverStrategy;
import org.apache.logging.log4j.core.appender.rolling.SizeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.appender.rolling.TimeBasedTriggeringPolicy;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.jetbrains.annotations.Nullable;

/**
 * Dedicated chat-only log file.
 *
 * <p>Writes every received chat message to {@code <server-root>/chatlogs/network-chat.log} via a
 * log4j2 {@link RollingFileAppender} attached to a private logger. The logger is non-additive so
 * chat lines never propagate to the root logger (main server console/log).
 */
public final class ChatLogger {
	private static final String LOGGER_NAME = "NetworkChatChatLog";
	private static final String APPENDER_NAME = "NetworkChatChatLogFile";
	private static final String PATTERN = "%d{yyyy-MM-dd HH:mm:ss} %msg%n";
	private static final String MAX_FILE_SIZE = "50 MB";
	private static final String MAX_ROLLOVER_FILES = "20";

	private static @Nullable Logger LOGGER = null;
	private static @Nullable String LOG_PATH = null;

	private ChatLogger() {
	}

	/** Initializes the dedicated chat log file. Safe to call multiple times. */
	public static synchronized void init() {
		if (LOGGER != null) {
			return;
		}

		File dir = new File(System.getProperty("user.dir"), "chatlogs");
		if (!dir.exists() && !dir.mkdirs()) {
			MMLog.warning("Could not create chat log directory: " + dir.getAbsolutePath());
		}
		File logFile = new File(dir, "network-chat.log");
		File patternFile = new File(dir, "network-chat-%d{yyyy-MM-dd}-%i.log.gz");
		LOG_PATH = logFile.getAbsolutePath();

		LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
		Configuration config = ctx.getConfiguration();

		PatternLayout layout = PatternLayout.newBuilder()
			.withPattern(PATTERN)
			.withConfiguration(config)
			.build();

		CompositeTriggeringPolicy policy = CompositeTriggeringPolicy.createPolicy(
			TimeBasedTriggeringPolicy.newBuilder().withInterval(1).build(),
			SizeBasedTriggeringPolicy.createPolicy(MAX_FILE_SIZE)
		);

		DefaultRolloverStrategy strategy = DefaultRolloverStrategy.newBuilder()
			.withMax(MAX_ROLLOVER_FILES)
			.withConfig(config)
			.build();

		RollingFileAppender appender = RollingFileAppender.newBuilder()
			.setName(APPENDER_NAME)
			.withFileName(logFile.getAbsolutePath())
			.withFilePattern(patternFile.getAbsolutePath())
			.setLayout(layout)
			.withPolicy(policy)
			.withStrategy(strategy)
			.setConfiguration(config)
			.build();
		appender.start();
		config.addAppender(appender);

		// additive=false ensures chat lines do NOT propagate to root/console
		LoggerConfig loggerConfig = new LoggerConfig(LOGGER_NAME, Level.INFO, false);
		loggerConfig.addAppender(appender, Level.INFO, null);
		config.addLogger(LOGGER_NAME, loggerConfig);
		ctx.updateLoggers();

		LOGGER = LogManager.getLogger(LOGGER_NAME);
		MMLog.info("NetworkChat chat log file: " + LOG_PATH);
		LOGGER.info("=== NetworkChat chat log opened ===");
	}

	/** Logs one plain-text chat line. No-op if {@link #init()} has not been called. */
	public static void log(String message) {
		Logger logger = LOGGER;
		if (logger == null) {
			return;
		}
		logger.info(message);
	}

	public static @Nullable String getLogPath() {
		return LOG_PATH;
	}
}
