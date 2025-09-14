package com.playmonumenta.networkrelay.config;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public class CommonConfig {
	public static final Level DEFAULT_LOG_LEVEL = Level.INFO;
	public static final String DEFAULT_SHARD_NAME = "default-shard";
	public static final String DEFAULT_RABBIT_URI = "amqp://guest:guest@127.0.0.1:5672";
	public static final int DEFAULT_HEARTBEAT_INTERVAL = 1;
	public static final int DEFAULT_DESTINATION_TIMEOUT = 5;
	public static final long DEFAULT_DEFAULT_TTL = 604800L;

	public Level mLogLevel = DEFAULT_LOG_LEVEL;
	public String mShardName = DEFAULT_SHARD_NAME;
	public String mRabbitUri = DEFAULT_RABBIT_URI;
	public int mHeartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
	public int mDestinationTimeout = DEFAULT_DESTINATION_TIMEOUT;
	public long mDefaultTtl = DEFAULT_DEFAULT_TTL;

	protected void loadCommon(Logger logger, Map<String, Object> config) {
		Level logLevel = null;
		String logLevelStr = getString(config, "log-level", "INFO");
		try {
			logLevel = Level.parse(logLevelStr);
			logger.setLevel(logLevel);
			logger.info("log-level=" + logLevelStr);
		} catch (Exception unused) {
			logger.warning("log-level=" + logLevelStr + " is invalid - defaulting to INFO");
		}
		if (logLevel == null) {
			logLevel = DEFAULT_LOG_LEVEL;
		}
		mLogLevel = logLevel;

		/* Shard name defaults to environment variable NETWORK_RELAY_NAME if present */
		String shardName = System.getenv("NETWORK_RELAY_NAME");
		if (shardName == null || shardName.isEmpty()) {
			shardName = DEFAULT_SHARD_NAME;
		}
		shardName = getString(config, "shard-name", shardName);
		mShardName = shardName;
		if (DEFAULT_SHARD_NAME.equals(mShardName)) {
			logger.warning("shard-name is default value 'default-shard' which should be changed!");
		} else {
			logger.info("shard-name=" + mShardName);
		}

		mRabbitUri = getString(config, "rabbitmq-uri", DEFAULT_RABBIT_URI);
		if (DEFAULT_RABBIT_URI.equals(mRabbitUri)) {
			logger.info("rabbitmq-uri=<default>");
		} else {
			logger.info("rabbitmq-uri=<set>");
		}

		mHeartbeatInterval = getInt(config, "heartbeat-interval", DEFAULT_HEARTBEAT_INTERVAL);
		if (mHeartbeatInterval <= 0) {
			logger.warning("heartbeat-interval is <= 0 which is invalid! Using default of "
				+ DEFAULT_HEARTBEAT_INTERVAL + ".");
			mHeartbeatInterval = DEFAULT_HEARTBEAT_INTERVAL;
		} else {
			logger.info("heartbeat-interval=" + mHeartbeatInterval);
		}

		mDestinationTimeout = getInt(config, "destination-timeout", DEFAULT_DESTINATION_TIMEOUT);
		if (mDestinationTimeout <= 1) {
			logger.warning("destination-timeout is <= 1 which is invalid! Using default of "
				+ DEFAULT_DESTINATION_TIMEOUT + ".");
			mDestinationTimeout = DEFAULT_DESTINATION_TIMEOUT;
		} else {
			logger.info("destination-timeout=" + mDestinationTimeout);
		}

		mDefaultTtl = getLong(config, "default-time-to-live", DEFAULT_DEFAULT_TTL);
		if (mDefaultTtl < 0L) {
			logger.warning("default-time-to-live is < 0 which is invalid! Using default of "
				+ DEFAULT_DEFAULT_TTL + ".");
			mDefaultTtl = DEFAULT_DEFAULT_TTL;
		} else {
			logger.info("default-time-to-live=" + mDefaultTtl);
		}
	}

	public static String getString(Map<String, Object> config, String key, String fallback) {
		return getString(config, key, fallback, false);
	}

	public static String getString(Map<String, Object> config, String key, String fallback, boolean allowEmpty) {
		Object o = config.get(key);
		if (!(o instanceof String)) {
			return fallback;
		}
		String result = (String) o;
		if (!allowEmpty && result.isEmpty()) {
			return fallback;
		}
		return result;
	}

	public static boolean getBoolean(Map<String, Object> config, String key, boolean fallback) {
		Object o = config.get(key);
		if (o instanceof Boolean) {
			return (boolean) o;
		}
		return fallback;
	}

	public static int getInt(Map<String, Object> config, String key, int fallback) {
		Object o = config.get(key);
		if (o instanceof Integer) {
			return (int) o;
		}
		return fallback;
	}

	public static long getLong(Map<String, Object> config, String key, long fallback) {
		Object o = config.get(key);
		if (o instanceof Long) {
			return (long) o;
		}
		if (o instanceof Integer) {
			return (int) o;
		}
		return fallback;
	}
}
