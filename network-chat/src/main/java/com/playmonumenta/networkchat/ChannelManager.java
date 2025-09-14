package com.playmonumenta.networkchat;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.channel.ChannelFuture;
import com.playmonumenta.networkchat.channel.ChannelLoading;
import com.playmonumenta.networkchat.channel.ChannelParty;
import com.playmonumenta.networkchat.channel.ChannelWhisper;
import com.playmonumenta.networkchat.channel.interfaces.ChannelAutoJoin;
import com.playmonumenta.networkchat.channel.interfaces.ChannelInviteOnly;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import com.playmonumenta.redissync.RedisAPI;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.TextArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.output.ValueStreamingChannel;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class ChannelManager implements Listener {
	public static final String NETWORK_CHAT_CHANNEL_UPDATE = "com.playmonumenta.networkchat.channel.Channel.update";
	private static final String REDIS_CHANNEL_NAME_TO_UUID_PATH = "networkchat:channel_name_to_uuids";
	private static final String REDIS_CHANNELS_PATH = "networkchat:channels";
	private static final String REDIS_FORCELOADED_CHANNEL_PATH = "networkchat:forceloaded_channels";
	private static final String REDIS_DEFAULT_CHANNELS_KEY = "default_channels";

	private static @Nullable ChannelManager INSTANCE = null;
	private static DefaultChannels mDefaultChannels = new DefaultChannels();
	private static final Set<UUID> mForceLoadedChannels = new ConcurrentSkipListSet<>();
	private static final Map<String, UUID> mChannelIdsByName = new ConcurrentSkipListMap<>();
	private static final Map<UUID, String> mChannelNames = new ConcurrentSkipListMap<>();
	private static final Map<UUID, Channel> mChannels = new ConcurrentSkipListMap<>();

	private static class ForceloadStreamingChannel implements ValueStreamingChannel<String> {
		@Override
		public void onValue(String value /*Channel UUID*/) {
			try {
				UUID channelId = UUID.fromString(value);
				mForceLoadedChannels.add(channelId);
				loadChannel(channelId);
			} catch (Exception e) {
				MMLog.warning("Could not force-load channel ID " + value + ".");
			}
		}
	}

	private ChannelManager() {
		INSTANCE = this;
		reload();
		loadAllChannelNames();
	}

	public static ChannelManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new ChannelManager();
		}
		return INSTANCE;
	}

	public static void reload() {
		mForceLoadedChannels.clear();

		RedisAPI.getInstance().async().hget(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_DEFAULT_CHANNELS_KEY)
			.thenApply(dataStr -> {
			if (dataStr != null) {
				Gson gson = new Gson();
				JsonObject dataJson = gson.fromJson(dataStr, JsonObject.class);
				mDefaultChannels = DefaultChannels.fromJson(dataJson);
			}
			return dataStr;
		});

		ValueStreamingChannel<String> forceloadStreamingChannel = new ForceloadStreamingChannel();
		RedisAPI.getInstance().async().smembers(forceloadStreamingChannel, REDIS_FORCELOADED_CHANNEL_PATH);
	}

	public static void saveDefaultChannels() {
		JsonObject defaultChannelsJson = mDefaultChannels.toJson();

		RedisAPI.getInstance().async().hset(NetworkChatPlugin.REDIS_CONFIG_PATH, REDIS_DEFAULT_CHANNELS_KEY, defaultChannelsJson.toString());

		JsonObject wrappedConfigJson = new JsonObject();
		wrappedConfigJson.add(REDIS_DEFAULT_CHANNELS_KEY, defaultChannelsJson);
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE,
			                                             wrappedConfigJson,
			                                             NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			MMLog.severe("Failed to broadcast " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE);
		}
	}

	public static Set<String> getChannelNames() {
		return new ConcurrentSkipListSet<>(mChannelIdsByName.keySet());
	}

	public static Set<String> getChannelNames(String channelType) {
		Set<String> matches = new ConcurrentSkipListSet<>();
		for (Channel channel : getLoadedChannels()) {
			if (channelType.equals(channel.getClassId())) {
				matches.add(channel.getName());
			}
		}
		return matches;
	}

	public static Set<String> getChannelNames(Predicate<Channel> channelPredicate) {
		Set<String> matches = new ConcurrentSkipListSet<>();
		for (Channel channel : getLoadedChannels()) {
			if (channelPredicate.test(channel)) {
				matches.add(channel.getName());
			}
		}
		return matches;
	}

	public static ArgumentSuggestions<CommandSender> getChannelNameSuggestions(String channelType) {
		return getChannelNameSuggestions(ChannelPredicate.channelType(channelType));
	}

	public static ArgumentSuggestions<CommandSender> getChannelNameSuggestions(ChannelPredicate channelPredicate) {
		return ArgumentSuggestions.strings(info -> {
			Predicate<Channel> predicate = channelPredicate.toPredicate(info.sender());
			return CommandUtils.quoteIfNeeded(ChannelManager.getChannelNames(predicate)).toArray(new String[0]);
		});
	}

	public static Argument<String> getChannelNameArgument(ChannelPredicate channelPredicate) {
		return getChannelNameArgument("Channel Name", channelPredicate);
	}

	public static Argument<String> getChannelNameArgument(String argName, ChannelPredicate channelPredicate) {
		return new TextArgument(argName).replaceSuggestions(getChannelNameSuggestions(channelPredicate));
	}

	public static Argument<String> getChannelDescriptionArgument(String argDescription) {
		return new GreedyStringArgument(argDescription);
	}

	public static @Nullable String getChannelName(UUID channelId) {
		return mChannelNames.get(channelId);
	}

	public static @Nullable UUID getChannelId(String channelName) {
		return mChannelIdsByName.get(channelName);
	}

	public static List<Channel> getLoadedChannels() {
		return new ArrayList<>(mChannels.values());
	}

	public static Set<String> getAutoJoinableChannelNames(CommandSender sender) {
		Set<String> channels = new ConcurrentSkipListSet<>();
		for (Channel channel : mChannels.values()) {
			if (channel instanceof ChannelWhisper) {
				continue;
			}

			if ((channel instanceof ChannelAutoJoin) && channel.mayManage(sender)) {
				channels.add(channel.getName());
			}
		}
		return channels;
	}

	public static Set<String> getManageableChannelNames(CommandSender sender) {
		Set<String> channels = new ConcurrentSkipListSet<>();
		for (Channel channel : mChannels.values()) {
			if (channel instanceof ChannelWhisper) {
				continue;
			}

			if (channel.mayManage(sender)) {
				channels.add(channel.getName());
			}
		}
		return channels;
	}

	public static Set<String> getChatableChannelNames(CommandSender sender) {
		Set<String> channels = new ConcurrentSkipListSet<>();
		for (Channel channel : mChannels.values()) {
			if (channel instanceof ChannelWhisper) {
				continue;
			}

			if (channel.mayChat(sender)) {
				channels.add(channel.getName());
			}
		}
		return channels;
	}

	public static Set<String> getListenableChannelNames(CommandSender sender) {
		Set<String> channels = new ConcurrentSkipListSet<>();
		for (Channel channel : mChannels.values()) {
			if (channel instanceof ChannelWhisper) {
				continue;
			}

			if (channel.mayListen(sender)) {
				channels.add(channel.getName());
			}
		}
		return channels;
	}

	public static Set<String> getPartyChannelNames(CommandSender sender) {
		Set<String> channels = new ConcurrentSkipListSet<>();
		for (Channel channel : mChannels.values()) {
			if (!(channel instanceof ChannelParty)) {
				continue;
			}

			if (channel.mayManage(sender)) {
				channels.add(channel.getName());
			}
		}
		return channels;
	}

	// Used for new channels
	public static void registerNewChannel(CommandSender sender, Channel channel) throws WrapperCommandSyntaxException {
		if (channel == null) {
			return;
		}

		String channelName = channel.getName();
		@Nullable UUID oldChannelId = mChannelIdsByName.get(channelName);
		if (oldChannelId != null) {
			throw CommandUtils.fail(sender, "Channel " + channelName + " already exists!");
		}
		UUID channelId = channel.getUniqueId();
		RedisAPI.getInstance().async().hset(REDIS_CHANNEL_NAME_TO_UUID_PATH, channelName, channelId.toString());
		mChannelNames.put(channelId, channelName);
		mChannelIdsByName.put(channelName, channelId);
		mChannels.put(channelId, channel);
		saveChannel(channel);

		for (PlayerState state : PlayerStateManager.getPlayerStates().values()) {
			if (state.hasNotSeenChannelId(channelId)) {
				if (channel.shouldAutoJoin(state)) {
					state.joinChannel(channel);
				}
			}
		}

		if (!(channel instanceof ChannelWhisper)) {
			sender.sendMessage(Component.text("Created channel " + channelName + ".", NamedTextColor.GRAY));
		}
	}

	// Used for channels that are done loading from Redis
	public static void registerLoadedChannel(@Nullable Channel channel) {
		if (channel == null) {
			return;
		}

		UUID channelId = channel.getUniqueId();

		// Unregister any old channels as needed.
		@Nullable String oldChannelName = mChannelNames.get(channelId);
		if (oldChannelName != null && !oldChannelName.equals(channel.getName())) {
			forceRenameChannel(channel);
		} else {
			@Nullable Channel oldChannel = mChannels.get(channelId);
			if (oldChannel != null && !(oldChannel instanceof ChannelLoading)) {
				// Unregister the old channel so the new one can load.
				mChannels.remove(channelId);
			}
		}

		// Continue registering the loaded channel.
		mChannels.put(channelId, channel);

		if (!(channel instanceof ChannelInviteOnly)) {
			for (PlayerState state : PlayerStateManager.getPlayerStates().values()) {
				if (state.hasNotSeenChannelId(channelId)) {
					state.joinChannel(channel);
				}
			}
		} else if (!(channel instanceof ChannelWhisper)) {
			ChannelInviteOnly channelInvOnly = (ChannelInviteOnly) channel;
			for (UUID participantId : channelInvOnly.getParticipantIds()) {
				PlayerState state = PlayerStateManager.getPlayerState(participantId);
				if (state == null) {
					continue;
				}
				if (state.hasNotSeenChannelId(channelId)) {
					state.joinChannel(channel);
				}
			}
		}
	}

	public static DefaultChannels getDefaultChannels() {
		return mDefaultChannels;
	}

	public static @Nullable Channel getDefaultChannel() {
		return mDefaultChannels.getDefaultChannel("default");
	}

	public static @Nullable Channel getDefaultChannel(String channelId) {
		return mDefaultChannels.getDefaultChannel(channelId);
	}

	public static @Nullable Channel getChannel(UUID channelId) {
		if (channelId == null) {
			throw new RuntimeException("Null channel IDs are invalid.");
		}
		return mChannels.get(channelId);
	}

	public static @Nullable Channel getChannel(String channelName) {
		@Nullable UUID channelId = mChannelIdsByName.get(channelName);
		if (channelId == null) {
			return null;
		}
		return mChannels.get(channelId);
	}

	// For channel types with hard-coded names, ie whisper channels
	public static void forceRenameChannel(Channel channel) {
		UUID channelId = channel.getUniqueId();
		String newName = channel.getName();
		String oldName = mChannelNames.get(channelId);
		if (newName.equals(oldName)) {
			return;
		}

		@Nullable UUID existingChannelId = mChannelIdsByName.get(newName);
		if (existingChannelId != null) {
			if (!existingChannelId.equals(channelId)) {
				MMLog.info("Replacing channel " + newName);
				String channelIdStr = channelId.toString();

				// Update Redis
				RedisAsyncCommands<String, String> redisAsync = RedisAPI.getInstance().async();
				redisAsync.hdel(REDIS_CHANNEL_NAME_TO_UUID_PATH, newName);
				redisAsync.hdel(REDIS_CHANNELS_PATH, channelIdStr);
				redisAsync.srem(REDIS_FORCELOADED_CHANNEL_PATH, channelIdStr);

				// Broadcast to other shards
				JsonObject wrappedChannelJson = new JsonObject();
				wrappedChannelJson.addProperty("channelId", channelIdStr);
				wrappedChannelJson.addProperty("channelLastUpdate", Instant.now().toEpochMilli());
				// "channelData" intentionally left out (null indicates delete)
				try {
					NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CHANNEL_UPDATE,
						wrappedChannelJson,
						NetworkChatPlugin.getMessageTtl());
				} catch (Exception e) {
					MMLog.severe("Failed to broadcast " + NETWORK_CHAT_CHANNEL_UPDATE);
				}
			}
		}

		mChannelIdsByName.put(newName, channel.getUniqueId());
		mChannelIdsByName.remove(oldName);
		mChannelNames.put(channel.getUniqueId(), newName);

		saveChannel(channel);
		RedisAPI.getInstance().async().hdel(REDIS_CHANNEL_NAME_TO_UUID_PATH, oldName);
	}

	public static void renameChannel(String oldName, String newName) throws WrapperCommandSyntaxException {
		@Nullable UUID oldChannelId = mChannelIdsByName.get(oldName);
		if (oldChannelId == null) {
			throw CommandAPI.failWithString("Channel " + oldName + " does not exist!");
		}
		Channel channel = mChannels.get(oldChannelId);
		if (channel == null || channel instanceof ChannelLoading) {
			loadChannel(oldChannelId);
			throw CommandAPI.failWithString("Channel " + oldName + " not yet loaded, try again.");
		}

		if (mChannelIdsByName.containsKey(newName)) {
			throw CommandAPI.failWithString("Channel " + newName + " already exists!");
		}

		// NOTE: May call CommandAPI.fail to cancel the change before it occurs.
		channel.setName(newName);

		mChannelIdsByName.put(newName, channel.getUniqueId());
		mChannelIdsByName.remove(oldName);
		mChannelNames.put(channel.getUniqueId(), newName);

		saveChannel(channel);
		RedisAPI.getInstance().async().hdel(REDIS_CHANNEL_NAME_TO_UUID_PATH, oldName);
	}

	public static void changeChannelDescription(String newChannelDescription, String channelName) throws WrapperCommandSyntaxException {
		@Nullable UUID channelId = mChannelIdsByName.get(channelName);
		if (channelId == null) {
			throw CommandAPI.failWithString("Channel " + channelName + " does not exist!");
		}
		Channel channel = mChannels.get(channelId);
		if (channel == null || channel instanceof ChannelLoading) {
			loadChannel(channelId);
			throw CommandAPI.failWithString("Channel " + channelName + " not yet loaded, try again.");
		}

		channel.setDescription(newChannelDescription);
		saveChannel(channel);
	}

	public static void deleteChannel(String channelName) throws WrapperCommandSyntaxException {
		Channel channel = getChannel(channelName);
		if (channel == null) {
			throw CommandAPI.failWithString("Channel " + channelName + " does not exist!");
		}

		MMLog.info("Deleting channel " + channelName);

		UUID channelId = channel.getUniqueId();
		String channelIdStr = channelId.toString();

		// Update Redis
		RedisAsyncCommands<String, String> redisAsync = RedisAPI.getInstance().async();
		redisAsync.hdel(REDIS_CHANNEL_NAME_TO_UUID_PATH, channelName);
		redisAsync.hdel(REDIS_CHANNELS_PATH, channelIdStr);
		redisAsync.srem(REDIS_FORCELOADED_CHANNEL_PATH, channelIdStr);

		// Broadcast to other shards
		JsonObject wrappedChannelJson = new JsonObject();
		wrappedChannelJson.addProperty("channelId", channelIdStr);
		wrappedChannelJson.addProperty("channelLastUpdate", Instant.now().toEpochMilli());
		// "channelData" intentionally left out (null indicates delete)
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CHANNEL_UPDATE,
			                                             wrappedChannelJson,
			                                             NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			MMLog.severe("Failed to broadcast " + NETWORK_CHAT_CHANNEL_UPDATE);
		}
	}

	private static void deleteChannelLocally(UUID channelId) {
		String channelName = mChannelNames.get(channelId);
		if (channelName != null) {
			mChannelIdsByName.remove(channelName);
		}

		mDefaultChannels.unsetChannel(channelId);
		mForceLoadedChannels.remove(channelId);
		mChannels.remove(channelId);
		mChannelNames.remove(channelId);

		for (PlayerState playerState : PlayerStateManager.getPlayerStates().values()) {
			playerState.channelUpdated(channelId, null);
		}
	}

	private static void forceLoadChannel(Channel channel) {
		RedisAPI.getInstance().async().sadd(REDIS_FORCELOADED_CHANNEL_PATH, channel.getUniqueId().toString());
		mForceLoadedChannels.add(channel.getUniqueId());
	}

	public static Channel loadChannel(UUID channelId) {
		/* Note that mChannels containing the channel ID means
		 * either the channel is loaded, or is being loaded. */
		Channel preloadedChannel = mChannels.get(channelId);
		if (preloadedChannel != null) {
			return preloadedChannel;
		}

		// Mark the channel as loading
		MMLog.finer("Attempting to load channel " + channelId.toString() + ".");
		ChannelLoading loadingChannel = new ChannelLoading(channelId);
		mChannels.put(channelId, loadingChannel);

		// Get the channel from Redis async
		String channelIdStr = channelId.toString();
		RedisFuture<String> channelDataFuture = RedisAPI.getInstance().async().hget(REDIS_CHANNELS_PATH, channelIdStr);
		channelDataFuture.thenApply(channelData -> {
			loadChannelApply(channelId, channelData);
			return channelData;
		});
		return loadingChannel;
	}

	public static void loadChannel(UUID channelId, PlayerState playerState) {
		Channel channel = loadChannel(channelId);
		if (channel instanceof ChannelLoading) {
			((ChannelLoading) channel).addWaitingPlayerState(playerState);
		} else if (!(channel instanceof ChannelFuture)) {
			playerState.channelUpdated(channelId, channel);
		}
	}

	public static void loadChannel(UUID channelId, Message message) {
		Channel channel = loadChannel(channelId);
		if (channel instanceof ChannelLoading && message != null) {
			((ChannelLoading) channel).addMessage(message);
		} else if (!(channel instanceof ChannelFuture)) {
			channel.distributeMessage(message);
		}
	}

	private static void loadChannelApply(UUID channelId, String channelData) {
		Channel channel = mChannels.get(channelId);

		Gson gson = new Gson();
		ChannelLoading loadingChannel = null;
		if (channel instanceof ChannelLoading) {
			loadingChannel = (ChannelLoading) channel;
		}
		if (channelData == null) {
			// No channel was found, alert the player it was deleted.
			MMLog.warning("Channel " + channelId.toString() + " was not found.");
			mChannels.remove(channelId);
			PlayerStateManager.unregisterChannel(channelId);
		} else {
			// Channel was found. Attempt to register it.
			JsonObject channelJson = gson.fromJson(channelData, JsonObject.class);
			loadChannelApply(channelId, channelJson);
		}
		if (loadingChannel != null) {
			loadingChannel.finishLoading();
		}
	}

	private static void loadChannelApply(UUID channelId, JsonObject channelJson) {
		List<PlayerState> playersToNotify = new ArrayList<>();
		Channel oldChannel = mChannels.get(channelId);
		if (oldChannel != null) {
			for (PlayerState state : PlayerStateManager.getPlayerStates().values()) {
				if (state.isListening(oldChannel)) {
					playersToNotify.add(state);
				}
			}
		}

		Channel channel;
		try {
			channel = Channel.fromJson(channelJson);
			MMLog.finer("Channel " + channelId.toString() + " loaded, registering...");
			registerLoadedChannel(channel);
		} catch (Exception e) {
			MMLog.severe("Caught exception trying to load channel " + channelId.toString() + ":");
			StringWriter sw = new StringWriter();
			PrintWriter pw = new PrintWriter(sw);
			e.printStackTrace(pw);
			MMLog.severe(sw.toString());
			return;
		}

		for (PlayerState playerState : playersToNotify) {
			playerState.channelUpdated(channelId, channel);
		}

		if (oldChannel != null) {
			// Clean up old name if needed
			String oldName = oldChannel.getName();
			if (!channel.getName().equals(oldName)) {
				// Remove the old name
				mChannelIdsByName.remove(oldName);
			}
		}

		String newName = channel.getName();
		mChannelIdsByName.put(newName, channelId);
		mChannelNames.put(channelId, newName);
	}

	public static void saveChannel(Channel channel) {
		if (channel == null || channel instanceof ChannelLoading || channel instanceof ChannelFuture) {
			return;
		}

		channel.markModified();

		String channelIdStr = channel.getUniqueId().toString();
		String channelName = channel.getName();
		String channelDescription = channel.getDescription();
		JsonObject channelJson = channel.toJson();
		String channelJsonStr = channelJson.toString();

		MMLog.finer("Saving channel " + channelIdStr + ".");
		RedisAsyncCommands<String, String> redisAsync = RedisAPI.getInstance().async();
		redisAsync.hset(REDIS_CHANNEL_NAME_TO_UUID_PATH, channelName, channelIdStr);
		redisAsync.hset(REDIS_CHANNELS_PATH, channelIdStr, channelJsonStr);

		JsonObject wrappedChannelJson = new JsonObject();
		wrappedChannelJson.addProperty("channelId", channelIdStr);
		wrappedChannelJson.addProperty("description", channelDescription);
		wrappedChannelJson.addProperty("channelLastUpdate", channel.lastModified().toEpochMilli());
		wrappedChannelJson.add("channelData", channelJson);
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CHANNEL_UPDATE,
			                                             wrappedChannelJson,
			                                             NetworkChatPlugin.getMessageTtl());
			MMLog.finer("Broadcast channel " + channelIdStr + " changes.");
		} catch (Exception e) {
			MMLog.severe("Failed to broadcast " + NETWORK_CHAT_CHANNEL_UPDATE);
		}
		if (!(channel instanceof ChannelInviteOnly)) {
			forceLoadChannel(channel);
		}
	}

	public static void unloadChannel(Channel channel) {
		UUID channelId = channel.getUniqueId();
		if (mForceLoadedChannels.contains(channelId)) {
			// Abort unload attempt
			return;
		}

		for (PlayerState playerState : PlayerStateManager.getPlayerStates().values()) {
			if (playerState.isWatchingChannelId(channelId)) {
				// Abort unload attempt
				return;
			}
		}

		mChannels.remove(channelId);
		saveChannel(channel);

		// No announcements or deletion to do.
	}

	private static void loadAllChannelNames() {
		RedisFuture<Map<String, String>> channelDataFuture = RedisAPI.getInstance().async().hgetall(REDIS_CHANNEL_NAME_TO_UUID_PATH);
		channelDataFuture.thenApply(channelStrIdsByName -> {
			mChannelIdsByName.clear();
			mChannelNames.clear();
			if (channelStrIdsByName != null) {
				for (Map.Entry<String, String> entry : channelStrIdsByName.entrySet()) {
					String channelName = entry.getKey();
					String channelIdStr = entry.getValue();
					UUID channelId = UUID.fromString(channelIdStr);
					mChannelIdsByName.put(channelName, channelId);
					mChannelNames.put(channelId, channelName);
				}
			}
			return channelStrIdsByName;
		});
	}

	private static void networkRelayChannelUpdate(JsonObject data) {
		UUID channelId;
		Instant channelLastUpdate;
		JsonObject channelData;
		Set<UUID> participants = null;
		boolean shouldLoad = true;
		try {
			channelId = UUID.fromString(data.get("channelId").getAsString());
			channelLastUpdate = Instant.ofEpochMilli(data.get("channelLastUpdate").getAsLong());
			channelData = data.getAsJsonObject("channelData");

			JsonArray participantsJson = data.getAsJsonArray("participants");
			if (participantsJson != null) {
				participants = new ConcurrentSkipListSet<>();
				for (JsonElement participantJson : participantsJson) {
					participants.add(UUID.fromString(participantJson.getAsString()));
				}
			}
		} catch (Exception e) {
			MMLog.severe("Got " + NETWORK_CHAT_CHANNEL_UPDATE + " channel with invalid data");
			return;
		}
		String logIdName = "ID " + channelId;
		String oldName = mChannelNames.get(channelId);
		if (oldName != null) {
			logIdName = oldName;
		}
		if (channelData == null) {
			MMLog.info("Got deletion notice for channel " + logIdName);
		} else {
			MMLog.finer("Got update for channel " + logIdName);
		}
		Channel oldChannel = mChannels.get(channelId);
		if (oldChannel != null) {
			if (channelLastUpdate.compareTo(oldChannel.lastModified()) < 0) {
				// Received channel data is older than ours, ignore it.
				return;
			}
		}

		if (participants != null) {
			shouldLoad = PlayerStateManager.isAnyParticipantLocal(participants);
		}

		if (oldChannel == null && !shouldLoad) {
			// Channel wasn't loaded, and doesn't need to be loaded.

			String newName;
			if (channelData != null) {
				// Rename (possibly new) channel locally
				try {
					newName = channelData.getAsJsonPrimitive("name").getAsString();
				} catch (Exception e) {
					MMLog.severe("Got " + NETWORK_CHAT_CHANNEL_UPDATE + " channel with no name");
					return;
				}

				mChannelIdsByName.remove(oldName);
				mChannelIdsByName.put(newName, channelId);
				mChannelNames.put(channelId, newName);
				MMLog.info("Renamed channel " + oldName + " to " + newName);
			}

			return;
		}

		if (channelData == null) {
			deleteChannelLocally(channelId);
		} else {
			loadChannelApply(channelId, channelData);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		JsonObject data;
		switch (event.getChannel()) {
			case NETWORK_CHAT_CHANNEL_UPDATE -> {
				data = event.getData();
				if (data == null) {
					MMLog.severe("Got " + NETWORK_CHAT_CHANNEL_UPDATE + " channel with null data");
					return;
				}
				networkRelayChannelUpdate(data);
			}
			case NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE -> {
				data = event.getData();
				if (data == null) {
					MMLog.severe("Got " + NetworkChatPlugin.NETWORK_CHAT_CONFIG_UPDATE + " channel with null data");
					return;
				}
				JsonObject defaultChannelsJson = data.getAsJsonObject(REDIS_DEFAULT_CHANNELS_KEY);
				if (defaultChannelsJson != null) {
					mDefaultChannels = DefaultChannels.fromJson(defaultChannelsJson);
				}
			}
			default -> {
			}
		}
	}
}
