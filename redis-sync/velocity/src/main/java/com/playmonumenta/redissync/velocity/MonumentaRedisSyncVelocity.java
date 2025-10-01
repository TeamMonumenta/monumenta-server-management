package com.playmonumenta.redissync.velocity;

import com.google.inject.Inject;
import com.playmonumenta.redissync.RedisAPI;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

@Plugin(id = "monumenta-redis-sync", name = "Monumenta-RedisAPI", version = "", url = "", description = "", authors = {
	""})
public class MonumentaRedisSyncVelocity {
	private final RedisAPI mRedisAPI;
	private final ProxyConfig mConfig;
	public final ProxyServer mServer;
	public final Logger mLogger;

	@Inject
	public MonumentaRedisSyncVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
		mServer = server;
		mLogger = logger;


		/* Needed to tell Netty where it moved to */
		System.setProperty("com.playmonumenta.redissync.internal.netty", "com.playmonumenta.redissync.internal");

		mConfig = loadConfig(dataDirectory);
		mRedisAPI = new RedisAPI(java.util.logging.Logger.getLogger(logger.getName()), mConfig, this::runAsync);
	}

	@Subscribe
	public void onEnable(ProxyInitializeEvent event) {
		mServer.getEventManager().register(this, new VelocityListener(this));
	}

	// we use ProxyShutdownEvent because ListenerClosEvent might fire too early
	@Subscribe(order = PostOrder.LATE)
	public void onDisable(ProxyShutdownEvent event) {
		mRedisAPI.shutdown();
	}

	private ProxyConfig loadConfig(Path path) {
		var config = new ProxyConfig("redis", 6379, "main", "velocity", "", List.of());

		final var loader = YamlConfigurationLoader.builder()
			.path(path.resolve(Path.of("config.yaml"))) // Set where we will load and save to
			.nodeStyle(NodeStyle.BLOCK)
			.build();

		try {
			final var node = loader.load();
			config = new ProxyConfig(
				node.node("redis_host").getString("redis"),
				node.node("redis_port").getInt(6379),
				node.node("server_domain").getString("build"),
				node.node("shard_name").getString("velocity"),
				node.node("default_server").getString(""),
				node.node("excluded_servers").getList(String.class, List.of())
			);
		} catch (ConfigurateException ex) {
			// TODO: may want to shut down the proxy if configuration fails to load
			mLogger.warn("Failed to load config file, using defaults: {}", ex.getMessage());
		}

		try {
			final var node = loader.createNode();
			node.node("redis_host").set(config.getRedisHost());
			node.node("redis_port").set(config.getRedisHost());
			node.node("server_domain").set(config.getRedisHost());
			node.node("shard_name").set(config.getRedisHost());
			node.node("default_server").set(config.getDefaultServer());
			node.node("excluded_servers").set(config.getExcludedServers());
			loader.save(node);
		} catch (ConfigurateException ex) {
			mLogger.warn("Could not save config.yaml", ex);
		}

		return mConfig;
	}

	private void runAsync(Runnable runnable) {
		mServer.getScheduler()
			.buildTask(this, runnable)
			.schedule();
	}
}
