package com.playmonumenta.networkrelay;

import com.playmonumenta.networkrelay.commands.BroadcastCommand;
import com.playmonumenta.networkrelay.commands.ChangeLogLevelCommand;
import com.playmonumenta.networkrelay.commands.DebugHeartbeatCommand;
import com.playmonumenta.networkrelay.commands.ListShardsCommand;
import com.playmonumenta.networkrelay.commands.RemotePlayerAPICommand;
import com.playmonumenta.networkrelay.commands.WhereIsCommand;
import com.playmonumenta.networkrelay.config.BukkitConfig;
import com.playmonumenta.networkrelay.shardhealth.ShardHealthManager;
import java.io.File;
import java.util.Objects;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public class NetworkRelay extends JavaPlugin {
	private static @Nullable NetworkRelay INSTANCE = null;
	private @Nullable RabbitMQManager mRabbitMQManager = null;
	private @Nullable BroadcastCommand mBroadcastCommand = null;
	private @Nullable CustomLogger mLogger = null;

	@Override
	public void onLoad() {
		mBroadcastCommand = new BroadcastCommand(this);
		ChangeLogLevelCommand.register(this);
		DebugHeartbeatCommand.registerCommands();
		ListShardsCommand.register();
		RemotePlayerAPICommand.register();
		WhereIsCommand.register();
		ShardHealthManager.init();
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		File configFile = new File(getDataFolder(), "config.yml");

		BukkitConfig config = new BukkitConfig(getLogger(), configFile, getClass(), "/default_config.yml");

		boolean broadcastCommandSendingEnabled = config.mBroadcastCommandSendingEnabled;
		boolean broadcastCommandReceivingEnabled = config.mBroadcastCommandReceivingEnabled;
		String shardName = config.mShardName;
		String serverAddress = config.mServerAddress;
		String rabbitURI = config.mRabbitUri;
		int heartbeatInterval = config.mHeartbeatInterval;
		int destinationTimeout = config.mDestinationTimeout;
		long defaultTTL = config.mDefaultTtl;

		Bukkit.getServer().getPluginManager().registerEvents(new NetworkMessageListener(serverAddress), this);

		/* Start relay components */
		BroadcastCommand.setEnabled(broadcastCommandSendingEnabled);
		if (broadcastCommandReceivingEnabled && mBroadcastCommand != null) {
			getServer().getPluginManager().registerEvents(Objects.requireNonNull(mBroadcastCommand), this);
		}

		try {
			mRabbitMQManager = new RabbitMQManager(new RabbitMQManagerAbstractionBukkit(this), getLogger(), shardName, rabbitURI, heartbeatInterval, destinationTimeout, defaultTTL);
		} catch (Exception e) {
			getLogger().severe("RabbitMQ manager failed to initialize. This plugin will not function");
			e.printStackTrace();
		}

		// Provide placeholder API replacements if it is present
		if (Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			new PlaceholderAPIIntegration(this).register();
		}

		// After a few ticks confirm the server has finished starting so messages can start being processed
		Bukkit.getScheduler().runTaskLater(this, () -> {
			if (mRabbitMQManager != null) {
				mRabbitMQManager.setServerFinishedStarting();
			}
		}, 5);

		//Loaded last to avoid issues where it not being able to load the shard would cause it to fail.
		Bukkit.getServer().getPluginManager().registerEvents(RemotePlayerManagerPaper.getInstance(), this);
		RemotePlayerAPI.init(RemotePlayerManagerPaper.getInstance());

		ShardHealthManager.startRunningAverageClock(this);
	}

	@Override
	public void onDisable() {
		RemotePlayerManagerPaper.getInstance().shutdown();
		if (mRabbitMQManager != null) {
			mRabbitMQManager.stop();
		}
		INSTANCE = null;
		ShardHealthManager.stopRunningAverageClock();
		getServer().getScheduler().cancelTasks(this);
	}

	public static NetworkRelay getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("NetworkRelay has not been initialized yet.");
		}
		return INSTANCE;
	}

	@Override
	public Logger getLogger() {
		if (mLogger == null) {
			mLogger = new CustomLogger(super.getLogger(), Level.INFO);
		}
		return mLogger;
	}

	public void setLogLevel(Level level) {
		super.getLogger().info("Changing log level to: " + level.toString());
		getLogger().setLevel(level);
	}
}
