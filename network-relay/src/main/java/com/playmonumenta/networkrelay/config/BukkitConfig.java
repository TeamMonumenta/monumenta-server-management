package com.playmonumenta.networkrelay.config;

import com.playmonumenta.networkrelay.util.YamlConfig;
import java.io.File;
import java.util.Map;
import java.util.logging.Logger;
import org.jetbrains.annotations.Nullable;

public class BukkitConfig extends CommonConfig {
	public static final boolean DEFAULT_BROADCAST_COMMAND_SENDING_ENABLED = true;
	public static final boolean DEFAULT_BROADCAST_COMMAND_RECEIVING_ENABLED = true;

	public boolean mBroadcastCommandSendingEnabled = DEFAULT_BROADCAST_COMMAND_SENDING_ENABLED;
	public boolean mBroadcastCommandReceivingEnabled = DEFAULT_BROADCAST_COMMAND_RECEIVING_ENABLED;
	public @Nullable String mServerAddress = null;

	public BukkitConfig(Logger logger, File configFile, Class<?> networkRelayClass, String resourcePath) {
		Map<String, Object> config = YamlConfig.loadWithFallback(logger, configFile, networkRelayClass, resourcePath);
		loadCommon(logger, config);

		mBroadcastCommandSendingEnabled = getBoolean(config,
			"broadcast-command-sending-enabled",
			DEFAULT_BROADCAST_COMMAND_SENDING_ENABLED);
		logger.info("broadcast-command-sending-enabled=" + mBroadcastCommandSendingEnabled);

		mBroadcastCommandReceivingEnabled = getBoolean(config,
			"broadcast-command-receiving-enabled",
			DEFAULT_BROADCAST_COMMAND_RECEIVING_ENABLED);
		logger.info("broadcast-command-receiving-enabled=" + mBroadcastCommandReceivingEnabled);

		/* Server address defaults to environment variable NETWORK_RELAY_SERVER_ADDRESS if present */
		String serverAddress = getString(config, "server-address", "");
		if (serverAddress.isEmpty()) {
			serverAddress = System.getenv("NETWORK_RELAY_SERVER_ADDRESS");
			if (serverAddress != null && serverAddress.isEmpty()) {
				serverAddress = null;
			}
		}
		mServerAddress = serverAddress;
		if (mServerAddress == null) {
			logger.info("server-address=<unset>");
		} else {
			logger.info("server-address=" + mServerAddress);
		}
	}
}
