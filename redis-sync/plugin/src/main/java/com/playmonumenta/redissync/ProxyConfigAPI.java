package com.playmonumenta.redissync;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class ProxyConfigAPI extends CommonConfig {
	protected static @Nullable ProxyConfigAPI PROXY_INSTANCE = null;

	protected final String mDefaultServer;
	protected final List<String> mExcludedServers = new ArrayList<>();

	ProxyConfigAPI(
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

		PROXY_INSTANCE = this;
	}

	private static ProxyConfigAPI getProxyInstance() {
		ProxyConfigAPI proxyConfig = PROXY_INSTANCE;
		if (proxyConfig == null) {
			throw new RuntimeException("ProxyConfigAPI not initialized");
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
