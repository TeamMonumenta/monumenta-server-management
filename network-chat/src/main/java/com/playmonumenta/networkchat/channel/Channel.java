package com.playmonumenta.networkchat.channel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.channel.property.ChannelSettings;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public abstract class Channel {
	protected final UUID mId;
	protected Instant mLastUpdate;
	protected String mName;
	protected String mDescription;
	protected @Nullable TextColor mMessageColor = null;
	protected ChannelSettings mDefaultSettings;
	protected ChannelAccess mDefaultAccess;
	protected final Map<UUID, ChannelAccess> mPlayerAccess;

	protected Channel(UUID channelId, Instant lastUpdate, String name) {
		mId = channelId;
		mLastUpdate = lastUpdate;
		mName = name;
		mDescription = "";
		mDefaultSettings = new ChannelSettings();
		mDefaultAccess = new ChannelAccess();
		mPlayerAccess = new HashMap<>();
	}

	protected Channel(JsonObject channelJson) throws Exception {
		this(channelJson, true);
	}

	protected Channel(JsonObject channelJson, boolean requireName) throws Exception {
		this(channelJson, requireName, true);
	}

	protected Channel(JsonObject channelJson, boolean requireName, boolean matchClassId) throws Exception {
		if (matchClassId) {
			String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();
			if (channelClassId == null || !channelClassId.equals(getClassId())) {
				throw new Exception("Cannot create " + getClassId() + " from channel ID " + channelClassId);
			}
		}
		String uuidString = channelJson.getAsJsonPrimitive("uuid").getAsString();
		mId = UUID.fromString(uuidString);

		JsonElement lastUpdateJson = channelJson.get("lastUpdate");
		if (lastUpdateJson != null) {
			mLastUpdate = Instant.ofEpochMilli(lastUpdateJson.getAsLong());
		} else {
			mLastUpdate = Instant.now();
		}
		JsonPrimitive namePrimitive = channelJson.getAsJsonPrimitive("name");
		if (namePrimitive != null) {
			mName = namePrimitive.getAsString();
		} else if (requireName) {
			throw new Exception("Name is required for channel type " + getClassId());
		} else {
			mName = "Missing_Name_" + mId;
		}
		mPlayerAccess = new HashMap<>();

		JsonPrimitive descriptionPrimitive = channelJson.getAsJsonPrimitive("description");
		if (descriptionPrimitive != null) {
			mDescription = descriptionPrimitive.getAsString();
		} else {
			mDescription = "";
		}

		JsonPrimitive messageColorJson = channelJson.getAsJsonPrimitive("messageColor");
		if (messageColorJson != null && messageColorJson.isString()) {
			String messageColorString = messageColorJson.getAsString();
			try {
				mMessageColor = MessagingUtils.colorFromString(messageColorString);
			} catch (Exception e) {
				MMLog.warning("Caught exception getting mMessageColor from json: " + e.getMessage());
			}
		}

		JsonObject defaultSettingsJson = channelJson.getAsJsonObject("defaultSettings");
		if (defaultSettingsJson == null) {
			mDefaultSettings = new ChannelSettings();
		} else {
			mDefaultSettings = ChannelSettings.fromJson(defaultSettingsJson);
		}

		@SuppressWarnings("ReassignedVariable") JsonObject defaultAccessJson = channelJson.getAsJsonObject("defaultAccess");
		if (defaultAccessJson == null) {
			defaultAccessJson = channelJson.getAsJsonObject("defaultPerms");
		}
		if (defaultAccessJson == null) {
			mDefaultAccess = new ChannelAccess();
		} else {
			mDefaultAccess = ChannelAccess.fromJson(defaultAccessJson);
		}

		JsonObject allPlayerAccessJson = channelJson.getAsJsonObject("playerAccess");
		if (allPlayerAccessJson == null) {
			allPlayerAccessJson = channelJson.getAsJsonObject("playerPerms");
		}
		if (allPlayerAccessJson != null) {
			for (Map.Entry<String, JsonElement> playerPermEntry : allPlayerAccessJson.entrySet()) {
				UUID playerId;
				JsonObject playerAccessJson;
				try {
					playerId = UUID.fromString(playerPermEntry.getKey());
					playerAccessJson = playerPermEntry.getValue().getAsJsonObject();
				} catch (Exception e) {
					MMLog.warning("Caught exception getting ChannelAccess from json: " + e.getMessage());
					continue;
				}
				ChannelAccess playerAccess = ChannelAccess.fromJson(playerAccessJson);
				mPlayerAccess.put(playerId, playerAccess);
			}
		}
	}

	public static Channel fromJson(JsonObject channelJson) throws Exception {
		String channelClassId = channelJson.getAsJsonPrimitive("type").getAsString();

		return switch (channelClassId) {
			case ChannelAnnouncement.CHANNEL_CLASS_ID -> new ChannelAnnouncement(channelJson);
			case ChannelGlobal.CHANNEL_CLASS_ID -> new ChannelGlobal(channelJson);
			case ChannelLocal.CHANNEL_CLASS_ID -> new ChannelLocal(channelJson);
			case ChannelParty.CHANNEL_CLASS_ID -> new ChannelParty(channelJson);
			case ChannelTeam.CHANNEL_CLASS_ID -> new ChannelTeam(channelJson);
			case ChannelWhisper.CHANNEL_CLASS_ID -> new ChannelWhisper(channelJson);
			case ChannelWorld.CHANNEL_CLASS_ID -> new ChannelWorld(channelJson);
			default -> new ChannelFuture(channelJson);
		};
	}

	// DEFINE ME - Load a channel from json, allowing messages in that channel to be received
	//protected static Channel fromJsonInternal(JsonObject channelJson) throws Exception;

	public JsonObject toJson() {
		JsonObject allPlayerAccessJson = new JsonObject();
		for (Map.Entry<UUID, ChannelAccess> playerPermEntry : mPlayerAccess.entrySet()) {
			UUID channelId = playerPermEntry.getKey();
			ChannelAccess channelAccess = playerPermEntry.getValue();
			if (!channelAccess.isDefault()) {
				allPlayerAccessJson.add(channelId.toString(), channelAccess.toJson());
			}
		}

		JsonObject result = new JsonObject();
		result.addProperty("type", getClassId());
		result.addProperty("uuid", mId.toString());
		result.addProperty("lastUpdate", mLastUpdate.toEpochMilli());
		result.addProperty("name", mName);
		result.addProperty("description", mDescription);
		if (mMessageColor != null) {
			result.addProperty("messageColor", MessagingUtils.colorToString(mMessageColor));
		}
		result.add("defaultSettings", mDefaultSettings.toJson());
		result.add("defaultAccess", mDefaultAccess.toJson());
		result.add("playerAccess", allPlayerAccessJson);
		return result;
	}

	// DEFINE ME - Register commands for new channels; continues off an existing argument list of literals.
	// Channel ID is at index = prefixArguments.size() - 1
	//public static void registerNewChannelCommands(String[] baseCommands, List<Argument<?>> prefixArguments);

	public abstract String getClassId();

	// Return this channel's UUID
	public UUID getUniqueId() {
		return mId;
	}

	public void markModified() {
		mLastUpdate = Instant.now();
	}

	// Used to make sure this is the latest version
	public Instant lastModified() {
		return mLastUpdate;
	}

	// Set this channel's name (MUST ONLY be called from ChannelManager).
	// May call throw CommandAPI.fail() to cancel, ie for direct messages or insufficient permissions.
	public void setName(String name) throws WrapperCommandSyntaxException {
		mName = name;
	}

	public void setDescription(String description) throws WrapperCommandSyntaxException {
		mDescription = description;
	}

	// Return this channel's name
	public String getName() {
		return mName;
	}

	public String getDescription() {
		return mDescription;
	}

	public String getFriendlyName() {
		return getName();
	}

	public @Nullable TextColor color() {
		return mMessageColor;
	}

	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		mMessageColor = color;
	}

	public ChannelSettings channelSettings() {
		return mDefaultSettings;
	}

	public ChannelAccess channelAccess() {
		return mDefaultAccess;
	}

	public ChannelAccess playerAccess(UUID playerId) {
		return mPlayerAccess.computeIfAbsent(playerId, unused -> new ChannelAccess());
	}

	public void resetPlayerAccess(UUID playerId) {
		if (playerId == null) {
			return;
		}
		mPlayerAccess.remove(playerId);
	}

	public boolean shouldAutoJoin(PlayerState state) {
		Player player = state.getPlayer();
		return player != null && mayListen(player);
	}

	public boolean mayManage(CommandSender sender) {
		return CommandUtils.hasPermission(sender, "networkchat.moderator");
	}

	public boolean mayChat(CommandSender sender) {
		return false;
	}

	public boolean mayListen(CommandSender sender) {
		return false;
	}

	protected @Nullable WrapperCommandSyntaxException isListeningCheck(CommandSender sender) {
		if (sender instanceof Player player) {
			@Nullable PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				return CommandUtils.fail(player, MessagingUtils.noChatStateStr(player));
			} else if (!playerState.isListening(this)) {
				return CommandUtils.fail(player, "You are not listening in that channel.");
			}
		}
		return null;
	}

	// Check for access, then send a message to the network.
	// This broadcasts the message without displaying for network messages.
	public abstract void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException;

	// Distributes a received message to the appropriate local player chat states. May be local or remote messages.
	// Note that sending to player chat state allows chat to be paused.
	public abstract void distributeMessage(Message message);

	// Get how the message appears to a given recipient.
	public abstract Component shownMessage(CommandSender recipient, Message message);

	// Show a message to a player immediately; must be called from Message via PlayerState, not directly.
	public abstract void showMessage(CommandSender recipient, Message message);
}
