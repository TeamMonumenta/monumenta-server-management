package com.playmonumenta.networkchat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.lang.ref.Cleaner;
import java.time.Instant;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.gson.GsonComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

public class Message implements AutoCloseable {
	static class State implements Runnable {
		private final UUID mId;

		State(UUID id) {
			mId = id;
		}

		@Override
		public void run() {
			// DO NOT UNREGISTER if the current message state does not match this state.
			Message currentMessage = MessageManager.getMessage(mId);
			if (currentMessage != null) {
				if (this != currentMessage.mState) {
					return;
				}
				MessageManager.unregisterMessage(mId);
			}
		}
	}

	private final State mState;
	private final Cleaner.Cleanable mCleanable;

	private final UUID mId;
	private final Instant mInstant;
	private final @Nullable UUID mChannelId;
	private final @Nullable UUID mSenderId;
	private final String mSenderName;
	private final @Nullable NamespacedKey mSenderType;
	private final boolean mSenderIsPlayer;
	private final Component mSenderComponent;
	private final @Nullable JsonObject mExtraData;
	private final Component mMessage;
	private boolean mIsDeleted = false;

	private Message(UUID id,
					Instant instant,
					@Nullable UUID channelId,
					@Nullable UUID senderId,
					String senderName,
					@Nullable NamespacedKey senderType,
					boolean senderIsPlayer,
					Component senderComponent,
					@Nullable JsonObject extraData,
					Component message) {
		mId = id;
		mInstant = instant;
		mChannelId = channelId;
		mSenderId = senderId;
		mSenderName = senderName;
		mSenderType = senderType;
		mSenderIsPlayer = senderIsPlayer;
		mSenderComponent = senderComponent;
		mExtraData = extraData;
		mMessage = message;

		// Member variable used to garbage collect Message objects
		mState = new State(mId);
		mCleanable = MessageManager.cleaner().register(this, mState);
		MessageManager.registerMessage(this);
	}

	// Normally called through a channel
	protected static @Nullable Message createMessage(Channel channel,
													 CommandSender sender,
													 @Nullable JsonObject extraData,
													 Component message) throws WrapperCommandSyntaxException {
		Message result;
		CommandSender callee = CommandUtils.getCallee(sender);
		Instant instant = Instant.now();
		UUID channelId = channel.getUniqueId();
		@Nullable UUID senderId = null;
		@Nullable NamespacedKey senderType = null;
		if (callee instanceof Entity entity) {
			senderId = entity.getUniqueId();
			senderType = entity.getType().getKey();
		}
		boolean senderIsPlayer = callee instanceof Player;
		Component senderComponent = MessagingUtils.senderComponent(sender);
		result = new Message(UUID.randomUUID(),
			instant,
			channelId,
			senderId,
			sender.getName(),
			senderType,
			senderIsPlayer,
			senderComponent,
			extraData,
			message);

		ChatFilter.ChatFilterResult filterResult = new ChatFilter.ChatFilterResult(result);
		NetworkChatPlugin.globalFilter().run(sender, filterResult);
		message = filterResult.component();
		if (filterResult.foundException()) {
			sender.sendMessage(Component.text("An error occurred processing your message. "
				+ "Please report this bug along with the shard and time it occurred.", NamedTextColor.RED));
			sender.sendMessage(message);
			return null;
		}
		if (filterResult.foundBadWord()) {
			sender.sendMessage(Component.text("You cannot say that on this server:", NamedTextColor.RED));
			sender.sendMessage(message);
			return null;
		}

		String plainMessage = MessagingUtils.plainText(message);
		if (plainMessage.contains("@")) {
			if (plainMessage.contains("@everyone") && !CommandUtils.hasPermission(sender, "networkchat.ping.everyone")) {
				throw CommandUtils.fail(sender, "You do not have permission to ping everyone in this channel.");
			} else if (!CommandUtils.hasPermission(sender, "networkchat.ping.player") && MessagingUtils.containsPlayerMention(plainMessage)) {
				throw CommandUtils.fail(sender, "You do not have permission to ping a player in this channel.");
			}
		}

		Component updatedMessage = NetworkChatPlugin.getReplacementsManagerInstance().run(callee, message);

		result = new Message(UUID.randomUUID(),
			instant,
			channelId,
			senderId,
			sender.getName(),
			senderType,
			senderIsPlayer,
			senderComponent,
			extraData,
			updatedMessage);
		return result;
	}

	// Normally called through a channel
	public static @Nullable Message createMessage(Channel channel,
												  CommandSender sender,
												  @Nullable JsonObject extraData,
												  String message) throws WrapperCommandSyntaxException {
		Component messageComponent = MessagingUtils.getAllowedMiniMessage(sender).deserialize(message);
		return createMessage(channel, sender, extraData, messageComponent);
	}

	// Raw, non-channel messages (use sparingly)
	protected static Message createRawMessage(@Nullable UUID senderId,
											  @Nullable JsonObject extraData,
											  Component message) {
		UUID id = UUID.randomUUID();
		Instant instant = Instant.now();
		if (senderId != null) {
			if (senderId.getMostSignificantBits() == 0 && senderId.getLeastSignificantBits() == 0) {
				senderId = null;
			}
		}
		@Nullable NamespacedKey senderType = null;
		if (senderId != null) {
			@Nullable Entity sender = Bukkit.getEntity(senderId);
			if (sender != null) {
				senderType = sender.getType().getKey();
			}
		}
		boolean senderIsPlayer = (senderId != null);
		String senderName = "";
		Component senderComponent = Component.empty();
		return new Message(id,
			instant,
			null,
			senderId,
			senderName,
			senderType,
			senderIsPlayer,
			senderComponent,
			extraData,
			message);
	}

	// For when receiving remote messages
	protected static Message fromJson(JsonObject object) {
		@Nullable UUID id = null;
		@Nullable JsonElement idJson = object.get("id");
		if (idJson != null) {
			id = UUID.fromString(idJson.getAsString());
		}
		if (id == null) {
			throw new RuntimeException("Incoming message has no ID");
		}
		@Nullable Message existingMessage = MessageManager.getMessage(id);
		if (existingMessage != null) {
			return existingMessage;
		}

		Instant instant;
		try {
			instant = Instant.ofEpochMilli(object.get("instant").getAsLong());
		} catch (Exception ex) {
			instant = Instant.EPOCH;
		}
		@Nullable UUID channelId = null;
		@Nullable JsonElement channelIdJson = object.get("channelId");
		if (channelIdJson != null) {
			channelId = UUID.fromString(channelIdJson.getAsString());
		}
		@Nullable UUID senderId = null;
		@Nullable JsonElement senderIdJson = object.get("senderId");
		if (senderIdJson != null) {
			senderId = UUID.fromString(senderIdJson.getAsString());
		}
		@Nullable JsonElement senderNameJson = object.get("senderName");
		String senderName;
		if (senderNameJson == null) {
			senderName = "@";
		} else {
			senderName = senderNameJson.getAsString();
		}
		@Nullable NamespacedKey senderType = null;
		@Nullable JsonElement senderTypeJson = object.get("senderType");
		if (senderTypeJson != null) {
			senderType = NamespacedKey.fromString(senderTypeJson.getAsString());
		}
		@Nullable JsonElement senderIsPlayerJson = object.get("senderIsPlayer");
		boolean senderIsPlayer;
		if (senderIsPlayerJson == null) {
			senderIsPlayer = false;
		} else {
			senderIsPlayer = senderIsPlayerJson.getAsBoolean();
		}
		Component senderComponent = Component.text(senderName);
		@Nullable JsonElement senderComponentJson = object.get("senderComponent");
		if (senderComponentJson != null) {
			senderComponent = MessagingUtils.fromJson(senderComponentJson);
		}
		@Nullable JsonObject extraData = null;
		@Nullable JsonElement extraJson = object.get("extra");
		if (extraJson != null) {
			extraData = extraJson.getAsJsonObject();
		}
		@Nullable JsonElement messageJson = object.get("message");
		Component message;
		if (messageJson == null) {
			message = Component.text("[MESSAGE COULD NOT BE LOADED]", NamedTextColor.RED, TextDecoration.BOLD);
		} else {
			message = GsonComponentSerializer.gson().deserializeFromTree(messageJson);
		}

		Message remoteMessage = new Message(
			id,
			instant,
			channelId,
			senderId,
			senderName,
			senderType,
			senderIsPlayer,
			senderComponent,
			extraData,
			message
		);

		if (
			object.get("isDeleted") instanceof JsonPrimitive isDeletedPrimitive
				&& isDeletedPrimitive.isBoolean()
				&& isDeletedPrimitive.getAsBoolean()
		) {
			remoteMessage.mIsDeleted = true;
		}

		return remoteMessage;
	}

	protected JsonObject toJson() {
		JsonObject object = new JsonObject();

		object.addProperty("id", mId.toString());
		object.addProperty("instant", mInstant.toEpochMilli());
		object.addProperty("isDeleted", mIsDeleted);
		if (mChannelId != null) {
			object.addProperty("channelId", mChannelId.toString());
		}
		if (mSenderId != null) {
			object.addProperty("senderId", mSenderId.toString());
		}
		object.addProperty("senderName", mSenderName);
		if (mSenderType != null) {
			object.addProperty("senderType", mSenderType.toString());
		}
		object.addProperty("senderIsPlayer", mSenderIsPlayer);
		object.add("senderComponent", MessagingUtils.toJson(mSenderComponent));
		if (mExtraData != null) {
			object.add("extra", mExtraData);
		}
		object.add("message", GsonComponentSerializer.gson().serializeToTree(mMessage));

		return object;
	}

	@Override
	public void close() {
		mCleanable.clean();
	}

	public UUID getUniqueId() {
		return mId;
	}

	public String getGuiCommand() {
		if (mId == null) {
			return "/chat gui";
		}
		return "/chat gui message " + mId;
	}

	public Instant getInstant() {
		return mInstant;
	}

	public @Nullable UUID getChannelUniqueId() {
		return mChannelId;
	}

	public @Nullable Channel getChannel() {
		if (mChannelId == null) {
			return null;
		}
		return ChannelManager.getChannel(mChannelId);
	}

	public @Nullable JsonObject getExtraData() {
		return mExtraData;
	}

	public @Nullable UUID getSenderId() {
		return mSenderId;
	}

	public String getSenderName() {
		return mSenderName;
	}

	public Component getSenderComponent() {
		return mSenderComponent;
	}

	public @Nullable NamespacedKey getSenderType() {
		return mSenderType;
	}

	public boolean senderIsPlayer() {
		return mSenderIsPlayer;
	}

	public Component getMessage() {
		if (mIsDeleted) {
			return Component.text("")
				.append(Component.text("[DELETED] ", NamedTextColor.DARK_RED, TextDecoration.BOLD))
				.append(Component.text(MessagingUtils.plainText(mMessage), NamedTextColor.RED));
		}
		return mMessage;
	}

	public String getPlainMessage() {
		return MessagingUtils.plainText(getMessage());
	}

	public boolean isDeleted() {
		return mIsDeleted;
	}

	// Get the message as shown to a given recipient
	public Component shownMessage(CommandSender recipient) {
		@Nullable Channel channel;
		if (mChannelId == null) {
			channel = null;
		} else {
			channel = ChannelManager.getChannel(mChannelId);
		}
		if (channel == null) {
			// Non-channel messages
			if (mExtraData != null) {
				@Nullable JsonPrimitive shardJson = mExtraData.getAsJsonPrimitive("shard");
				if (shardJson != null) {
					String shard = shardJson.getAsString();
					if (!shard.equals("*") && !shard.equals(NetworkChatPlugin.getShardName())) {
						return Component.text("[NOT VISIBLE ON THIS SHARD]", NamedTextColor.RED, TextDecoration.BOLD);
					}
				}
			}
			return mMessage;
		}
		return channel.shownMessage(recipient, this);
	}

	// Must be called from PlayerState to allow pausing messages.
	protected void showMessage(CommandSender recipient) {
		@Nullable Channel channel;
		if (mChannelId == null) {
			channel = null;
		} else {
			channel = ChannelManager.getChannel(mChannelId);
		}
		if (channel == null) {
			// Non-channel messages
			if (mExtraData != null) {
				@Nullable JsonPrimitive shardJson = mExtraData.getAsJsonPrimitive("shard");
				if (shardJson != null) {
					String shard = shardJson.getAsString();
					if (!shard.equals("*") && !shard.equals(NetworkChatPlugin.getShardName())) {
						return;
					}
				}
			}
			recipient.sendMessage(mMessage);
			return;
		}
		channel.showMessage(recipient, this);
	}

	protected void markDeleted() {
		mIsDeleted = true;
	}
}
