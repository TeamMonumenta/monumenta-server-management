package com.playmonumenta.common.managers;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.common.MonumentaCommonPlugin;
import com.playmonumenta.common.event.RefreshTimeEvent;
import com.playmonumenta.common.utils.DateUtils;
import com.playmonumenta.common.utils.FileUtils;
import com.playmonumenta.common.utils.MMLog;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;

public class TimeWarpManager {
	public static String CONFIG_NAME = "time_warp.json";
	public static final String TIMEWARP_OFFSET_PATH = "offset_seconds";
	private static long mSecondOffset = 0;

	public static void reset() {
		set(0);
	}

	public static void add(long amount, TemporalUnit unit) {
		set(DateUtils.localDateTime().plus(amount, unit));
	}

	public static void set(LocalDateTime desiredUtcTime) {
		set(desiredUtcTime.toEpochSecond((ZoneOffset) DateUtils.TIMEZONE)
			- DateUtils.trueLocalDateTime().toEpochSecond((ZoneOffset) DateUtils.TIMEZONE));
	}

	public static void set(long offsetSeconds) {
		mSecondOffset = offsetSeconds;
		save();
	}

	public static long get() {
		return mSecondOffset;
	}

	public static void load() {
		if (!MonumentaCommonPlugin.ENABLE_TIME_WARP) {
			reset();
			return;
		}

		File configFile = getConfigFile();
		try {
			JsonObject config = FileUtils.readJson(configFile.getAbsolutePath());
			if (config.get(TIMEWARP_OFFSET_PATH) instanceof JsonPrimitive timewarpPrimitive && timewarpPrimitive.isNumber()) {
				set(timewarpPrimitive.getAsLong());
			} else {
				set(0L);
			}
		} catch (Exception e) {
			MMLog.warning("Failed to load " + CONFIG_NAME + ": " + e.getMessage());
			reset();
		}
	}

	private static void save() {
		// Update real-time-based plugin code
		RefreshTimeEvent refreshTimeEvent = new RefreshTimeEvent();
		refreshTimeEvent.callEvent();
		Bukkit.getServer().sendMessage(Component.text("The hands of time drift, its sands fly ever swift...", NamedTextColor.DARK_AQUA, TextDecoration.ITALIC));

		JsonObject config = new JsonObject();
		config.addProperty(TIMEWARP_OFFSET_PATH, mSecondOffset);
		try {
			FileUtils.writeJson(getConfigFile().getAbsolutePath(), config);
		} catch (IOException e) {
			MMLog.warning("Failed to save " + CONFIG_NAME + ": " + e.getMessage());
		}
	}

	private static File getConfigFile() {
		return new File(MonumentaCommonPlugin.getInstance().getDataFolder(), CONFIG_NAME);
	}
}
