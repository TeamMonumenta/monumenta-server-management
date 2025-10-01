package com.playmonumenta.redissync;

public abstract class RedisConfig {
	private final String mRedisHost;
	private final int mRedisPort;
	private final String mServerDomain;
	private final String mShardName;

	protected RedisConfig(String redisHost, int redisPort, String serverDomain, String shardName) {
		mRedisHost = redisHost;
		mRedisPort = redisPort;
		mServerDomain = serverDomain;
		mShardName = shardName;
	}

	public int getRedisPort() {
		return mRedisPort;
	}

	public String getRedisHost() {
		return mRedisHost;
	}

	public String getServerDomain() {
		return mServerDomain;
	}

	public String getShardName() {
		return mShardName;
	}
}
