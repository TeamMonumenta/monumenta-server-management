package com.playmonumenta.worlds.paper;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import com.playmonumenta.worlds.common.MMLog;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.apache.logging.log4j.Level;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;

public class WorldManagementPlugin extends JavaPlugin {
	private static @Nullable WorldManagementPlugin INSTANCE = null;

	private static boolean mSortWorldByScoreOnJoin = false;
	private static boolean mSortWorldByScoreOnRespawn = false;
	private static boolean mAllowInstanceAutocreation = false;
	private static int mUnloadInactiveWorldAfterTicks = 10 * 60 * 20;
	private static @Nullable String mNotifyWorldPermission = "monumenta.worldmanagement.worldnotify";
	private static String mCopyWorldCommand = "cp -a";
	private static final Map<String, ContentInfo> mContentInfoMap = new HashMap<>();
	private static final ConcurrentMap<String, ConcurrentMap<String, ContentInfo>> mRemoteContentByContent = new ConcurrentHashMap<>();
	private static final ConcurrentMap<String, ConcurrentMap<String, ContentInfo>> mRemoteContentByShard = new ConcurrentHashMap<>();

	private @Nullable WorldManagementListener mListener = null;
	private @Nullable WorldGenerator mGenerator = null;

	@Override
	public void onLoad() {
		MMLog.init(getName());
		com.playmonumenta.common.MMLogPaper.registerCommand(MMLog.getLog());
		WorldCommands.register(this);
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		getWorldGenerator();

		loadConfig();

		WorldManagementListener worldManagementListener = getListener();
		PluginManager pluginManager = Bukkit.getPluginManager();
		pluginManager.registerEvents(worldManagementListener, this);

		MonumentaWorldManagementAPI.refreshCachedAvailableWorlds();
		pluginManager.registerEvents(new NetworkRelayIntegration(), this);
	}

	protected void loadConfig() {
		File configFile = new File(getDataFolder(), "config.yml");

		/* Create the config file & directories if it does not exist */
		if (!configFile.exists()) {
			try {
				// Create parent directories if they do not exist
				configFile.getParentFile().mkdirs();

				// Copy the default config file
				InputStream defaultConfig = getClass().getResourceAsStream("/default_config.yml");
				if (defaultConfig == null) {
					MMLog.severe("Failed to locate default configuration file; was the plugin jar replaced?");
				} else {
					Files.copy(defaultConfig, configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
				}
			} catch (IOException ex) {
				MMLog.severe("Failed to create configuration file", ex);
			}
		}

		/* Load the config file & parse it */
		YamlConfiguration config = YamlConfiguration.loadConfiguration(configFile);

		String logLevel = config.getString("log-level", "INFO");
		try {
			MMLog.setLevel(Level.valueOf(logLevel));
			printConfig("log-level", logLevel);
		} catch (Exception ex) {
			MMLog.warning("log-level=" + logLevel + " is invalid - defaulting to INFO");
		}

		ConfigurationSection instancingConfig = config.getConfigurationSection("instancing");
		mContentInfoMap.clear();
		if (instancingConfig == null) {
			printConfig("instancing", null);
		} else {
			printConfigHeader("instancing");
			for (String contentName : instancingConfig.getKeys(false)) {
				ConfigurationSection contentConfig = instancingConfig.getConfigurationSection(contentName);
				if (contentConfig == null) {
					printConfig("  " + contentName, null);
				} else {
					printConfigHeader("  " + contentName);
					ContentInfo contentInfo = new ContentInfo(this, contentName, contentConfig);
					mContentInfoMap.put(contentName, contentInfo);
				}
			}
		}

		mSortWorldByScoreOnJoin = config.getBoolean("sort-world-by-score-on-join", mSortWorldByScoreOnJoin);
		printConfig("sort-world-by-score-on-join", mSortWorldByScoreOnJoin);

		mSortWorldByScoreOnRespawn = config.getBoolean("sort-world-by-score-on-respawn", mSortWorldByScoreOnRespawn);
		printConfig("sort-world-by-score-on-respawn", mSortWorldByScoreOnRespawn);

		mAllowInstanceAutocreation = config.getBoolean("allow-instance-autocreation", mAllowInstanceAutocreation);
		printConfig("allow-instance-autocreation", mAllowInstanceAutocreation);

		mUnloadInactiveWorldAfterTicks = config.getInt("unload-inactive-world-after-ticks", mUnloadInactiveWorldAfterTicks);
		printConfig("unload-inactive-world-after-ticks", mUnloadInactiveWorldAfterTicks);

		mNotifyWorldPermission = config.getString("notify-world-permission", mNotifyWorldPermission);
		if (mNotifyWorldPermission != null && (mNotifyWorldPermission.equals("null") || mNotifyWorldPermission.isEmpty())) {
			mNotifyWorldPermission = null;
		}
		printConfig("notify-world-permission", mNotifyWorldPermission);

		mCopyWorldCommand = config.getString("copy-world-command", mCopyWorldCommand);
		printConfig("copy-world-command", mCopyWorldCommand);

		reload();
		NetworkRelayIntegration.broadcastContentRequest();
	}

	public void reload() {
		getListener().reloadConfig();
		getWorldGenerator().reloadConfig();
		NetworkRelayIntegration.broadcastContentChange();
	}

	protected void printConfigHeader(String configKey) {
		MMLog.info(configKey + ":");
	}

	protected <T> void printConfig(String configKey, @Nullable T value) {
		MMLog.info(configKey + "=" + (value == null ? "null" : value));
	}

	public static boolean isSortWorldByScoreOnJoin() {
		return mSortWorldByScoreOnJoin;
	}

	public static boolean isSortWorldByScoreOnRespawn() {
		return mSortWorldByScoreOnRespawn;
	}

	public static boolean allowInstanceAutocreation() {
		return mAllowInstanceAutocreation;
	}

	public static Map<String, ContentInfo> getContentInfo() {
		return Collections.unmodifiableMap(mContentInfoMap);
	}

	public static @Nullable ContentInfo getContentInfo(Player player) {
		// TODO: For now, just use the first content name.
		// Eventually need some sorcery to let a player select a different entry
		ContentInfo info = null;
		for (ContentInfo contentInfo : mContentInfoMap.values()) {
			info = contentInfo;
			break;
		}
		if (info == null) {
			MMLog.debug("No content info found.");
			return null;
		}
		return info;
	}

	public static @Nullable ContentInfo getContentInfo(String contentName) {
		return mContentInfoMap.get(contentName);
	}

	protected static void registerRemoteContent(String shard, JsonObject content) {
		Gson gson = new Gson();
		for (Map.Entry<String, JsonElement> entry : content.entrySet()) {
			String contentName = entry.getKey();
			ContentInfo contentInfo;
			try {
				contentInfo = gson.fromJson(entry.getValue(), ContentInfo.class);
			} catch (JsonSyntaxException ignored) {
				continue;
			}

			mRemoteContentByShard
				.computeIfAbsent(shard, k -> new ConcurrentHashMap<>())
				.put(contentName, contentInfo);
			mRemoteContentByContent
				.computeIfAbsent(contentName, k -> new ConcurrentHashMap<>())
				.put(shard, contentInfo);
		}
	}

	protected static void unregisterRemoteShard(String shard) {
		Set<String> contentSet = mRemoteContentByShard.remove(shard).keySet();
		for (String content : contentSet) {
			ConcurrentMap<String, ContentInfo> remoteContentForContent = mRemoteContentByContent.get(content);
			if (remoteContentForContent != null) {
				remoteContentForContent.remove(shard);
				// Don't bother removing the empty map; this being async, something else
				// may register an entry, or the shard may come back up
			}
		}
	}

	protected void showShardsSupportingContent(Audience audience) {
		mRemoteContentByContent.forEach((String content, Map<String, ContentInfo> remoteShardContent) -> {
			audience.sendMessage(
				Component.empty()
					.append(Component.text(content + ": ", NamedTextColor.DARK_BLUE, TextDecoration.BOLD))
					.append(Component.text(String.join(", ", remoteShardContent.keySet()), NamedTextColor.BLUE))
			);
		});
	}

	public static Map<String, Integer> getPregeneratedInstanceLimits() {
		// TODO Expose an unmodifiable map so the world generator can handle this part
		Map<String, Integer> templatePregenLimits = new HashMap<>();
		for (ContentInfo contentInfo : mContentInfoMap.values()) {
			int contentPregenLimit = contentInfo.getPregeneratedInstances();
			if (contentPregenLimit > 0) {
				for (String template : contentInfo.getVariantTemplates()) {
					Integer oldLimit = templatePregenLimits.get(template);
					if (oldLimit == null || oldLimit < contentPregenLimit) {
						templatePregenLimits.put(template, contentPregenLimit);
					}
				}
			}
		}
		return templatePregenLimits;
	}

	public static int getUnloadInactiveWorldAfterTicks() {
		return mUnloadInactiveWorldAfterTicks;
	}

	public static @Nullable String getNotifyWorldPermission() {
		return mNotifyWorldPermission;
	}

	public static String getCopyWorldCommand() {
		return mCopyWorldCommand;
	}

	public static String[] getCopyWorldCommandWithArgs(String... args) {
		String[] cmdParts = mCopyWorldCommand.split("\\s+");
		String[] result = Arrays.copyOf(cmdParts, cmdParts.length + args.length);
		System.arraycopy(args, 0, result, cmdParts.length, args.length);
		return result;
	}

	@Override
	public void onDisable() {
		INSTANCE = null;
	}

	/** @deprecated Use {@link MMLog} static methods instead. */
	@Deprecated
	@Override
	public java.util.logging.Logger getLogger() {
		return super.getLogger();
	}

	/* If this ever returned null everything would explode anyway, no reason to add error handling around this */
	protected static WorldManagementPlugin getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("WorldManagementPlugin accessed before loading");
		}
		return INSTANCE;
	}

	protected WorldManagementListener getListener() {
		if (mListener == null) {
			mListener = new WorldManagementListener(this);
		}
		return mListener;
	}

	protected WorldGenerator getWorldGenerator() {
		if (mGenerator == null) {
			mGenerator = WorldGenerator.getInstance();
		}
		return mGenerator;
	}
}
