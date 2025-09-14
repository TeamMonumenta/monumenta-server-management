package com.playmonumenta.networkchat.channel;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.channel.property.ChannelSettings;
import com.playmonumenta.networkchat.utils.CommandUtils;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.CommandSender;

// A channel that is from a future plugin version. This may not be saved.
public class ChannelFuture extends Channel {
	public static final String CHANNEL_CLASS_ID = "future";

	private final String mType;

	protected ChannelFuture(JsonObject channelJson) throws Exception {
		super(channelJson, false, false);
		mType = channelJson.getAsJsonPrimitive("type").getAsString();
	}

	// NOTE: This channel type should never be saved, as it will overwrite a real channel.
	// toJson() shouldn't even be called for this class.
	@Override
	public JsonObject toJson() {
		throw new RuntimeException("Cannot convert future channels to json");
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
	public @Nullable TextColor color() {
		return null;
	}

	@Override
	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		throw CommandUtils.fail(sender, "This channel's type (" + mType + ") is not supported in this plugin version.");
	}

	@Override
	public ChannelSettings channelSettings() {
		throw new RuntimeException("Channel settings not available for future channels.");
	}

	@Override
	public ChannelAccess channelAccess() {
		throw new RuntimeException("Channel access not available for future channels.");
	}

	@Override
	public ChannelAccess playerAccess(UUID playerId) {
		throw new RuntimeException("Player access not available for future channels.");
	}

	@Override
	public void resetPlayerAccess(UUID playerId) {
	}

	@Override
	public void sendMessage(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		throw CommandUtils.fail(sender, "This channel's type (" + mType + ") is not supported in this plugin version.");
	}

	// Messages will be replayed for anyone triggering the channel to load, nothing to do.
	@Override
	public void distributeMessage(Message message) {
	}

	// The channel is from a future version - we can't determine how to display this!
	@Override
	public Component shownMessage(CommandSender recipient, Message message) {
		return message.getMessage();
	}

	// The channel is from a future version - we can't determine who can see this!
	@Override
	public void showMessage(CommandSender recipient, Message message) {
	}
}
