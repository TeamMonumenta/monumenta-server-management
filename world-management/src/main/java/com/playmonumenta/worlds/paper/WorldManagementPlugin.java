package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.CustomLogger;
import com.playmonumenta.worlds.common.MMLog;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class WorldManagementPlugin extends JavaPlugin {
	private static @Nullable WorldManagementPlugin INSTANCE = null;

	private static @Nullable CustomLogger mLogger = null;
	private static boolean mSortWorldByScoreOnJoin = false;
	private static boolean mSortWorldByScoreOnRespawn = false;
	private static boolean mAllowInstanceAutocreation = false;
	private static int mUnloadInactiveWorldAfterTicks = 10 * 60 * 20;
	private static @Nullable String mNotifyWorldPermission = "monumenta.worldmanagement.worldnotify";
	private static String mCopyWorldCommand = "cp -a";
	private static final Map<String, ShardInfo> mShardInfoMap = new HashMap<>();

	private @Nullable WorldManagementListener mListener = null;
	private @Nullable WorldGenerator mGenerator = null;

	@Override
	public void onLoad() {
		WorldCommands.register(this);
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		getWorldGenerator();

		loadConfig();

		getListener();
		Bukkit.getPluginManager().registerEvents(mListener, this);

		MonumentaWorldManagementAPI.refreshCachedAvailableWorlds();
	}

	protected void loadConfig() {
		File configFile = new File(getDataFolder(), "config.yml");

		/* Create the config file & directories if it does not exist */
		if (!configFile.exists()) {
			try {
				// Create parent directories if they do not exist
				configFile.getParentFile().mkdirs();

				// Copy the default config file
				InputStream defaultConfig = getClass().getResourceAsStream("/default_config.yml");
				if (defaultConfig == null) {
					getLogger().log(Level.SEVERE, "Failed to locate default configuration file; was the plugin jar replaced?");
				} else {
					Files.copy(defaultConfig, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException ex) {
				getLogger().log(Level.SEVERE, "Failed to create configuration file");
			}
		}

		/* Load the config file & parse it */
		YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		String logLevel = config.getString("log-level", "INFO");
		try {
			getLogger().setLevel(Level.parse(logLevel));
			printConfig("log-level", logLevel);
		} catch (Exception ex) {
			getLogger().warning("log-level=" + logLevel + " is invalid - defaulting to INFO");
		}

		ConfigurationSection instancingConfig = config.getConfigurationSection("instancing");
		mShardInfoMap.clear();
		if (instancingConfig == null) {
			printConfig("instancing", null);
		} else {
			printConfigHeader("instancing");
			for (String shardName : instancingConfig.getKeys(false)) {
				ConfigurationSection shardConfig = instancingConfig.getConfigurationSection(shardName);
				if (shardConfig == null) {
					printConfig("  " + shardName, null);
				} else {
					printConfigHeader("  " + shardName);
					ShardInfo shardInfo = new ShardInfo(this, shardConfig);
					mShardInfoMap.put(shardName, shardInfo);
				}
			}
		}

		mSortWorldByScoreOnJoin = config.getBoolean("sort-world-by-score-on-join", mSortWorldByScoreOnJoin);
		printConfig("sort-world-by-score-on-join", mSortWorldByScoreOnJoin);

		mSortWorldByScoreOnRespawn = config.getBoolean("sort-world-by-score-on-respawn", mSortWorldByScoreOnRespawn);
		printConfig("sort-world-by-score-on-respawn", mSortWorldByScoreOnRespawn);

		mAllowInstanceAutocreation = config.getBoolean("allow-instance-autocreation", mAllowInstanceAutocreation);
		printConfig("allow-instance-autocreation", mAllowInstanceAutocreation);

		mUnloadInactiveWorldAfterTicks = config.getInt("unload-inactive-world-after-ticks", mUnloadInactiveWorldAfterTicks);
		printConfig("unload-inactive-world-after-ticks", mUnloadInactiveWorldAfterTicks);

		mNotifyWorldPermission = config.getString("notify-world-permission", mNotifyWorldPermission);
		if (mNotifyWorldPermission != null && (mNotifyWorldPermission.equals("null") || mNotifyWorldPermission.isEmpty())) {
			mNotifyWorldPermission = null;
		}
		printConfig("notify-world-permission", mNotifyWorldPermission);

		mCopyWorldCommand = config.getString("copy-world-command", mCopyWorldCommand);
		printConfig("copy-world-command", mCopyWorldCommand);

		reload();
	}

	public void reload() {
		getListener().reloadConfig();
		getWorldGenerator().reloadConfig();
	}

	protected void printConfigHeader(String configKey) {
		getLogger().info(configKey + ":");
	}

	protected <T> void printConfig(String configKey, @Nullable T value) {
		getLogger().info(configKey + "=" + (value == null ? "null" : value));
	}

	public static boolean isSortWorldByScoreOnJoin() {
		return mSortWorldByScoreOnJoin;
	}

	public static boolean isSortWorldByScoreOnRespawn() {
		return mSortWorldByScoreOnRespawn;
	}

	public static boolean allowInstanceAutocreation() {
		return mAllowInstanceAutocreation;
	}

	public static @Nullable ShardInfo getShardInfo(Player player) {
		// TODO: For now, just use the first shard name.
		// Eventually need some sorcery to let a player select a different entry
		ShardInfo info = null;
		for (ShardInfo shardInfo : mShardInfoMap.values()) {
			info = shardInfo;
			break;
		}
		if (info == null) {
			MMLog.fine("No shard info found.");
			return null;
		}
		return info;
	}

	public static @Nullable ShardInfo getShardInfo(String shardName) {
		return mShardInfoMap.get(shardName);
	}

	public static Map<String, Integer> getPregeneratedInstanceLimits() {
		// TODO Expose an unmodifiable map so the world generator can handle this part
		Map<String, Integer> templatePregenLimits = new HashMap<>();
		for (ShardInfo shardInfo : mShardInfoMap.values()) {
			int shardPregenLimit = shardInfo.getPregeneratedInstances();
			if (shardPregenLimit > 0) {
				for (String template : shardInfo.getVariantTemplates()) {
					Integer oldLimit = templatePregenLimits.get(template);
					if (oldLimit == null || oldLimit < shardPregenLimit) {
						templatePregenLimits.put(template, shardPregenLimit);
					}
				}
			}
		}
		return templatePregenLimits;
	}

	public static int getUnloadInactiveWorldAfterTicks() {
		return mUnloadInactiveWorldAfterTicks;
	}

	public static @Nullable String getNotifyWorldPermission() {
		return mNotifyWorldPermission;
	}

	public static String getCopyWorldCommand() {
		return mCopyWorldCommand;
	}

	@Override
	public void onDisable() {
		INSTANCE = null;
	}

	@Override
	public @NotNull Logger getLogger() {
		if (mLogger == null) {
			mLogger = new CustomLogger(super.getLogger(), Level.INFO);
		}
		return mLogger;
	}

	protected void setLogLevel(Level level) {
		super.getLogger().info("Changing log level to: " + level.toString());
		getLogger().setLevel(level);
	}

	/* If this ever returned null everything would explode anyway, no reason to add error handling around this */
	protected static WorldManagementPlugin getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("WorldManagementPlugin accessed before loading");
		}
		return INSTANCE;
	}

	protected WorldManagementListener getListener() {
		if (mListener == null) {
			mListener = new WorldManagementListener(this);
		}
		return mListener;
	}

	protected WorldGenerator getWorldGenerator() {
		if (mGenerator == null) {
			mGenerator = WorldGenerator.getInstance();
		}
		return mGenerator;
	}
}
