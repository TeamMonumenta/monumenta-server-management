package com.playmonumenta.redissync;

import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.commands.ChangeLogLevel;
import com.playmonumenta.redissync.commands.PlayerHistory;
import com.playmonumenta.redissync.commands.PlayerLoadFromPlayer;
import com.playmonumenta.redissync.commands.PlayerRollback;
import com.playmonumenta.redissync.commands.PlayerTransferHistory;
import com.playmonumenta.redissync.commands.RboardCommand;
import com.playmonumenta.redissync.commands.RemoteDataCommand;
import com.playmonumenta.redissync.commands.Stash;
import com.playmonumenta.redissync.commands.TransferServer;
import com.playmonumenta.redissync.commands.UpgradeAllPlayers;
import com.playmonumenta.redissync.config.BukkitConfig;
import java.io.File;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class MonumentaRedisSync extends JavaPlugin implements MonumentaRedisSyncInterface {
	private static @Nullable MonumentaRedisSync INSTANCE = null;
	private @Nullable RedisAPI mRedisAPI = null;
	private @Nullable VersionAdapter mVersionAdapter = null;
	private @Nullable CustomLogger mLogger = null;

	private void loadVersionAdapter() {
		/* From https://github.com/mbax/AbstractionExamplePlugin */

		String packageName = this.getServer().getClass().getPackage().getName();
		String version = packageName.substring(packageName.lastIndexOf('.') + 1);

		try {
			final Class<?> clazz = Class.forName("com.playmonumenta.redissync.adapters.VersionAdapter_" + version);
			// Check if we have a valid adapter class at that location.
			if (VersionAdapter.class.isAssignableFrom(clazz)) {
				mVersionAdapter = (VersionAdapter) clazz.getConstructor(Logger.class).newInstance(this.getLogger());
			}
		} catch (final Exception e) {
			e.printStackTrace();
			getLogger().severe("Server version " + version + " is not supported!");
			return;
		}
		getLogger().info("Loading support for " + version);
	}

	@Override
	public void onLoad() {
		loadVersionAdapter();

		/*
		 * CommandAPI commands which register directly and are usable in functions
		 *
		 * These need to register immediately on load to prevent function loading errors
		 */
		TransferServer.register();
		Stash.register();
		PlayerHistory.register(this);
		PlayerRollback.register();
		PlayerLoadFromPlayer.register();
		PlayerTransferHistory.register(this);
		UpgradeAllPlayers.register(this);
		ChangeLogLevel.register(this);
		RboardCommand.register(this);
		RemoteDataCommand.register(this);
	}

	@Override
	public void onEnable() {
		/* Refuse to enable without a version adapter */
		if (mVersionAdapter == null) {
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		/* Needed to tell Netty where it moved to */
		System.setProperty("com.playmonumenta.redissync.internal.io.netty", "com.playmonumenta.redissync.internal");

		INSTANCE = this;

		if (getServer().getPluginManager().isPluginEnabled("MonumentaNetworkRelay")) {
			try {
				getServer().getPluginManager().registerEvents(new NetworkRelayIntegration(this.getLogger()), this);
			} catch (Exception ex) {
				getLogger().severe("Failed to enable MonumentaNetworkRelay integration: " + ex.getMessage());
			}
		}

		loadConfig();
		mRedisAPI = new RedisAPI(this, BukkitConfig.getRedisHost(), BukkitConfig.getRedisPort());
		getServer().getPluginManager().registerEvents(new DataEventListener(this.getLogger(), mVersionAdapter), this);
		getServer().getPluginManager().registerEvents(new ScoreboardCleanupListener(this, this.getLogger(), mVersionAdapter), this);
		getServer().getPluginManager().registerEvents(AccountTransferManager.getInstance(), this);
		if (BukkitConfig.getTicksPerPlayerAutosave() > 0) {
			getServer().getPluginManager().registerEvents(new AutoSaveListener(this, mVersionAdapter), this);
		}

		this.getServer().getMessenger().registerOutgoingPluginChannel(this, "BungeeCord");
	}

	@Override
	public void onDisable() {
		INSTANCE = null;
		AccountTransferManager.onDisable();
		if (mRedisAPI != null) {
			mRedisAPI.shutdown();
		}
		mRedisAPI = null;
		getServer().getScheduler().cancelTasks(this);
	}

	public static MonumentaRedisSync getInstance() {
		MonumentaRedisSync instance = INSTANCE;
		if (instance == null) {
			throw new RuntimeException("MonumentaRedisSync is not enabled yet");
		}
		return instance;
	}

	public VersionAdapter getVersionAdapter() {
		VersionAdapter versionAdapter = mVersionAdapter;
		if (versionAdapter == null) {
			throw new RuntimeException("MonumentaRedisSync is not enabled yet");
		}
		return versionAdapter;
	}

	private BukkitConfig loadConfig() {
		File configFile = new File(this.getDataFolder(), "config.yml");
		/* TODO: Default file if not exist */
		FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);
		String redisHost = config.getString("redis_host", "redis");
		int redisPort = config.getInt("redis_port", 6379);
		String serverDomain = config.getString("server_domain", "default_domain");

		/* Get default shard name from network relay if enabled */
		String shardName = NetworkRelayIntegration.getShardName();
		if (shardName == null) {
			shardName = "default_shard";
		}
		shardName = config.getString("shard_name", shardName);

		int historyAmount = config.getInt("history_amount", 20);
		int ticksPerPlayerAutosave = config.getInt("ticks_per_player_autosave", 6060);
		boolean savingDisabled = config.getBoolean("saving_disabled", false);
		boolean scoreboardCleanupEnabled = config.getBoolean("scoreboard_cleanup_enabled", true);

		String level = config.getString("log_level", "INFO").toLowerCase(Locale.ENGLISH);
		switch (level) {
			case "finest":
				setLogLevel(Level.FINEST);
				break;
			case "finer":
				setLogLevel(Level.FINER);
				break;
			case "fine":
				setLogLevel(Level.FINE);
				break;
			default:
				setLogLevel(Level.INFO);
		}

		 return new BukkitConfig(getLogger(), redisHost, redisPort, serverDomain, shardName, historyAmount, ticksPerPlayerAutosave, savingDisabled, scoreboardCleanupEnabled);
	}

	public void setLogLevel(Level level) {
		super.getLogger().info("Changing log level to: " + level.toString());
		getLogger().setLevel(level);
	}

	@Override
	public @NotNull Logger getLogger() {
		if (mLogger == null) {
			mLogger = new CustomLogger(super.getLogger(), Level.INFO);
		}
		return mLogger;
	}

	@Override
	public void runAsync(Runnable runnable) {
		Bukkit.getScheduler().runTaskAsynchronously(this, runnable);
	}
}
