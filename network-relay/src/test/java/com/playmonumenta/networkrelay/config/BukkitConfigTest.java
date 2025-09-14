package com.playmonumenta.networkrelay.config;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BukkitConfigTest {

	private static final String NOT_FOUND_FILENAME = "not_found.yml";
	private static final String EMPTY_FILENAME = "empty.yml";
	private static final String NON_DEFAULT_FILENAME = "non_default_config_bukkit.yml";

	@Test
	@SuppressWarnings("PMD.AvoidUsingHardCodedIP")
	void loadConfigTest() {
		Logger logger = Logger.getLogger(Logger.GLOBAL_LOGGER_NAME);

		File testResources = new File("src/test/resources");
		File nonExistantFile = new File(testResources, NOT_FOUND_FILENAME);
		assertFalse(nonExistantFile.exists(), NOT_FOUND_FILENAME + " exists, but shouldn't");
		File emptyFile = new File(testResources, EMPTY_FILENAME);
		assertTrue(emptyFile.exists(), EMPTY_FILENAME + " does not exist, but should");
		File nonDefaultFile = new File(testResources, NON_DEFAULT_FILENAME);
		assertTrue(nonDefaultFile.exists(), NON_DEFAULT_FILENAME + " does not exist, but should");

		// No file, empty fallback
		BukkitConfig defaultConfig = new BukkitConfig(logger, nonExistantFile, getClass(), EMPTY_FILENAME);
		assertNotNull(defaultConfig);
		assertEquals(CommonConfig.DEFAULT_LOG_LEVEL, defaultConfig.mLogLevel, () ->
			"Uninitialized log level is set to " + defaultConfig.mLogLevel
				+ " instead of default " + CommonConfig.DEFAULT_LOG_LEVEL);
		assertEquals(CommonConfig.DEFAULT_SHARD_NAME, defaultConfig.mShardName, () ->
			"Uninitialized shard name is set to " + defaultConfig.mShardName
				+ " instead of default " + CommonConfig.DEFAULT_SHARD_NAME);
		assertEquals(CommonConfig.DEFAULT_RABBIT_URI, defaultConfig.mRabbitUri, () ->
			"Uninitialized rabbit URI is set to " + defaultConfig.mRabbitUri
				+ " instead of default " + CommonConfig.DEFAULT_RABBIT_URI);
		assertEquals(CommonConfig.DEFAULT_HEARTBEAT_INTERVAL, defaultConfig.mHeartbeatInterval, () ->
			"Uninitialized heartbeat interval is set to " + defaultConfig.mHeartbeatInterval
				+ " instead of default " + CommonConfig.DEFAULT_HEARTBEAT_INTERVAL);
		assertEquals(CommonConfig.DEFAULT_DESTINATION_TIMEOUT, defaultConfig.mDestinationTimeout, () ->
			"Uninitialized destination timeout is set to " + defaultConfig.mDestinationTimeout
				+ " instead of default " + CommonConfig.DEFAULT_DESTINATION_TIMEOUT);
		assertEquals(CommonConfig.DEFAULT_DEFAULT_TTL, defaultConfig.mDefaultTtl, () ->
			"Uninitialized default TTL is set to " + defaultConfig.mDefaultTtl
				+ " instead of default " + CommonConfig.DEFAULT_DEFAULT_TTL);
		assertTrue(defaultConfig.mBroadcastCommandSendingEnabled,
			"Uninitialized broadcast command sending is false instead of default true");
		assertTrue(defaultConfig.mBroadcastCommandReceivingEnabled,
			"Uninitialized broadcast command receiving is false instead of default true");
		assertNull(defaultConfig.mServerAddress, () ->
			"Uninitialized server address is " + defaultConfig.mServerAddress + " instead of default null");

		// Empty file
		BukkitConfig emptyConfig = new BukkitConfig(logger, emptyFile, getClass(), NOT_FOUND_FILENAME);
		assertNotNull(emptyConfig);
		assertEquals(CommonConfig.DEFAULT_LOG_LEVEL, emptyConfig.mLogLevel, () ->
			"Empty log level is set to " + emptyConfig.mLogLevel
				+ " instead of default " + CommonConfig.DEFAULT_LOG_LEVEL);
		assertEquals(CommonConfig.DEFAULT_SHARD_NAME, emptyConfig.mShardName, () ->
			"Empty shard name is set to " + emptyConfig.mShardName
				+ " instead of default " + CommonConfig.DEFAULT_SHARD_NAME);
		assertEquals(CommonConfig.DEFAULT_RABBIT_URI, emptyConfig.mRabbitUri, () ->
			"Empty rabbit URI is set to " + emptyConfig.mRabbitUri
				+ " instead of default " + CommonConfig.DEFAULT_RABBIT_URI);
		assertEquals(CommonConfig.DEFAULT_HEARTBEAT_INTERVAL, emptyConfig.mHeartbeatInterval, () ->
			"Empty heartbeat interval is set to " + emptyConfig.mHeartbeatInterval
				+ " instead of default " + CommonConfig.DEFAULT_HEARTBEAT_INTERVAL);
		assertEquals(CommonConfig.DEFAULT_DESTINATION_TIMEOUT, emptyConfig.mDestinationTimeout, () ->
			"Empty destination timeout is set to " + emptyConfig.mDestinationTimeout
				+ " instead of default " + CommonConfig.DEFAULT_DESTINATION_TIMEOUT);
		assertEquals(CommonConfig.DEFAULT_DEFAULT_TTL, emptyConfig.mDefaultTtl, () ->
			"Empty default TTL is set to " + emptyConfig.mDefaultTtl
				+ " instead of default " + CommonConfig.DEFAULT_DEFAULT_TTL);
		assertTrue(emptyConfig.mBroadcastCommandSendingEnabled,
			"Empty broadcast command sending is false instead of default true");
		assertTrue(emptyConfig.mBroadcastCommandReceivingEnabled,
			"Empty broadcast command receiving is false instead of default true");
		assertNull(emptyConfig.mServerAddress, () ->
			"Empty server address is " + emptyConfig.mServerAddress + " instead of default null");

		// Non-default file
		BukkitConfig nonDefaultConfig = new BukkitConfig(logger, nonDefaultFile, getClass(), NOT_FOUND_FILENAME);
		assertNotNull(nonDefaultConfig);
		assertNotNull(nonDefaultConfig);
		assertEquals(Level.FINE, nonDefaultConfig.mLogLevel, () ->
			"Non-default log level is set to " + nonDefaultConfig.mLogLevel
				+ " instead of requested " + Level.FINE);
		assertEquals("non-default-shard", nonDefaultConfig.mShardName, () ->
			"Non-default shard name is set to " + nonDefaultConfig.mShardName
				+ " instead of requested non-default-shard");
		assertEquals("amqp://user:pass@127.0.0.1:5673", nonDefaultConfig.mRabbitUri, () ->
			"Non-default rabbit URI is set to " + nonDefaultConfig.mRabbitUri
				+ " instead of requested amqp://user:pass@127.0.0.1:5673");
		assertEquals(2, nonDefaultConfig.mHeartbeatInterval, () ->
			"Non-default heartbeat interval is set to " + nonDefaultConfig.mHeartbeatInterval
				+ " instead of requested 2");
		assertEquals(6, nonDefaultConfig.mDestinationTimeout, () ->
			"Non-default destination timeout is set to " + nonDefaultConfig.mDestinationTimeout
				+ " instead of requested 6");
		assertEquals(604801, nonDefaultConfig.mDefaultTtl, () ->
			"Non-default default TTL is set to " + nonDefaultConfig.mDefaultTtl
				+ " instead of requested 604801");
		assertFalse(nonDefaultConfig.mBroadcastCommandSendingEnabled,
			"Empty broadcast command sending is true instead of requested false");
		assertFalse(nonDefaultConfig.mBroadcastCommandReceivingEnabled,
			"Empty broadcast command receiving is true instead of requested false");
		assertEquals("1.2.3.4", nonDefaultConfig.mServerAddress,
			"Empty server address is null instead of default requested \"1.2.3.4\"");

		// EMPTY_FILENAME with NON_DEFAULT_FILENAME fallback
		BukkitConfig emptyWithFallbackConfig = new BukkitConfig(logger, emptyFile, getClass(), NON_DEFAULT_FILENAME);
		assertNotNull(emptyWithFallbackConfig);
		assertEquals(CommonConfig.DEFAULT_LOG_LEVEL, emptyWithFallbackConfig.mLogLevel, () ->
			"Empty (with fallback) log level is set to " + emptyWithFallbackConfig.mLogLevel
				+ " instead of default " + CommonConfig.DEFAULT_LOG_LEVEL);
		assertEquals(CommonConfig.DEFAULT_SHARD_NAME, emptyWithFallbackConfig.mShardName, () ->
			"Empty (with fallback) shard name is set to " + emptyWithFallbackConfig.mShardName
				+ " instead of default " + CommonConfig.DEFAULT_SHARD_NAME);
		assertEquals(CommonConfig.DEFAULT_RABBIT_URI, emptyWithFallbackConfig.mRabbitUri, () ->
			"Empty (with fallback) rabbit URI is set to " + emptyWithFallbackConfig.mRabbitUri
				+ " instead of default " + CommonConfig.DEFAULT_RABBIT_URI);
		assertEquals(CommonConfig.DEFAULT_HEARTBEAT_INTERVAL, emptyWithFallbackConfig.mHeartbeatInterval, () ->
			"Empty (with fallback) heartbeat interval is set to " + emptyWithFallbackConfig.mHeartbeatInterval
				+ " instead of default " + CommonConfig.DEFAULT_HEARTBEAT_INTERVAL);
		assertEquals(CommonConfig.DEFAULT_DESTINATION_TIMEOUT, emptyWithFallbackConfig.mDestinationTimeout, () ->
			"Empty (with fallback) destination timeout is set to " + emptyWithFallbackConfig.mDestinationTimeout
				+ " instead of default " + CommonConfig.DEFAULT_DESTINATION_TIMEOUT);
		assertEquals(CommonConfig.DEFAULT_DEFAULT_TTL, emptyWithFallbackConfig.mDefaultTtl, () ->
			"Empty (with fallback) default TTL is set to " + emptyWithFallbackConfig.mDefaultTtl
				+ " instead of default " + CommonConfig.DEFAULT_DEFAULT_TTL);
		assertTrue(emptyWithFallbackConfig.mBroadcastCommandSendingEnabled,
			"Empty (with fallback) broadcast command sending is false instead of default true");
		assertTrue(emptyWithFallbackConfig.mBroadcastCommandReceivingEnabled,
			"Empty (with fallback) broadcast command receiving is false instead of default true");
		assertNull(emptyWithFallbackConfig.mServerAddress, () ->
			"Empty (with fallback) server address is " + emptyWithFallbackConfig.mServerAddress + " instead of default null");

		// NON_DEFAULT_FILENAME with EMPTY_FILENAME fallback
		BukkitConfig nonDefaultWithFallbackConfig
			= new BukkitConfig(logger, nonDefaultFile, getClass(), EMPTY_FILENAME);
		assertNotNull(nonDefaultWithFallbackConfig);
		assertEquals(Level.FINE, nonDefaultWithFallbackConfig.mLogLevel, () ->
			"Non-default (with fallback) log level is set to " + nonDefaultWithFallbackConfig.mLogLevel
				+ " instead of requested " + Level.FINE);
		assertEquals("non-default-shard", nonDefaultWithFallbackConfig.mShardName, () ->
			"Non-default (with fallback) shard name is set to " + nonDefaultWithFallbackConfig.mShardName
				+ " instead of requested non-default-shard");
		assertEquals("amqp://user:pass@127.0.0.1:5673", nonDefaultWithFallbackConfig.mRabbitUri, () ->
			"Non-default (with fallback) rabbit URI is set to " + nonDefaultWithFallbackConfig.mRabbitUri
				+ " instead of requested amqp://user:pass@127.0.0.1:5673");
		assertEquals(2, nonDefaultWithFallbackConfig.mHeartbeatInterval, () ->
			"Non-default (with fallback) heartbeat interval is set to " + nonDefaultWithFallbackConfig.mHeartbeatInterval
				+ " instead of requested 2");
		assertEquals(6, nonDefaultWithFallbackConfig.mDestinationTimeout, () ->
			"Non-default (with fallback) destination timeout is set to " + nonDefaultWithFallbackConfig.mDestinationTimeout
				+ " instead of requested 6");
		assertEquals(604801, nonDefaultWithFallbackConfig.mDefaultTtl, () ->
			"Non-default (with fallback) default TTL is set to " + nonDefaultWithFallbackConfig.mDefaultTtl
				+ " instead of requested 604801");
		assertFalse(nonDefaultWithFallbackConfig.mBroadcastCommandSendingEnabled,
			"Empty (with fallback) broadcast command sending is true instead of requested false");
		assertFalse(nonDefaultWithFallbackConfig.mBroadcastCommandReceivingEnabled,
			"Empty (with fallback) broadcast command receiving is true instead of requested false");
		assertEquals("1.2.3.4", nonDefaultWithFallbackConfig.mServerAddress,
			"Empty (with fallback) server address is null instead of default requested \"1.2.3.4\"");
	}

}
