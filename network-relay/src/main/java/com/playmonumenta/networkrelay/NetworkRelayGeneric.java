package com.playmonumenta.networkrelay;

import com.playmonumenta.networkrelay.config.GenericConfig;
import java.io.File;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

public class NetworkRelayGeneric {
	private @Nullable static NetworkRelayGeneric INSTANCE = null;
	private final CustomLogger mLogger;
	private final Consumer<Object> mCallEventMethod;
	private @Nullable RabbitMQManager mRabbitMQManager = null;

	/**
	 * Creates a NetworkRelay instance for use outside of Bukkit/Bungee servers
	 *
	 * @param parentLogger        Base logger for your Java application; will be wrapped with custom logging info
	 * @param configFile          Path to the NetworkRelay config YAML file for your Java application
	 * @param defaultOwnerClass   A class in the JAR containing the default NetworkRelay config for your plugin
	 * @param resourcePath        The path to the default NetworkRelay config within the JAR for defaultOwnerClass
	 * @param serverType          A string identifier, such as "minecraft", "proxy", or "logger"
	 * @param registerEventMethod Method provided by your Java application to register event consumers.
	 *                               Accepts a priority and an event consumer.
	 *                               Higher priority values run first.
	 *                               Multiple consumers of the same priority must be accepted.
	 *                               Consumers are responsible for checking the event type.
	 * @param callEventMethod     Method provided by your Java application to send event objects to
	 *                               registered event listeners
	 */
	public NetworkRelayGeneric(Logger parentLogger,
	                           File configFile,
	                           Class<?> defaultOwnerClass,
	                           String resourcePath,
							   String serverType,
	                           BiConsumer<Integer, Consumer<Object>> registerEventMethod,
	                           Consumer<Object> callEventMethod) {
		mLogger = new CustomLogger(parentLogger, Level.INFO);
		mCallEventMethod = callEventMethod;
		GenericConfig config = new GenericConfig(mLogger, configFile, defaultOwnerClass, resourcePath);

		new NetworkMessageListenerGeneric(registerEventMethod, serverType);

		try {
			mRabbitMQManager = new RabbitMQManager(
				new RabbitMQManagerAbstractionGeneric(this),
				getLogger(),
				config.mShardName,
				config.mRabbitUri,
				config.mHeartbeatInterval,
				config.mDestinationTimeout,
				config.mDefaultTtl);
		} catch (Exception e) {
			getLogger().severe("RabbitMQ manager failed to initialize. This plugin will not function");
			e.printStackTrace();
		}

		INSTANCE = this;
	}

	public static NetworkRelayGeneric getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("Attempted to get NetworkRelay instance before initialized");
		}
		return INSTANCE;
	}

	public void setServerFinishedStarting() {
		if (mRabbitMQManager != null) {
			mRabbitMQManager.setServerFinishedStarting();
		}
	}

	public void onDisable() {
		if (mRabbitMQManager != null) {
			mRabbitMQManager.stop();
		}
	}

	public CustomLogger getLogger() {
		return mLogger;
	}

	protected void callEvent(Object event) {
		mCallEventMethod.accept(event);
	}
}
