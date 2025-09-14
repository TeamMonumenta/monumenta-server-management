package com.playmonumenta.networkchat;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.channel.ChannelAnnouncement;
import com.playmonumenta.networkchat.channel.ChannelGlobal;
import com.playmonumenta.networkchat.channel.ChannelLocal;
import com.playmonumenta.networkchat.channel.ChannelParty;
import com.playmonumenta.networkchat.channel.ChannelTeam;
import com.playmonumenta.networkchat.channel.ChannelWhisper;
import com.playmonumenta.networkchat.channel.ChannelWorld;
import com.playmonumenta.networkchat.commands.ChangeLogLevel;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.inlinereplacements.ReplacementsManager;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.redissync.RedisAPI;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.ZipFile;
import javax.annotation.Nullable;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;

public class NetworkChatPlugin extends JavaPlugin implements Listener {
	// Config is partially handled in other classes, such as ChannelManager
	public static final String NETWORK_CHAT_CONFIG_UPDATE = "com.playmonumenta.networkchat.config_update";
	protected static final String REDIS_CONFIG_PATH = "networkchat:config";
	private static final String REDIS_MESSAGE_COLORS_KEY = "message_colors";
	private static final String REDIS_MESSAGE_FORMATS_KEY = "message_formats";
	private static final String REDIS_CHAT_FILTERS_KEY = "chat_filters";
	private static final String REDIS_CHAT_MOD_LOG_KEY = "chat_mod_log_command";

	private static @Nullable NetworkChatPlugin INSTANCE = null;
	private @Nullable CustomLogger mLogger = null;

	private static final Map<String, TextColor> mDefaultMessageColors = new ConcurrentSkipListMap<>();
	private static final Map<String, String> mDefaultMessageFormats = new ConcurrentSkipListMap<>();
	private static final String mDefaultChatModLogCommand
		= "auditlogchatmod <moderator> <details>";

	private static final Map<String, TextColor> mMessageColors = new ConcurrentSkipListMap<>();
	private static final Map<String, String> mMessageFormats = new ConcurrentSkipListMap<>();
	private static String mChatModLogCommand = mDefaultChatModLogCommand;

	private static ChatFilter mGlobalChatFilter = new ChatFilter();
	private static final ReplacementsManager mReplacementsManager = new ReplacementsManager();

	public static String getShardName() {
		@Nullable String shardName = null;
		try {
			shardName = NetworkRelayAPI.getShardName();
		} catch (Exception e) {
			MMLog.severe("Failed to get shard name");
		}
		if (shardName == null) {
			throw new RuntimeException("Got null shard name");
		}
		return shardName;
	}

	@Override
	public void onLoad() {
		if (mLogger == null) {
			mLogger = new CustomLogger(super.getLogger(), Level.INFO);
		}

		NetworkChatProperties.load(this, null);

		mDefaultMessageFormats.put("player", "<insert:<player_name>>"
			+ "<click:suggest_command:/tell <player_name> >"
			+ "<hover:show_entity:'minecraft:player':<player_uuid>:<player_name>>"
			+ "<team_color><team_prefix><player_name><team_suffix>"
			+ "</hover></click></insert>");

		mDefaultMessageFormats.put("entity", "<insert:<entity_uuid>>"
			+ "<hover:show_entity:'<entity_type>':<entity_uuid>:<entity_name>>"
			+ "<team_color><team_prefix><entity_name><team_suffix>"
			+ "</hover></insert>");

		mDefaultMessageFormats.put("sender", "<sender_name>");

		mDefaultMessageColors.put(ChannelAnnouncement.CHANNEL_CLASS_ID, NamedTextColor.RED);
		mDefaultMessageFormats.put(ChannelAnnouncement.CHANNEL_CLASS_ID, "<gray><click:run_command:\"<message_gui_cmd>\"><hover:show_text:\"<gray><<channel_color><channel_name><gray>> <channel_description>\\n<dark_gray>Type: Announcement Channel.\\nClick to open this channel's settings in a GUI.\">\\<<channel_color><channel_name><gray>></hover></click> <gray>»");
		mDefaultMessageColors.put(ChannelGlobal.CHANNEL_CLASS_ID, NamedTextColor.WHITE);
		mDefaultMessageFormats.put(ChannelGlobal.CHANNEL_CLASS_ID, "<gray><click:run_command:\"<message_gui_cmd>\"><hover:show_text:\"<gray><<channel_color><channel_name><gray>> <channel_description>\\n<dark_gray>Type: Global Channel.\\nClick to open this channel's settings in a GUI.\">\\<<channel_color><channel_name><gray>></hover></click> <white><sender> <gray>»");
		mDefaultMessageColors.put(ChannelLocal.CHANNEL_CLASS_ID, NamedTextColor.YELLOW);
		mDefaultMessageFormats.put(ChannelLocal.CHANNEL_CLASS_ID, "<gray><click:run_command:\"<message_gui_cmd>\"><hover:show_text:\"<gray><<channel_color><channel_name><gray>> <channel_description>\\n<dark_gray>Type: Local Channel.\\nClick to open this channel's settings in a GUI.\">\\<<channel_color><channel_name><gray>></hover></click> <white><sender> <gray>»");
		mDefaultMessageColors.put(ChannelWorld.CHANNEL_CLASS_ID, NamedTextColor.BLUE);
		mDefaultMessageFormats.put(ChannelWorld.CHANNEL_CLASS_ID, "<gray><click:run_command:\"<message_gui_cmd>\"><hover:show_text:\"<gray><<channel_color><channel_name><gray>> <channel_description>\\n<dark_gray>Type: World Channel.\\nClick to open this channel's settings in a GUI.\">\\<<channel_color><channel_name><gray>></hover></click> <white><sender> <gray>»");
		mDefaultMessageColors.put(ChannelParty.CHANNEL_CLASS_ID, NamedTextColor.LIGHT_PURPLE);
		mDefaultMessageFormats.put(ChannelParty.CHANNEL_CLASS_ID, "<gray><click:run_command:\"<message_gui_cmd>\"><hover:show_text:\"<gray><<channel_color><channel_name><gray>> <channel_description>\\n<dark_gray>Type: Party Channel.\\nClick to open this channel's settings in a GUI.\">\\<<channel_color><channel_name><gray>></hover></click> <white><sender> <gray>»");
		mDefaultMessageColors.put(ChannelTeam.CHANNEL_CLASS_ID, NamedTextColor.WHITE);
		mDefaultMessageFormats.put(ChannelTeam.CHANNEL_CLASS_ID, "<channel_color><team_displayname> \\<<sender>>");
		mDefaultMessageColors.put(ChannelWhisper.CHANNEL_CLASS_ID, NamedTextColor.GRAY);
		mDefaultMessageFormats.put(ChannelWhisper.CHANNEL_CLASS_ID, "<channel_color><sender> whispers to <receiver> <gray>»");

		mMessageColors.putAll(mDefaultMessageColors);
		mMessageFormats.putAll(mDefaultMessageFormats);

		ChangeLogLevel.register();
		@Nullable ZipFile zip = null;
		try {
			zip = new ZipFile(getFile());
		} catch (IOException ex) {
			MMLog.severe("Could not load help data from plugin.");
		}
		ChatCommand.register(this, zip);
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		reload(Bukkit.getConsoleSender());

		/* Check for Placeholder API */
		if (!Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")) {
			MMLog.severe("Could not find PlaceholderAPI! This plugin is required.");
			Bukkit.getPluginManager().disablePlugin(this);
			return;
		}

		getServer().getPluginManager().registerEvents(ChannelManager.getInstance(), this);
		getServer().getPluginManager().registerEvents(MessageManager.getInstance(), this);
		getServer().getPluginManager().registerEvents(RemotePlayerListener.getInstance(), this);
		getServer().getPluginManager().registerEvents(PlayerStateManager.getInstance(), this);
		getServer().getPluginManager().registerEvents(this, this);

		RedisAPI.getInstance().async().hget(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_MESSAGE_COLORS_KEY)
			.thenApply(dataStr -> {
			if (dataStr != null) {
				Gson gson = new Gson();
				JsonObject dataJson = gson.fromJson(dataStr, JsonObject.class);
				colorsFromJson(dataJson);
			}
			return dataStr;
		});

		RedisAPI.getInstance().async().hget(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_MESSAGE_FORMATS_KEY)
			.thenApply(dataStr -> {
			if (dataStr != null) {
				Gson gson = new Gson();
				JsonObject dataJson = gson.fromJson(dataStr, JsonObject.class);
				for (Map.Entry<String, JsonElement> entry : dataJson.entrySet()) {
					String id = entry.getKey();
					JsonElement element = entry.getValue();
					if (!element.isJsonPrimitive()
					  || !element.getAsJsonPrimitive().isString()) {
						continue;
					}
					String value = element.getAsString();
					mMessageFormats.put(id, value);
				}
			}
			return dataStr;
		});

		RedisAPI.getInstance().async().hget(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_CHAT_FILTERS_KEY)
			.thenApply(dataStr -> {
			if (dataStr != null) {
				Gson gson = new Gson();
				final JsonObject dataJson = gson.fromJson(dataStr, JsonObject.class);
				Bukkit.getServer().getScheduler().runTask(INSTANCE,
					() -> mGlobalChatFilter = ChatFilter.fromJson(Bukkit.getConsoleSender(), dataJson));
			}
			return dataStr;
		});

		RedisAPI.getInstance().async().hget(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_CHAT_MOD_LOG_KEY)
			.thenApply(dataStr -> {
				if (dataStr != null) {
					Bukkit.getServer().getScheduler().runTask(INSTANCE,
						() -> mChatModLogCommand = dataStr);
				}
				return dataStr;
			});
	}

	@Override
	public void onDisable() {
		NetworkChatProperties.save(this);
		INSTANCE = null;
		getServer().getScheduler().cancelTasks(this);
	}

	public static NetworkChatPlugin getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("NetworkChat has not been initialized yet.");
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

	public static void reload(CommandSender sender) {
		Bukkit.getScheduler().runTaskAsynchronously(NetworkChatPlugin.getInstance(),
			() -> mGlobalChatFilter = ChatFilter.globalFilter(sender));
	}

	public static int getMessageTtl() {
		return 5;
	}

	public static void colorsFromJson(JsonObject dataJson) {
		for (Map.Entry<String, JsonElement> entry : dataJson.entrySet()) {
			String id = entry.getKey();
			JsonElement element = entry.getValue();
			if (!element.isJsonPrimitive()
			  || !element.getAsJsonPrimitive().isString()) {
				continue;
			}
			String value = element.getAsString();
			@Nullable TextColor color = MessagingUtils.colorFromString(value);
			if (color != null) {
				mMessageColors.put(id, color);
			}
		}
	}

	public static void saveColors() {
		JsonObject dataJson = new JsonObject();
		for (Map.Entry<String, TextColor> entry : mMessageColors.entrySet()) {
			String id = entry.getKey();
			TextColor color = entry.getValue();
			dataJson.addProperty(id, MessagingUtils.colorToString(color));
		}

		RedisAPI.getInstance().async().hset(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_MESSAGE_COLORS_KEY, dataJson.toString());

		JsonObject wrappedConfigJson = new JsonObject();
		wrappedConfigJson.add(REDIS_MESSAGE_COLORS_KEY, dataJson);
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CONFIG_UPDATE,
			                                             wrappedConfigJson,
			                                             getMessageTtl());
		} catch (Exception e) {
			MMLog.severe("Failed to broadcast " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE);
		}
	}

	public static @Nullable TextColor messageColor(String id) {
		return mMessageColors.get(id);
	}

	public static void messageColor(String id, TextColor value) {
		mMessageColors.put(id, value);
		saveColors();
	}

	public static void formatsFromJson(JsonObject dataJson) {
		for (Map.Entry<String, JsonElement> entry : dataJson.entrySet()) {
			String id = entry.getKey();
			JsonElement element = entry.getValue();
			if (!element.isJsonPrimitive()
			  || !element.getAsJsonPrimitive().isString()) {
				continue;
			}
			String value = element.getAsString();
			mMessageFormats.put(id, value);
		}
	}

	public static void saveFormats() {
		JsonObject dataJson = new JsonObject();
		for (Map.Entry<String, String> entry : mMessageFormats.entrySet()) {
			String id = entry.getKey();
			String format = entry.getValue();
			dataJson.addProperty(id, format);
		}

		RedisAPI.getInstance().async().hset(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_MESSAGE_FORMATS_KEY, dataJson.toString());

		JsonObject wrappedConfigJson = new JsonObject();
		wrappedConfigJson.add(REDIS_MESSAGE_FORMATS_KEY, dataJson);
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CONFIG_UPDATE,
			                                             wrappedConfigJson,
			                                             getMessageTtl());
		} catch (Exception e) {
			MMLog.severe("Failed to broadcast " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE);
		}
	}

	public static String messageFormat(String id) {
		return mMessageFormats.getOrDefault(id, "Format string not found");
	}

	public static void messageFormat(String id, String value) {
		mMessageFormats.put(id, value);
		saveFormats();
		if (id.equals("player")) {
			RemotePlayerListener.refreshLocalPlayers();
		}
	}

	public static void logModChatAction(String moderator, String details) {
		String command = mChatModLogCommand;
		if (command.isBlank()) {
			return;
		}
		String finishedCommand = command
			.replace("<moderator>", moderator)
			.replace("<details>", details);
		Bukkit.getScheduler().runTask(NetworkChatPlugin.getInstance(),
			() -> Bukkit.getServer().dispatchCommand(Bukkit.getConsoleSender(), finishedCommand));
	}

	public static ChatFilter globalFilter() {
		return mGlobalChatFilter;
	}

	/**
	 * Gets the global bad word filter for use in external plugins
	 * @return the global bad word filter
	 */
	public static ChatFilter globalBadWordFilter() {
		return mGlobalChatFilter.badWordFiltersOnly();
	}

	public static void globalFilterFromJson(JsonObject dataJson) {
		mGlobalChatFilter = ChatFilter.fromJson(Bukkit.getConsoleSender(), dataJson);
	}

	public static void saveGlobalFilter() {
		JsonObject dataJson = mGlobalChatFilter.toJson();

		RedisAPI.getInstance().async().hset(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_CHAT_FILTERS_KEY, dataJson.toString());

		JsonObject wrappedConfigJson = new JsonObject();
		wrappedConfigJson.add(REDIS_CHAT_FILTERS_KEY, dataJson);
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CONFIG_UPDATE,
			                                             wrappedConfigJson,
			                                             getMessageTtl());
		} catch (Exception e) {
			MMLog.severe("Failed to broadcast " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		@Nullable JsonObject data;
		switch (event.getChannel()) {
			case NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE -> {
				data = event.getData();
				if (data == null) {
					MMLog.severe("Got " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE + " channel with null data");
					return;
				}
				@Nullable JsonObject messageColorsJson = data.getAsJsonObject(REDIS_MESSAGE_COLORS_KEY);
				if (messageColorsJson != null) {
					colorsFromJson(messageColorsJson);
				}
				@Nullable JsonObject messageFormatsJson = data.getAsJsonObject(REDIS_MESSAGE_FORMATS_KEY);
				if (messageFormatsJson != null) {
					formatsFromJson(messageFormatsJson);
				}
				@Nullable JsonObject chatFilterJson = data.getAsJsonObject(REDIS_CHAT_FILTERS_KEY);
				if (chatFilterJson != null) {
					globalFilterFromJson(chatFilterJson);
				}
			}
			default -> {
			}
		}
	}

	public static ReplacementsManager getReplacementsManagerInstance() {
		return mReplacementsManager;
	}
}
