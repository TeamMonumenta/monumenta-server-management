package com.playmonumenta.redissync.config;

import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

public class BukkitConfig extends CommonConfig {
	protected static @Nullable BukkitConfig BUKKIT_INSTANCE = null;

	protected final int mHistoryAmount;
	protected final int mTicksPerPlayerAutosave;
	protected final boolean mSavingDisabled;
	protected final boolean mScoreboardCleanupEnabled;

	public BukkitConfig(Logger logger, String redisHost, int redisPort, String serverDomain, String shardName, int historyAmount, int ticksPerPlayerAutosave, boolean savingDisabled, boolean scoreboardCleanupEnabled) {
		super(redisHost, redisPort, serverDomain, shardName);
		mHistoryAmount = historyAmount;
		mTicksPerPlayerAutosave = ticksPerPlayerAutosave;
		mSavingDisabled = savingDisabled;
		mScoreboardCleanupEnabled = scoreboardCleanupEnabled;

		logger.info("Configuration:");
		logger.info("  redis_host = " + (mRedisHost == null ? "null" : mRedisHost));
		logger.info("  redis_port = " + mRedisPort);
		logger.info("  server_domain = " + (mServerDomain == null ? "null" : mServerDomain));
		logger.info("  shard_name = " + (mShardName == null ? "null" : mShardName));
		logger.info("  history_amount = " + mHistoryAmount);
		logger.info("  ticks_per_player_autosave = " + mTicksPerPlayerAutosave);
		logger.info("  saving_disabled = " + mSavingDisabled);
		logger.info("  scoreboard_cleanup_enabled = " + mScoreboardCleanupEnabled);

		COMMON_INSTANCE = this;
		BUKKIT_INSTANCE = this;
	}

	public static BukkitConfig getBukkitInstance() {
		BukkitConfig bukkitConfig = BUKKIT_INSTANCE;
		if (bukkitConfig == null) {
			throw new RuntimeException("BukkitConfig not initialized");
		}
		return bukkitConfig;
	}

	public static int getHistoryAmount() {
		return getBukkitInstance().mHistoryAmount;
	}

	public static int getTicksPerPlayerAutosave() {
		return getBukkitInstance().mTicksPerPlayerAutosave;
	}

	public static boolean getSavingDisabled() {
		return getBukkitInstance().mSavingDisabled;
	}

	public static boolean getScoreboardCleanupEnabled() {
		return getBukkitInstance().mScoreboardCleanupEnabled;
	}
}
