package com.playmonumenta.networkrelay;

import com.google.inject.Inject;
import com.playmonumenta.networkrelay.commands.WhereIsCommandVelocity;
import com.playmonumenta.networkrelay.config.BungeeConfig;
import com.playmonumenta.networkrelay.config.CommonConfig;
import com.playmonumenta.networkrelay.util.MMLog;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.Dependency;
import com.velocitypowered.api.plugin.Plugin;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.ProxyServer;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.spongepowered.configurate.CommentedConfigurationNode;
import org.spongepowered.configurate.ConfigurateException;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Setting;
import org.spongepowered.configurate.yaml.NodeStyle;
import org.spongepowered.configurate.yaml.YamlConfigurationLoader;

@Plugin(
	id = "monumenta-network-relay",
	name = "MonumentaNetworkRelay",
	version = "",
	url = "",
	description = "",
	authors = {""},
	dependencies = {
		@Dependency(id = "monumenta-common"),
		@Dependency(id = "viaversion", optional = true)
	}
)
public class NetworkRelayVelocity {
	public static @MonotonicNonNull NetworkRelayVelocity INSTANCE;
	public final ProxyServer mServer;
	public final Logger mLogger;
	private @Nullable RabbitMQManager mRabbitMQManager = null;
	private final YamlConfigurationLoader mLoader; // Config reader & writer
	private @Nullable CommentedConfigurationNode mBaseConfig; // backing config node for the class
	public ConfigurateVelocityConfig mConfig = new ConfigurateVelocityConfig(); // class with actual data

	@Inject
	public NetworkRelayVelocity(ProxyServer server, Logger logger, @DataDirectory Path dataDirectory) {
		this.mServer = server;
		this.mLogger = logger;

		this.mLoader = YamlConfigurationLoader.builder()
			.path(dataDirectory.resolve(Path.of("config.yaml"))) // Set where we will load and save to
			.nodeStyle(NodeStyle.BLOCK)
			.build();

		loadConfig();
		saveConfig();

		// init RabbitMQ single thread mimic - usb
		NetworkRelayVelocityExecutor.getInstance();

		// "MonumentaNetworkRelay" matches @Plugin(name = "MonumentaNetworkRelay") above
		MMLog.init("MonumentaNetworkRelay");
		com.playmonumenta.common.MMLogVelocity.registerCommand(MMLog.getLog(), mServer.getCommandManager(), this);

		INSTANCE = this;
	}

	public static NetworkRelayVelocity getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("Attempted to get NetworkRelay instance before initialized");
		}
		return INSTANCE;
	}

	@Subscribe(priority = 0)
	public void onProxyInit(ProxyInitializeEvent event) {
		this.mServer.getEventManager().register(this, new NetworkMessageListenerVelocity(mConfig.mRunRecievedCommands, mConfig.mAutoRegisterServersToProxy, mConfig.mAutoUnregisterInactiveServersFromProxy));
		try {
			String shardName = System.getenv("NETWORK_RELAY_NAME") == null ? mConfig.mShardName : System.getenv("NETWORK_RELAY_NAME");
			mRabbitMQManager = new RabbitMQManager(new RabbitMQManagerAbstractionVelocity(this), shardName, mConfig.mRabbitUri, mConfig.mHeartbeatInterval, mConfig.mDestinationTimeout, mConfig.mDefaultTtl);
		} catch (Exception e) {
			MMLog.severe("RabbitMQ manager failed to initialize. This plugin will not function", e);
		}
		VelocityShardPingManager.schedulePingUpdates(this);

		//Loaded last to avoid issues where it not being able to load the shard would cause it to fail.
		this.mServer.getEventManager().register(this, new RemotePlayerManagerVelocity());
		RemotePlayerAPI.init(RemotePlayerManagerVelocity.getInstance());

		this.mServer.getScheduler().buildTask(this, () -> {
			if (mRabbitMQManager != null) {
				mRabbitMQManager.setServerFinishedStarting();
			}
		}).delay(5, TimeUnit.SECONDS).schedule();

		this.mServer.getCommandManager().register(this.mServer.getCommandManager().metaBuilder("whereisv").plugin(this).build(), new WhereIsCommandVelocity());
	}

	@Subscribe(priority = 0)
	public void onProxyShutdown(ProxyShutdownEvent event) {
		NetworkRelayVelocityExecutor.getInstance().stop();
		if (mRabbitMQManager != null) {
			mRabbitMQManager.stop();
		}
	}


	private void loadConfig() {
		try {
			// attempt to load from default
			mBaseConfig = mLoader.load(); // Load from file
			ConfigurateVelocityConfig temp = mBaseConfig.get(ConfigurateVelocityConfig.class);
			if (temp != null) {
				mConfig = temp;
			}
		} catch (ConfigurateException ex) {
			mLogger.warn("Could not load config.yaml", ex);
		}
	}

	private void saveConfig() {
		if (mBaseConfig == null || mConfig == null) {
			mLogger.warn("Tried to save current config but config is null!");
			return;
		}
		try {
			mBaseConfig.set(ConfigurateVelocityConfig.class, mConfig); // Update the backing node
			mLoader.save(mBaseConfig); // Write to the original file
		} catch (ConfigurateException ex) {
			mLogger.warn("Could not save config.yaml", ex);
		}
	}

	// TODO: eventually use configurate for Paper stuff as well since it's pretty nice - usb
	@ConfigSerializable
	public static class ConfigurateVelocityConfig {
		@Setting(value = "run-recieved-commands")
		public boolean mRunRecievedCommands = BungeeConfig.DEFAULT_RUN_RECEIVED_COMMANDS;

		@Setting(value = "auto-register-servers-to-proxy")
		public boolean mAutoRegisterServersToProxy = BungeeConfig.DEFAULT_AUTO_REGISTER_SERVERS_TO_BUNGEE;

		@Setting(value = "auto-unregister-inactive-servers-from-proxy")
		public boolean mAutoUnregisterInactiveServersFromProxy = BungeeConfig.DEFAULT_AUTO_UNREGISTER_INACTIVE_SERVERS_FROM_BUNGEE;

		@Setting(value = "log-level")
		public String mLogLevel = CommonConfig.DEFAULT_LOG_LEVEL.getName();

		@Setting(value = "shard-name")
		public String mShardName = CommonConfig.DEFAULT_SHARD_NAME;

		@Setting(value = "rabbitmq-uri")
		public String mRabbitUri = CommonConfig.DEFAULT_RABBIT_URI;

		@Setting(value = "heartbeat-interval")
		public int mHeartbeatInterval = CommonConfig.DEFAULT_HEARTBEAT_INTERVAL;

		@Setting(value = "destination-timeout")
		public int mDestinationTimeout = CommonConfig.DEFAULT_DESTINATION_TIMEOUT;

		@Setting(value = "default-time-to-live")
		public long mDefaultTtl = CommonConfig.DEFAULT_DEFAULT_TTL;
	}
}
