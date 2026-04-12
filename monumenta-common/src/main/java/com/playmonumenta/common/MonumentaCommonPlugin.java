package com.playmonumenta.common;

import org.bukkit.plugin.java.JavaPlugin;

public class MonumentaCommonPlugin extends JavaPlugin {
	@Override
	public void onEnable() {
		getLogger().info("MonumentaCommon enabled");
	}

	@Override
	public void onDisable() {
		getLogger().info("MonumentaCommon disabled");
	}
}
