package com.playmonumenta.common;

import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public class MonumentaCommonPlugin extends JavaPlugin {
	private static @Nullable MonumentaCommonPlugin INSTANCE = null;

	@Override
	public void onEnable() {
		com.playmonumenta.common.utils.MMLog.init(getName());
		MMLogPaper.registerCommand(com.playmonumenta.common.utils.MMLog.getLog());

		INSTANCE = this;
		getLogger().info("MonumentaCommon enabled");
	}

	@Override
	public void onDisable() {
		INSTANCE = null;
		getLogger().info("MonumentaCommon disabled");
	}

	public static MonumentaCommonPlugin getInstance() {
		final MonumentaCommonPlugin instance = INSTANCE;
		if (instance == null) {
			throw new RuntimeException("Attempted to access MonumentaCommonPlugin plugin before it loaded.");
		}
		return instance;
	}
}
