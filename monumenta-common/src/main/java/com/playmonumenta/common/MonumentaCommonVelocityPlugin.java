package com.playmonumenta.common;

import com.google.inject.Inject;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.proxy.ProxyServer;

@Plugin(
	id = "monumenta-common",
	name = "MonumentaCommon",
	version = "",
	url = "",
	description = "",
	authors = {""}
)
public class MonumentaCommonVelocityPlugin {
	private final ProxyServer mServer;

	@Inject
	public MonumentaCommonVelocityPlugin(ProxyServer server) {
		mServer = server;
	}

	@Subscribe
	public void onProxyInit(ProxyInitializeEvent event) {
		MMLog log = new MMLog(MonumentaCommonPlugin.PLUGIN_ID);
		log.registerVelocityCommand(mServer.getCommandManager(), this, "monumentaCommon");
	}
}
