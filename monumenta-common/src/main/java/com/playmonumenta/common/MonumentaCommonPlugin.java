package com.playmonumenta.common;

import com.playmonumenta.common.commands.GetDateCommand;
import com.playmonumenta.common.commands.RefreshTimeCommand;
import com.playmonumenta.common.commands.TimeWarpCommand;
import com.playmonumenta.common.managers.TimeWarpManager;
import com.playmonumenta.common.utils.DateUtils;
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
	public static final boolean ENABLE_TIME_WARP;

	public boolean mShowZonesDynmap = false;
	public boolean mFallbackZoneLookup = false;

	public @MonotonicNonNull ZoneManager mZoneManager;

	static {
		/*
		 * Reads the environment variable MONUMENTA_ENABLE_TIME_WARP to determine if /timewarp is allowed
		 * If environment variable is not set or 0, disabled. If set and nonzero, enabled.
		 */
		String envEnableTimeWarp = System.getenv("MONUMENTA_ENABLE_TIME_WARP");
		if (envEnableTimeWarp == null || envEnableTimeWarp.isEmpty()) {
			ENABLE_TIME_WARP = false;
		} else {
			boolean val;
			try {
				val = Integer.parseInt(envEnableTimeWarp) != 0;
			} catch (Exception ex) {
				val = false;
			}
			ENABLE_TIME_WARP = val;
		}
	}

	@Override
	public void onLoad() {
		// Zone registration
		DebugZones.register();
		TestZone.register();
		ShowZones.register(this);

		// Time registration
		TimeWarpManager.load();
		TimeWarpCommand.register();
		GetDateCommand.register();
		RefreshTimeCommand.register();
		DateUtils.refreshTime();
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
