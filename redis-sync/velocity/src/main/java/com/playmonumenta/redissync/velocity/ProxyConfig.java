package com.playmonumenta.redissync.velocity;

import com.playmonumenta.redissync.RedisConfig;
import java.util.List;
import org.slf4j.Logger;

public final class ProxyConfig extends RedisConfig {
	private final String mDefaultServer;
	private final List<String> mExcludedServers;

	public ProxyConfig(
		String redisHost,
		int redisPort,
		String serverDomain,
		String shardName,
		String defaultServer,
		List<String> excludedServers
	) {
		super(redisHost, redisPort, serverDomain, shardName);
		mDefaultServer = defaultServer;
		mExcludedServers = List.copyOf(excludedServers);
	}

	public String getDefaultServer() {
		return mDefaultServer;
	}

	public List<String> getExcludedServers() {
		return mExcludedServers;
	}
}
