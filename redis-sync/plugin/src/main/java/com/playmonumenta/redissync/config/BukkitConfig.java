package com.playmonumenta.redissync.config;

import com.playmonumenta.redissync.RedisConfig;

public final class BukkitConfig extends RedisConfig {
	private final int mHistoryAmount;
	private final int mTicksPerPlayerAutosave;
	private final boolean mSavingDisabled;
	private final boolean mScoreboardCleanupEnabled;

	public BukkitConfig(String redisHost, int redisPort, String serverDomain, String shardName, int historyAmount,
						int ticksPerPlayerAutosave, boolean savingDisabled, boolean scoreboardCleanupEnabled) {
		super(redisHost, redisPort, serverDomain, shardName);
		mHistoryAmount = historyAmount;
		mTicksPerPlayerAutosave = ticksPerPlayerAutosave;
		mSavingDisabled = savingDisabled;
		mScoreboardCleanupEnabled = scoreboardCleanupEnabled;
	}

	public int getHistoryAmount() {
		return mHistoryAmount;
	}

	public int getTicksPerPlayerAutosave() {
		return mTicksPerPlayerAutosave;
	}

	public boolean isSavingDisabled() {
		return mSavingDisabled;
	}

	public boolean isScoreboardCleanupEnabled() {
		return mScoreboardCleanupEnabled;
	}
}
