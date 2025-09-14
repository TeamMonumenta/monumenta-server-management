package com.playmonumenta.networkchat.channel;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.channel.property.ChannelSettings;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

// A channel that has not finished loading. This may not be saved.
public class ChannelLoading extends Channel {
	public static final String CHANNEL_CLASS_ID = "loading";

	private final Set<PlayerState> mAwaitingPlayers = new HashSet<>();
	private final List<Message> mQueuedMessages = new ArrayList<>();

	/* Note: This channel type may only be created by this plugin while waiting for Redis data.
	 * This is why it is the only channel whose constructor is protected, and has no name. */
	public ChannelLoading(UUID channelId) {
		super(channelId, Instant.MIN, "Loading_" + channelId);
	}

	// NOTE: This channel type should never be saved, as it could overwrite a real channel.
	// toJson() shouldn't even be called for this class.
	@Override
	public JsonObject toJson() {
		throw new RuntimeException("Cannot convert loading channel to json!");
	}

	// NOTE: If this class ID is ever read, it means the channel hasn't loaded and should be ignored.
	@Override
	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	@Override
	public void markModified() {
	}

	@Override
	public Instant lastModified() {
		return Instant.MIN;
	}

	@Override
	public void setName(String name) throws WrapperCommandSyntaxException {
		throw CommandAPI.failWithString("This channel is still loading, please try again.");
	}

	@Override
	public void setDescription(String description) throws WrapperCommandSyntaxException {
		throw CommandAPI.failWithString("This channel is still loading, please try again.");
	}

	// NOTE: This should not be called for this class.
	@Override
	public String getName() {
		throw new RuntimeException("Cannot getName() for loading channels");
	}

	@Override
	public String getDescription() {
		throw new RuntimeException("Cannot getDescription() for loading channels");
	}

	@Override
	public @Nullable TextColor color() {
		throw new RuntimeException("Cannot get color of loading channels");
	}

	@Override
	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		throw CommandUtils.fail(sender, "This channel is still loading, please try again.");
	}

	@Override
	public ChannelSettings channelSettings() {
		throw new RuntimeException("Channel settings not available for loading channels.");
	}

	@Override
	public ChannelAccess channelAccess() {
		throw new RuntimeException("Channel access not available for loading channels.");
	}

	@Override
	public ChannelAccess playerAccess(UUID playerId) {
		throw new RuntimeException("Player access not available for loading channels.");
	}

	@Override
	public void resetPlayerAccess(UUID playerId) {
	}

	@Override
	public void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		throw CommandUtils.fail(sender, "This channel is still loading, please try again.");
	}

	// Messages will be replayed for anyone triggering the channel to load, nothing to do.
	@Override
	public void distributeMessage(Message message) {
	}

	// The channel is loading - we don't know how to display this message yet!
	@Override
	public Component shownMessage(CommandSender recipient, Message message) {
		return message.getMessage();
	}

	// The channel is loading - we can't determine who can see this yet!
	@Override
	public void showMessage(CommandSender recipient, Message message) {
	}

	// Register a player state waiting for this channel to finish loading.
	public void addWaitingPlayerState(PlayerState playerState) {
		mAwaitingPlayers.add(playerState);
	}

	// Register a message waiting for this channel to finish loading.
	public void addMessage(Message message) {
		mQueuedMessages.add(message);
	}

	// Notify the player the channel has finish loading, or failed to load.
	public void finishLoading() {
		Channel channel = ChannelManager.getChannel(mId);

		// Update awaiting player states regardless of load success (will show deletion on failure)
		for (PlayerState playerState : mAwaitingPlayers) {
			playerState.channelUpdated(mId, channel);
		}

		// If the newly loaded channel is valid, distribute messages that were missed
		if (channel != null && !(channel instanceof ChannelFuture)) {
			for (Message message : mQueuedMessages) {
				channel.distributeMessage(message);
			}
		}
	}
}
