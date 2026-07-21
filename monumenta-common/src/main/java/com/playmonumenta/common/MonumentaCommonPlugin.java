package com.playmonumenta.common;

import com.playmonumenta.common.zones.ZoneManager;
import com.playmonumenta.common.zones.commands.DebugZones;
import com.playmonumenta.common.zones.commands.ShowZones;
import com.playmonumenta.common.zones.commands.TestZone;
import com.playmonumenta.common.zones.listeners.RedisSyncListener;
import com.playmonumenta.common.zones.listeners.WorldListener;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.Nullable;

public class MonumentaCommonPlugin extends JavaPlugin {
	private static @Nullable MonumentaCommonPlugin INSTANCE = null;

	public boolean mShowZonesDynmap = false;
	public boolean mFallbackZoneLookup = false;

	public @MonotonicNonNull ZoneManager mZoneManager;

	@Override
	public void onLoad() {
		DebugZones.register();
		TestZone.register();
		ShowZones.register(this);
	}

	@Override
	public void onEnable() {
		com.playmonumenta.common.utils.MMLog.init(getName());
		MMLogPaper.registerCommand(com.playmonumenta.common.utils.MMLog.getLog());

		INSTANCE = this;
		PluginManager manager = getServer().getPluginManager();
		manager.registerEvents(new RedisSyncListener(), this);
		manager.registerEvents(new WorldListener(this), this);

		mZoneManager = ZoneManager.getInstance();
		mZoneManager.doReload(true);

		/* Load the config 1 tick later to let other plugins load */
		new BukkitRunnable() {
			@Override
			public void run() {
				mZoneManager.reload(Bukkit.getConsoleSender());
			}
		}.runTaskLater(this, 1);

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
