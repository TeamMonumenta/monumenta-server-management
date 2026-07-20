package com.playmonumenta.zones;

import com.playmonumenta.zones.commands.DebugZones;
import com.playmonumenta.zones.commands.ShowZones;
import com.playmonumenta.zones.commands.TestZone;
import com.playmonumenta.zones.utils.MMLog;
import org.bukkit.Bukkit;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.Nullable;

public class ZonesPlugin extends JavaPlugin {
	private static @Nullable ZonesPlugin INSTANCE = null;

	public boolean mShowZonesDynmap = false;
	public boolean mFallbackZoneLookup = false;

	public @MonotonicNonNull ZoneManager mZoneManager;

	@Override
	public void onLoad() {
		MMLog.init(getName());
		com.playmonumenta.common.MMLogPaper.registerCommand(MMLog.getLog());

		DebugZones.register();
		TestZone.register();
		ShowZones.register(this);
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		PluginManager manager = getServer().getPluginManager();
		if (manager.isPluginEnabled("MonumentaRedisSync")) {
			manager.registerEvents(new RedisSyncListener(), this);
		}
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
	}

	public static ZonesPlugin getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("Attempted to access MonumentaZones plugin before it loaded.");
		}
		return INSTANCE;
	}
}
