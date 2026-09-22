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
		com.playmonumenta.common.utils.MMLog.init("MonumentaCommon");
		MMLogVelocity.registerCommand(com.playmonumenta.common.utils.MMLog.getLog(), server.getCommandManager(), this);
	}

	@Subscribe
	public void onProxyInit(ProxyInitializeEvent event) {
		MMLog log = new MMLog("MonumentaCommon");
		MMLogVelocity.registerCommand(log, mServer.getCommandManager(), this);
	}
}
