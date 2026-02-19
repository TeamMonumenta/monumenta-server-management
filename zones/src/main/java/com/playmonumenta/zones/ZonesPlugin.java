package com.playmonumenta.zones;

import com.playmonumenta.zones.commands.DebugZones;
import com.playmonumenta.zones.commands.ShowZones;
import com.playmonumenta.zones.commands.TestZone;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.Nullable;

public class ZonesPlugin extends JavaPlugin {
	private static @Nullable ZonesPlugin INSTANCE = null;

	public boolean mShowZonesDynmap = false;
	public boolean mFallbackZoneLookup = false;

	public @MonotonicNonNull ZoneManager mZoneManager;

	private @MonotonicNonNull CustomLogger mLogger = null;

	@Override
	public void onLoad() {
		if (mLogger == null) {
			mLogger = new CustomLogger(super.getLogger(), Level.INFO);
		}

		DebugZones.register();
		TestZone.register();
		ShowZones.register(this);
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		mZoneManager = ZoneManager.getInstance();
		mZoneManager.doReload(this, true);

		/* Load the config 1 tick later to let other plugins load */
		new BukkitRunnable() {
			@Override
			public void run() {
				mZoneManager.reload(ZonesPlugin.this, Bukkit.getConsoleSender());
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
