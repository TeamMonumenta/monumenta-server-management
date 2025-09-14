package com.playmonumenta.networkrelay.config;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CommonConfigTest {

	@Test
	void loadCommon() {
		// Uninitialized
		CommonConfig config = new CommonConfig();
		assertEquals(CommonConfig.DEFAULT_LOG_LEVEL, config.mLogLevel, () ->
			"Uninitialized log level is set to " + config.mLogLevel
				+ " instead of default " + CommonConfig.DEFAULT_LOG_LEVEL);
		assertEquals(CommonConfig.DEFAULT_SHARD_NAME, config.mShardName, () ->
			"Uninitialized shard name is set to " + config.mShardName
				+ " instead of default " + CommonConfig.DEFAULT_SHARD_NAME);
		assertEquals(CommonConfig.DEFAULT_RABBIT_URI, config.mRabbitUri, () ->
			"Uninitialized rabbit URI is set to " + config.mRabbitUri
				+ " instead of default " + CommonConfig.DEFAULT_RABBIT_URI);
		assertEquals(CommonConfig.DEFAULT_HEARTBEAT_INTERVAL, config.mHeartbeatInterval, () ->
			"Uninitialized heartbeat interval is set to " + config.mHeartbeatInterval
				+ " instead of default " + CommonConfig.DEFAULT_HEARTBEAT_INTERVAL);
		assertEquals(CommonConfig.DEFAULT_DESTINATION_TIMEOUT, config.mDestinationTimeout, () ->
			"Uninitialized destination timeout is set to " + config.mDestinationTimeout
				+ " instead of default " + CommonConfig.DEFAULT_DESTINATION_TIMEOUT);
		assertEquals(CommonConfig.DEFAULT_DEFAULT_TTL, config.mDefaultTtl, () ->
			"Uninitialized default TTL is set to " + config.mDefaultTtl
				+ " instead of default " + CommonConfig.DEFAULT_DEFAULT_TTL);

		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

		// Initialized
		Map<String, Object> configMap = new HashMap<>();
		config.loadCommon(logger, configMap);

		assertEquals(CommonConfig.DEFAULT_LOG_LEVEL, config.mLogLevel, () ->
			"Undefined log level is set to " + config.mLogLevel
				+ " instead of default " + CommonConfig.DEFAULT_LOG_LEVEL);
		assertEquals(CommonConfig.DEFAULT_SHARD_NAME, config.mShardName, () ->
			"Undefined shard name is set to " + config.mShardName
				+ " instead of default " + CommonConfig.DEFAULT_SHARD_NAME);
		assertEquals(CommonConfig.DEFAULT_RABBIT_URI, config.mRabbitUri, () ->
			"Undefined rabbit URI is set to " + config.mRabbitUri
				+ " instead of default " + CommonConfig.DEFAULT_RABBIT_URI);
		assertEquals(CommonConfig.DEFAULT_HEARTBEAT_INTERVAL, config.mHeartbeatInterval, () ->
			"Undefined heartbeat interval is set to " + config.mHeartbeatInterval
				+ " instead of default " + CommonConfig.DEFAULT_HEARTBEAT_INTERVAL);
		assertEquals(CommonConfig.DEFAULT_DESTINATION_TIMEOUT, config.mDestinationTimeout, () ->
			"Undefined destination timeout is set to " + config.mDestinationTimeout
				+ " instead of default " + CommonConfig.DEFAULT_DESTINATION_TIMEOUT);
		assertEquals(CommonConfig.DEFAULT_DEFAULT_TTL, config.mDefaultTtl, () ->
			"Undefined default TTL is set to " + config.mDefaultTtl
				+ " instead of default " + CommonConfig.DEFAULT_DEFAULT_TTL);

		// Invalid options
		configMap.put("log-level", "WHAT");
		configMap.put("heartbeat-interval", -1);
		configMap.put("destination-timeout", -1);
		configMap.put("default-time-to-live", -1);
		CommonConfig invalidConfig = new CommonConfig();
		invalidConfig.loadCommon(logger, configMap);

		assertEquals(CommonConfig.DEFAULT_LOG_LEVEL, invalidConfig.mLogLevel, () ->
			"Invalid log level is set to " + invalidConfig.mLogLevel
				+ " instead of default " + CommonConfig.DEFAULT_LOG_LEVEL);
		assertEquals(CommonConfig.DEFAULT_SHARD_NAME, invalidConfig.mShardName, () ->
			"Invalid-ish shard name is set to " + invalidConfig.mShardName
				+ " instead of default " + CommonConfig.DEFAULT_SHARD_NAME);
		assertEquals(CommonConfig.DEFAULT_RABBIT_URI, invalidConfig.mRabbitUri, () ->
			"Invalid-ish rabbit URI is set to " + invalidConfig.mRabbitUri
				+ " instead of default " + CommonConfig.DEFAULT_RABBIT_URI);
		assertEquals(CommonConfig.DEFAULT_HEARTBEAT_INTERVAL, invalidConfig.mHeartbeatInterval, () ->
			"Invalid heartbeat interval is set to " + invalidConfig.mHeartbeatInterval
				+ " instead of default " + CommonConfig.DEFAULT_HEARTBEAT_INTERVAL);
		assertEquals(CommonConfig.DEFAULT_DESTINATION_TIMEOUT, invalidConfig.mDestinationTimeout, () ->
			"Invalid destination timeout is set to " + invalidConfig.mDestinationTimeout
				+ " instead of default " + CommonConfig.DEFAULT_DESTINATION_TIMEOUT);
		assertEquals(CommonConfig.DEFAULT_DEFAULT_TTL, invalidConfig.mDefaultTtl, () ->
			"Invalid default TTL is set to " + invalidConfig.mDefaultTtl
				+ " instead of default " + CommonConfig.DEFAULT_DEFAULT_TTL);

		// Non-default options
		configMap.put("log-level", "FINE");
		configMap.put("shard-name", "non-default-shard");
		configMap.put("rabbitmq-uri", "amqp://user:pass@127.0.0.1:5673");
		configMap.put("heartbeat-interval", 2);
		configMap.put("destination-timeout", 6);
		configMap.put("default-time-to-live", 604801);
		CommonConfig nonDefaultConfig = new CommonConfig();
		nonDefaultConfig.loadCommon(logger, configMap);

		assertEquals(Level.FINE, nonDefaultConfig.mLogLevel, () ->
			"Undefined log level is set to " + nonDefaultConfig.mLogLevel
				+ " instead of default FINE");
		assertEquals("non-default-shard", nonDefaultConfig.mShardName, () ->
			"Undefined shard name is set to " + nonDefaultConfig.mShardName
				+ " instead of default \"non-default-shard\"");
		assertEquals("amqp://user:pass@127.0.0.1:5673", nonDefaultConfig.mRabbitUri, () ->
			"Undefined rabbit URI is set to " + nonDefaultConfig.mRabbitUri
				+ " instead of default \"amqp://user:pass@127.0.0.1:5673\"");
		assertEquals(2, nonDefaultConfig.mHeartbeatInterval, () ->
			"Undefined heartbeat interval is set to " + nonDefaultConfig.mHeartbeatInterval
				+ " instead of default " + 2);
		assertEquals(6, nonDefaultConfig.mDestinationTimeout, () ->
			"Undefined destination timeout is set to " + nonDefaultConfig.mDestinationTimeout
				+ " instead of default " + 6);
		assertEquals(604801, nonDefaultConfig.mDefaultTtl, () ->
			"Undefined default TTL is set to " + nonDefaultConfig.mDefaultTtl
				+ " instead of default " + 604801);
	}

	@Test
	void getString() {
		// Empty map
		Map<String, Object> config = new HashMap<>();
		assertEquals("Not Found",
			CommonConfig.getString(config, "NotFound", "Not Found"),
			"Failed to get fallback of \"Not Found\" from empty config");
		assertEquals("Not Found",
			CommonConfig.getString(config, "NotFound", "Not Found", true),
			"Failed to get fallback of \"Not Found\" from empty config (empty allowed)");

		// Unrelated key
		config.put("Unrelated", "Unrelated");
		assertEquals("Not Found",
			CommonConfig.getString(config, "NotFound", "Not Found"),
			"Failed to get fallback of \"Not Found\" with unrelated config entry");
		assertEquals("Not Found",
			CommonConfig.getString(config, "NotFound", "Not Found", true),
			"Failed to get fallback of \"Not Found\" with unrelated config entry (empty allowed)");

		// Incorrect type
		config.put("IncorrectType", true);
		assertEquals("Incorrect Type",
			CommonConfig.getString(config, "IncorrectType", "Incorrect Type"),
			"Failed to get fallback of \"Incorrect Type\" with incorrect type boolean");
		assertEquals("Incorrect Type",
			CommonConfig.getString(config, "IncorrectType", "Incorrect Type", true),
			"Failed to get fallback of \"Incorrect Type\" with incorrect type boolean (empty allowed)");
		config.put("IncorrectType", 5);
		assertEquals("Incorrect Type",
			CommonConfig.getString(config, "IncorrectType", "Incorrect Type"),
			"Failed to get fallback of \"Incorrect Type\" with incorrect type Integer");
		assertEquals("Incorrect Type",
			CommonConfig.getString(config, "IncorrectType", "Incorrect Type", true),
			"Failed to get fallback of \"Incorrect Type\" with incorrect type Integer (empty allowed)");
		config.put("IncorrectType", 5L);
		assertEquals("Incorrect Type",
			CommonConfig.getString(config, "IncorrectType", "Incorrect Type"),
			"Failed to get fallback of \"Incorrect Type\" with incorrect type Long");
		assertEquals("Incorrect Type",
			CommonConfig.getString(config, "IncorrectType", "Incorrect Type", true),
			"Failed to get fallback of \"Incorrect Type\" with incorrect type Long (empty allowed)");

		config.put("EmptyKey", "");
		assertEquals("Fallback",
			CommonConfig.getString(config, "EmptyKey", "Fallback"),
			"Failed to get \"Fallback\" from key with zero-length string value (empty not allowed)");
		assertEquals("",
			CommonConfig.getString(config, "EmptyKey", "Fallback", true),
			"Failed to get \"\" from key with zero-length string value (empty allowed)");

		config.put("TestKey", "Expected");
		assertEquals("Expected",
			CommonConfig.getString(config, "TestKey", "The Spanish Inquisition"),
			"Failed to get set value of \"Expected\" from config");
		assertEquals("Expected",
			CommonConfig.getString(config, "TestKey", "The Spanish Inquisition", true),
			"Failed to get set value of \"Expected\" from config (empty allowed)");
	}

	@Test
	void getBoolean() {
		// Empty map
		Map<String, Object> config = new HashMap<>();
		assertTrue(CommonConfig.getBoolean(config, "NotFound", true), "Failed to get fallback of false from empty config");
		assertFalse(CommonConfig.getBoolean(config, "NotFound", false), "Failed to get fallback of true from empty config");

		// Unrelated key
		config.put("Unrelated", true);
		assertTrue(CommonConfig.getBoolean(config, "NotFound", true), "Failed to get fallback of false with unrelated config entry");
		assertFalse(CommonConfig.getBoolean(config, "NotFound", false), "Failed to get fallback of true with unrelated config entry");
		config.put("Unrelated", false);
		assertTrue(CommonConfig.getBoolean(config, "NotFound", true), "Failed to get fallback of false with unrelated config entry");
		assertFalse(CommonConfig.getBoolean(config, "NotFound", false), "Failed to get fallback of true with unrelated config entry");

		// Incorrect type
		config.put("IncorrectType", "Test String");
		assertTrue(CommonConfig.getBoolean(config, "IncorrectType", true), "Failed to get fallback of false with incorrect type String");
		assertFalse(CommonConfig.getBoolean(config, "IncorrectType", false), "Failed to get fallback of true with incorrect type String");
		config.put("IncorrectType", 5);
		assertTrue(CommonConfig.getBoolean(config, "IncorrectType", true), "Failed to get fallback of false with incorrect type Integer");
		assertFalse(CommonConfig.getBoolean(config, "IncorrectType", false), "Failed to get fallback of true with incorrect type Integer");
		config.put("IncorrectType", 5L);
		assertTrue(CommonConfig.getBoolean(config, "IncorrectType", true), "Failed to get fallback of false with incorrect type Long");
		assertFalse(CommonConfig.getBoolean(config, "IncorrectType", false), "Failed to get fallback of true with incorrect type Long");

		// Expecting true
		config.put("TestKey", true);
		assertTrue(CommonConfig.getBoolean(config, "TestKey", true), "Failed to get set value of true from config");
		assertTrue(CommonConfig.getBoolean(config, "TestKey", false), "Failed to get set value of true from config");

		// Expecting false
		config.put("TestKey", false);
		assertFalse(CommonConfig.getBoolean(config, "TestKey", true), "Failed to get set value of false from config");
		assertFalse(CommonConfig.getBoolean(config, "TestKey", false), "Failed to get set value of false from config");
	}

	@Test
	void getInt() {
		List<Integer> intValues = new ArrayList<>();
		intValues.add(0);
		intValues.add(-1);
		intValues.add(1);
		intValues.add(2);
		intValues.add(Integer.MIN_VALUE);
		intValues.add(Integer.MAX_VALUE);

		// Empty map
		Map<String, Object> config = new HashMap<>();
		for (int fallback : intValues) {
			int actual = CommonConfig.getInt(config, "NotFound", fallback);
			assertEquals(fallback, actual, () ->
				"Got " + actual
					+ " instead of " + fallback
					+ " from empty config");
		}

		// Unrelated entry
		config.put("Unrelated", 5);
		for (int fallback : intValues) {
			int actual = CommonConfig.getInt(config, "NotFound", fallback);
			assertEquals(fallback, actual, () ->
				"Got " + actual
					+ " instead of " + fallback
					+ " from unrelated config entry");
		}

		// Incorrect type
		config.put("IncorrectType", "Test String");
		for (int fallback : intValues) {
			int actual = CommonConfig.getInt(config, "IncorrectType", fallback);
			assertEquals(fallback, actual, () ->
				"Got " + actual
					+ " instead of "
					+ fallback + " from incorrect type String");
		}
		config.put("IncorrectType", true);
		for (int fallback : intValues) {
			int actual = CommonConfig.getInt(config, "IncorrectType", fallback);
			assertEquals(fallback, actual, () ->
				"Got " + actual
					+ " instead of "
					+ fallback + " from incorrect type Boolean");
		}
		config.put("IncorrectType", 1L);
		for (int fallback : intValues) {
			int actual = CommonConfig.getInt(config, "IncorrectType", fallback);
			assertEquals(fallback, actual, () ->
				"Got " + actual
					+ " instead of " + fallback
					+ " from incorrect type Long");
		}

		// Expecting value
		for (int expected : intValues) {
			config.put("Expected", expected);
			for (int fallback : intValues) {
				int actual = CommonConfig.getInt(config, "Expected", fallback);
				assertEquals(expected, actual, () ->
					"Got " + actual
						+ " instead of " + expected
						+ " with expected value and fallback of " + fallback);
			}
		}
	}

	@Test
	void getLong() {
		List<Long> longValues = new ArrayList<>();
		longValues.add(0L);
		longValues.add(-1L);
		longValues.add(1L);
		longValues.add(2L);
		longValues.add(Long.MIN_VALUE);
		longValues.add(Long.MAX_VALUE);

		List<Integer> intValues = new ArrayList<>();
		intValues.add(0);
		intValues.add(-1);
		intValues.add(1);
		intValues.add(2);
		intValues.add(Integer.MIN_VALUE);
		intValues.add(Integer.MAX_VALUE);

		// Empty map
		Map<String, Object> config = new HashMap<>();
		for (long fallback : longValues) {
			long actual = CommonConfig.getLong(config, "NotFound", fallback);
			assertEquals(fallback, actual, () ->
				"Got " + actual
					+ " instead of " + fallback
					+ " from empty config");
		}

		// Unrelated entry
		config.put("Unrelated", 5L);
		for (long fallback : longValues) {
			long actual = CommonConfig.getLong(config, "NotFound", fallback);
			assertEquals(fallback, actual, () ->
				"Got " + actual
					+ " instead of " + fallback
					+ " from unrelated config entry");
		}

		// Incorrect type
		config.put("IncorrectType", "Test String");
		for (long fallback : longValues) {
			long actual = CommonConfig.getLong(config, "IncorrectType", fallback);
			assertEquals(fallback, actual, () ->
				"Got " + actual
					+ " instead of "
					+ fallback + " from incorrect type String");
		}
		config.put("IncorrectType", true);
		for (long fallback : longValues) {
			long actual = CommonConfig.getLong(config, "IncorrectType", fallback);
			assertEquals(fallback, actual, () ->
				"Got " + actual
					+ " instead of "
					+ fallback + " from incorrect type Boolean");
		}

		// Acceptable equivalent type Integer
		for (int expected : intValues) {
			config.put("AcceptableType", expected);
			for (long fallback : longValues) {
				long actual = CommonConfig.getLong(config, "AcceptableType", fallback);
				assertEquals(expected, actual, () ->
					"Got " + actual
						+ " instead of " + expected
						+ " from acceptable type Integer");
			}
		}

		// Expecting value
		for (long expected : longValues) {
			config.put("Expected", expected);
			for (long fallback : longValues) {
				long actual = CommonConfig.getLong(config, "Expected", fallback);
				assertEquals(expected, actual, () ->
					"Got " + actual
						+ " instead of " + expected
						+ " with expected value and fallback of " + fallback);
			}
		}
	}
}
