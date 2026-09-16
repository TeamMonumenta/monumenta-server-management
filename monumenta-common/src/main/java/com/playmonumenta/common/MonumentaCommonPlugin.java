package com.playmonumenta.common;

import com.playmonumenta.common.zones.ZoneManager;
import com.playmonumenta.common.zones.commands.DebugZones;
import com.playmonumenta.common.zones.commands.ShowZones;
import com.playmonumenta.common.zones.commands.TestZone;
import com.playmonumenta.common.zones.listeners.RedisSyncListener;
import com.playmonumenta.common.zones.listeners.WorldListener;
import java.io.File;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.Nullable;

public class MonumentaCommonPlugin extends JavaPlugin {
	private static @Nullable MonumentaCommonPlugin INSTANCE = null;

	private @MonotonicNonNull File mConfigFile;
	public boolean mShowZonesDynmap = false;
	public boolean mFallbackZoneLookup = false;

	public @MonotonicNonNull ZoneManager mZoneManager;

	@Override
	public void onLoad() {
		reloadZoneConfigYaml(null);
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

	public void reloadZoneConfigYaml(@Nullable Audience sender) {
		if (mConfigFile == null) {
			mConfigFile = new File(getDataFolder(), "zone_config.yml");
		}

		FileConfiguration config = YamlConfiguration.loadConfiguration(mConfigFile);

		if (config.isBoolean("show_zones_dynmap")) {
			mShowZonesDynmap = config.getBoolean("show_zones_dynmap", false);
		} else {
			mShowZonesDynmap = false;
		}
		if (sender != null) {
			sender.sendMessage(Component.text("show_zones_dynmap: " + mShowZonesDynmap));
		}

		if (config.isBoolean("fallback_zone_lookup")) {
			mFallbackZoneLookup = config.getBoolean("fallback_zone_lookup", false);
		} else {
			mFallbackZoneLookup = false;
		}
		if (sender != null) {
			sender.sendMessage(Component.text("fallback_zone_lookup: " + mFallbackZoneLookup));
		}
	}
}
