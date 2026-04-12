package com.playmonumenta.common;

import org.bukkit.plugin.java.JavaPlugin;

public class MonumentaCommonPlugin extends JavaPlugin {
	public static final String PLUGIN_ID = "MonumentaCommon";

	@Override
	public void onEnable() {
		MMLog log = new MMLog(PLUGIN_ID);
		log.registerPaperCommand("monumenta", "common");
		getLogger().info("MonumentaCommon enabled");
	}

	@Override
	public void onDisable() {
		getLogger().info("MonumentaCommon disabled");
	}
}
