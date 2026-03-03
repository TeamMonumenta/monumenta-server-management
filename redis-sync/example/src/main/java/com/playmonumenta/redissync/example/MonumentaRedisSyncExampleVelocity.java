package com.playmonumenta.redissync.example;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;
import org.slf4j.Logger;

/* This is an example Velocity plugin that uses MonumentaRedisSync as a dependency */
@Plugin(id = "monumenta-redissync-example", name = "MonumentaRedisSyncExample",
        version = "0.0.1", authors = {"Combustible"},
        dependencies = {@Dependency(id = "monumenta-redisapi")})
public class MonumentaRedisSyncExampleVelocity {
	final ProxyServer mServer;
	final Logger mLogger;

	@Inject
	public MonumentaRedisSyncExampleVelocity(ProxyServer server, Logger logger) {
		mServer = server;
		mLogger = logger;
	}

	@Subscribe
	public void onProxyInit(ProxyInitializeEvent event) {
		mServer.getEventManager().register(this, new ExampleVelocityListener(this));
	}
}
