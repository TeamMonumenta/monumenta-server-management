package com.playmonumenta.redissync.config;

import org.jetbrains.annotations.Nullable;

public class CommonConfig {
	protected static @Nullable CommonConfig COMMON_INSTANCE = null;

	protected final String mRedisHost;
	protected final int mRedisPort;
	protected final String mServerDomain;
	protected final String mShardName;

	public CommonConfig(String redisHost, int redisPort, String serverDomain, String shardName) {
		mRedisHost = redisHost;
		mRedisPort = redisPort;
		mServerDomain = serverDomain;
		mShardName = shardName;

	}

	public static CommonConfig getCommonInstance() {
		CommonConfig commonConfig = COMMON_INSTANCE;
		if (commonConfig == null) {
			throw new RuntimeException("CommonConfig not initialized");
		}
		return commonConfig;
	}

	public static String getRedisHost() {
		return getCommonInstance().mRedisHost;
	}

	public static int getRedisPort() {
		return getCommonInstance().mRedisPort;
	}

	/**
	 * Returns the current server domain as set in the config file for this plugin.
	 * <p>
	 * This domain info is useful as a prefix for redis keys so that multiple different types of
	 * servers can share the same redis database without intermingling data
	 */
	public static String getServerDomain() {
		return getCommonInstance().mServerDomain;
	}

	public static String getShardName() {
		return getCommonInstance().mShardName;
	}
}
