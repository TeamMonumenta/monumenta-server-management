package com.playmonumenta.redissync;

import com.playmonumenta.redissync.config.BukkitConfig;
import com.playmonumenta.redissync.config.CommonConfig;

public class ConfigAPI {
	@Deprecated()
	public static String getRedisHost() {
		return CommonConfig.getRedisHost();
	}

	@Deprecated()
	public static int getRedisPort() {
		return CommonConfig.getRedisPort();
	}

	/**
	 * Returns the current server domain as set in the config file for this plugin.
	 * <p>
	 * This domain info is useful as a prefix for redis keys so that multiple different types of
	 * servers can share the same redis database without intermingling data
	 */
	@Deprecated()
	public static String getServerDomain() {
		return CommonConfig.getServerDomain();
	}

	@Deprecated()
	public static String getShardName() {
		return CommonConfig.getShardName();
	}

	@Deprecated()
	public static int getHistoryAmount() {
		return BukkitConfig.getHistoryAmount();
	}

	@Deprecated()
	public static int getTicksPerPlayerAutosave() {
		return BukkitConfig.getTicksPerPlayerAutosave();
	}

	@Deprecated()
	public static boolean getSavingDisabled() {
		return BukkitConfig.getSavingDisabled();
	}

	@Deprecated()
	public static boolean getScoreboardCleanupEnabled() {
		return BukkitConfig.getScoreboardCleanupEnabled();
	}
}
