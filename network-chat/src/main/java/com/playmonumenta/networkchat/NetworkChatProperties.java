package com.playmonumenta.networkchat;

import com.playmonumenta.networkchat.utils.MMLog;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

public class NetworkChatProperties {

	private static @Nullable NetworkChatProperties INSTANCE = null;

	private boolean mReplaceHelpCommand = false;
	private boolean mChatCommandCreateEnabled = true;
	private boolean mChatCommandModifyEnabled = true;
	private boolean mChatCommandDeleteEnabled = true;
	private boolean mChatRequiresPlayer = false;
	private boolean mSudoEnabled = false;

	private NetworkChatProperties() {
		INSTANCE = this;
	}

	public static NetworkChatProperties getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new NetworkChatProperties();
		}
		return INSTANCE;
	}

	public static boolean getSudoEnabled() {
		return getInstance().mSudoEnabled;
	}

	public static boolean getReplaceHelpCommand() {
		return getInstance().mReplaceHelpCommand;
	}

	public static boolean getChatCommandCreateEnabled() {
		return getInstance().mChatCommandCreateEnabled;
	}

	public static boolean getChatCommandModifyEnabled() {
		return getInstance().mChatCommandModifyEnabled;
	}

	public static boolean getChatCommandDeleteEnabled() {
		return getInstance().mChatCommandDeleteEnabled;
	}

	public static boolean getChatRequiresPlayer() {
		return getInstance().mChatRequiresPlayer;
	}

	public static void load(Plugin plugin, @Nullable CommandSender sender) {
		getInstance().loadInternal(plugin, sender);
	}

	private void loadInternal(Plugin plugin, @Nullable CommandSender sender) {
		File configFile = new File(plugin.getDataFolder(), "config.yml");
		FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		if (config.isBoolean("ReplaceHelpCommand")) {
			mReplaceHelpCommand = config.getBoolean("ReplaceHelpCommand", mReplaceHelpCommand);
		}

		if (config.isBoolean("ChatCommandCreate")) {
			mChatCommandCreateEnabled = config.getBoolean("ChatCommandCreate", mChatCommandCreateEnabled);
		}

		if (config.isBoolean("ChatCommandModify")) {
			mChatCommandModifyEnabled = config.getBoolean("ChatCommandModify", mChatCommandModifyEnabled);
		}

		if (config.isBoolean("ChatCommandDelete")) {
			mChatCommandDeleteEnabled = config.getBoolean("ChatCommandDelete", mChatCommandDeleteEnabled);
		}

		if (config.isBoolean("ChatRequiresPlayer")) {
			mChatRequiresPlayer = config.getBoolean("ChatRequiresPlayer", mChatRequiresPlayer);
		}

		if (config.isBoolean("SudoEnabled")) {
			mSudoEnabled = config.getBoolean("SudoEnabled", mChatCommandDeleteEnabled);
		}

		MMLog.info("Properties:");
		if (sender != null) {
			sender.sendMessage("Properties:");
		}
		for (String str : toDisplay()) {
			MMLog.info("  " + str);
			if (sender != null) {
				sender.sendMessage("  " + str);
			}
		}
	}

	private List<String> toDisplay() {
		List<String> out = new ArrayList<>();

		out.add("mReplaceHelpCommand = " + mReplaceHelpCommand);
		out.add("mChatCommandCreateEnabled = " + mChatCommandCreateEnabled);
		out.add("mChatCommandModifyEnabled = " + mChatCommandModifyEnabled);
		out.add("mChatCommandDeleteEnabled = " + mChatCommandDeleteEnabled);
		out.add("mChatRequiresPlayer = " + mChatRequiresPlayer);
		out.add("mSudoEnabled = " + mSudoEnabled);

		return out;
	}


	public static void save(Plugin plugin) {
		getInstance().saveConfig(plugin);
	}

	public void saveConfig(Plugin plugin) {
		File configFile = new File(plugin.getDataFolder(), "config.yml");

		if (!configFile.exists()) {
			try {
				if (configFile.createNewFile()) {
					MMLog.info("Created config file.");
				}
			} catch (IOException e) {
				MMLog.warning("Catch exception during create new file for config.yml. Reason: " + e.getMessage());
			}
		}

		FileConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		if (!config.contains("ReplaceHelpCommand")) {
			config.set("ReplaceHelpCommand", mReplaceHelpCommand);
		}

		if (!config.contains("ChatCommandCreate")) {
			config.set("ChatCommandCreate", mChatCommandCreateEnabled);
		}

		if (!config.contains("ChatCommandModify")) {
			config.set("ChatCommandModify", mChatCommandModifyEnabled);
		}

		if (!config.contains("ChatCommandDelete")) {
			config.set("ChatCommandDelete", mChatCommandDeleteEnabled);
		}

		if (!config.contains("ChatRequiresPlayer")) {
			config.set("ChatRequiresPlayer", mChatRequiresPlayer);
		}

		if (!config.contains("SudoEnabled")) {
			config.set("SudoEnabled", mSudoEnabled);
		}

		try {
			config.save(configFile);
		} catch (IOException e) {
			MMLog.warning("Catch exception while save config.yml. Reason: " + e.getMessage());
		}
	}
}
