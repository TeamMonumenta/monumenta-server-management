package com.playmonumenta.redissync;

import com.google.common.base.Preconditions;
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
import com.playmonumenta.redissync.utils.PluginScheduler;
import com.playmonumenta.redissync.utils.VersionAdapterHolder;
import java.io.File;
import java.util.Locale;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class MonumentaRedisSync extends JavaPlugin implements PluginScheduler {
	private static @Nullable MonumentaRedisSync INSTANCE = null;
	private final VersionAdapterHolder<VersionAdapter> mVersionAdapter = new VersionAdapterHolder<>(
		VersionAdapter.class,
		this
	);

	private @Nullable RedisAPI mRedisAPI = null;
	private @Nullable CustomLogger mLogger = null;
	private @Nullable BukkitConfig mConfig = null;

	@Override
	public void onLoad() {
		// pre-load adapter
		mVersionAdapter.get();
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

		mRedisAPI = new RedisAPI(getLogger(), mConfig = loadConfig(), this::runAsync);
		getServer().getPluginManager().registerEvents(new ScoreboardCleanupListener(this, this.getLogger(),
			mVersionAdapter.get()), this);
		getServer().getPluginManager().registerEvents(AccountTransferManager.getInstance(), this);

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
		Preconditions.checkState(INSTANCE != null, "MonumentaRedisSync is not enabled yet");
		return INSTANCE;
	}

	public static RedisAPI redisApi() {
		return getInstance().getRedisApi();
	}

	public static BukkitConfig config() {
		return getInstance().getBukkitConfig();
	}

	public RedisAPI getRedisApi() {
		Preconditions.checkState(mRedisAPI != null, "MonumentaRedisSync is not enabled yet");
		return mRedisAPI;
	}

	public BukkitConfig getBukkitConfig() {
		Preconditions.checkState(mConfig != null, "MonumentaRedisSync is not enabled yet");
		return mConfig;
	}

	public VersionAdapter getVersionAdapter() {
		return mVersionAdapter.get();
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

		return new BukkitConfig(redisHost, redisPort, serverDomain, shardName, historyAmount,
			ticksPerPlayerAutosave, savingDisabled, scoreboardCleanupEnabled);
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

	private void runAsync(Runnable runnable) {
		Bukkit.getScheduler().runTaskAsynchronously(this, runnable);
	}
}
