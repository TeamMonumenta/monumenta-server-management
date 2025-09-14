package com.playmonumenta.networkrelay;

import com.playmonumenta.networkrelay.config.BungeeConfig;
import java.io.File;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

public class NetworkRelayBungee extends Plugin {
	private static @Nullable NetworkRelayBungee INSTANCE = null;
	private @Nullable RabbitMQManager mRabbitMQManager = null;
	private @Nullable CustomLogger mLogger = null;

	@Override
	public void onEnable() {
		INSTANCE = this;

		File configFile = new File(getDataFolder(), "config.yml");

		BungeeConfig config =
			new BungeeConfig(getLogger(), configFile, getClass(), "/default_config_bungee.yml");

		boolean runReceivedCommands = config.mRunReceivedCommands;
		/* Shard name defaults to environment variable NETWORK_RELAY_NAME if present */
		String shardName = config.mShardName;
		boolean autoRegisterServersToBungee = config.mAutoRegisterServersToBungee;
		boolean autoUnregisterInactiveServersFromBungee = config.mAutoUnregisterInactiveServersFromBungee;
		String rabbitURI = config.mRabbitUri;
		int heartbeatInterval = config.mHeartbeatInterval;
		int destinationTimeout = config.mDestinationTimeout;
		long defaultTTL = config.mDefaultTtl;

		getProxy().getPluginManager().registerListener(this, new NetworkMessageListenerBungee(getLogger(), runReceivedCommands, autoRegisterServersToBungee, autoUnregisterInactiveServersFromBungee));

		try {
			mRabbitMQManager = new RabbitMQManager(new RabbitMQManagerAbstractionBungee(this), getLogger(), shardName, rabbitURI, heartbeatInterval, destinationTimeout, defaultTTL);
		} catch (Exception e) {
			getLogger().severe("RabbitMQ manager failed to initialize. This plugin will not function");
			e.printStackTrace();
		}

		// After a short while confirm the server has finished starting so messages can start being processed
		getProxy().getScheduler().schedule(this, () -> {
			if (mRabbitMQManager != null) {
				mRabbitMQManager.setServerFinishedStarting();
			}
		}, 5, TimeUnit.SECONDS);

		//Loaded last to avoid issues where it not being able to load the shard would cause it to fail.
		ProxyServer.getInstance().getPluginManager().registerListener(this, RemotePlayerManagerBungee.getInstance());
		RemotePlayerAPI.init(RemotePlayerManagerBungee.getInstance());

		ProxyServer.getInstance().getPluginManager().registerCommand(this, new WhereIsCommandBungee());
	}

	@Override
	public void onDisable() {
		if (mRabbitMQManager != null) {
			mRabbitMQManager.stop();
		}
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

	public static NetworkRelayBungee getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("NetworkRelay has not been initialized yet.");
		}
		return INSTANCE;
	}
}
