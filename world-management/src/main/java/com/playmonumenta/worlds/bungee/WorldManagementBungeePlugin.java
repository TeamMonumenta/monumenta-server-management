package com.playmonumenta.worlds.bungee;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.annotation.Nullable;

import com.playmonumenta.worlds.common.CustomLogger;

import net.md_5.bungee.api.plugin.Plugin;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

public class WorldManagementBungeePlugin extends Plugin {
	private @Nullable CustomLogger mLogger = null;

	@Override
	public void onEnable() {
		File configFile = new File(getDataFolder(), "config.yml");

		/* Create the config file & directories if it does not exist */
		if (!configFile.exists()) {
			try {
				// Create parent directories if they do not exist
				configFile.getParentFile().mkdirs();

				// Copy the default config file
				Files.copy(getClass().getResourceAsStream("/default_config_bungee.yml"), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
			} catch (IOException ex) {
				getLogger().log(Level.SEVERE, "Failed to create configuration file");
			}
		}

		/* Load the config file & parse it */
		Configuration config;
		try {
			config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
		} catch (IOException ex) {
			getLogger().warning("Failed to load config file, using defaults: " + ex.getMessage());
			config = new Configuration();
		}

		String logLevel = config.getString("log-level", "INFO");

		/* Echo config */
		try {
			getLogger().setLevel(Level.parse(logLevel));
			getLogger().info("log-level=" + logLevel);
		} catch (Exception ex) {
			getLogger().warning("log-level=" + logLevel + " is invalid - defaulting to INFO");
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
}
