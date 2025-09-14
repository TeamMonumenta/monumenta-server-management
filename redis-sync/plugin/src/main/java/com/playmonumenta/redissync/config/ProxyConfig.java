package com.playmonumenta.redissync.config;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ProxyConfig extends CommonConfig {
	protected static @Nullable ProxyConfig PROXY_INSTANCE = null;

	protected final String mDefaultServer;
	protected final List<String> mExcludedServers = new ArrayList<>();

	public ProxyConfig(
		Logger logger,
		String redisHost,
		int redisPort,
		String serverDomain,
		String shardName,
		String defaultServer,
		List<String> excludedServers
	) {
		super(redisHost, redisPort, serverDomain, shardName);
		mDefaultServer = defaultServer;
		mExcludedServers.addAll(excludedServers);

		logger.info("Configuration:");
		logger.info("  redis_host = {}", (mRedisHost == null ? "null" : mRedisHost));
		logger.info("  redis_port = {}", mRedisPort);
		logger.info("  server_domain = {}", (mServerDomain == null ? "null" : mServerDomain));
		logger.info("  shard_name = {}", (mShardName == null ? "null" : mShardName));
		logger.info("  default_server = {}", (mDefaultServer == null ? "null" : mDefaultServer));
		logger.info("  excluded_servers = [{}]", String.join("  ", mExcludedServers));

		COMMON_INSTANCE = this;
		PROXY_INSTANCE = this;
	}

	public static ProxyConfig getProxyInstance() {
		ProxyConfig proxyConfig = PROXY_INSTANCE;
		if (proxyConfig == null) {
			throw new RuntimeException("ProxyConfig not initialized");
		}
		return proxyConfig;
	}

	public static String getDefaultServer() {
		return getProxyInstance().mDefaultServer;
	}

	public static List<String> getExcludedServers() {
		return new ArrayList<>(getProxyInstance().mExcludedServers);
	}
}
