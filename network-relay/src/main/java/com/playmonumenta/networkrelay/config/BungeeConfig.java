package com.playmonumenta.networkrelay.config;

import com.playmonumenta.networkrelay.util.YamlConfig;
import java.io.File;
import java.util.Map;
import java.util.logging.Logger;

public class BungeeConfig extends CommonConfig {
	public static final boolean DEFAULT_RUN_RECEIVED_COMMANDS = true;
	public static final boolean DEFAULT_AUTO_REGISTER_SERVERS_TO_BUNGEE = false;
	public static final boolean DEFAULT_AUTO_UNREGISTER_INACTIVE_SERVERS_FROM_BUNGEE = false;

	public boolean mRunReceivedCommands;
	public boolean mAutoRegisterServersToBungee;
	public boolean mAutoUnregisterInactiveServersFromBungee;

	public BungeeConfig(Logger logger, File configFile, Class<?> networkRelayClass, String resourcePath) {
		Map<String, Object> config = YamlConfig.loadWithFallback(logger, configFile, networkRelayClass, resourcePath);
		loadCommon(logger, config);

		mRunReceivedCommands = getBoolean(config, "run-received-commands",
			DEFAULT_RUN_RECEIVED_COMMANDS);
		logger.info("run-received-commands=" + mRunReceivedCommands);

		mAutoRegisterServersToBungee = getBoolean(config, "auto-register-servers-to-bungee",
			DEFAULT_AUTO_REGISTER_SERVERS_TO_BUNGEE);
		logger.info("auto-register-servers-to-bungee=" + mAutoRegisterServersToBungee);

		mAutoUnregisterInactiveServersFromBungee =
			getBoolean(config, "auto-unregister-inactive-servers-from-bungee",
				DEFAULT_AUTO_UNREGISTER_INACTIVE_SERVERS_FROM_BUNGEE);
		if (mAutoUnregisterInactiveServersFromBungee && !mAutoRegisterServersToBungee) {
			logger.warning("Config mismatch - auto-unregister-inactive-servers-from-bungee auto-register-servers-to-bungee=false");
			logger.warning("Setting auto-unregister-inactive-servers-from-bungee");
			mAutoUnregisterInactiveServersFromBungee = false;
		}
		logger.info("auto-unregister-inactive-servers-from-bungee=" + mAutoUnregisterInactiveServersFromBungee);
	}
}
