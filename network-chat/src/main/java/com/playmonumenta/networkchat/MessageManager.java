package com.playmonumenta.networkchat;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.channel.Channel;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.NetworkRelayMessageEvent;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.lang.ref.Cleaner;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class MessageManager implements Listener {
	public static final String NETWORK_CHAT_MESSAGE = "com.playmonumenta.networkchat.Message";
	public static final String NETWORK_CHAT_DELETE_MESSAGE = "com.playmonumenta.networkchat.Message.delete";
	public static final String NETWORK_CHAT_DELETE_FROM_SENDER = "com.playmonumenta.networkchat.Message.deleteFromSender";
	public static final String NETWORK_CHAT_CLEAR_CHAT = "com.playmonumenta.networkchat.Message.clearChat";

	private static @Nullable MessageManager INSTANCE = null;
	private static final Cleaner mCleaner = Cleaner.create();
	private static final Map<UUID, WeakReference<Message>> mMessages = new HashMap<>();

	private MessageManager() {
		INSTANCE = this;
	}

	public static MessageManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new MessageManager();
		}
		return INSTANCE;
	}

	public static @Nullable Message getMessage(UUID messageId) {
		WeakReference<Message> messageWeakReference = mMessages.get(messageId);
		if (messageWeakReference == null) {
			return null;
		}
		Message message = messageWeakReference.get();
		if (message == null) {
			mMessages.remove(messageId);
		}
		return message;
	}

	public void broadcastMessage(CommandSender sender, Message message) throws WrapperCommandSyntaxException {
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_MESSAGE,
				message.toJson(),
				NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			MMLog.warning("Could not send message; A RabbitMQ error has occurred.", e);
			MessagingUtils.sendStackTrace(Bukkit.getConsoleSender(), e);
			throw CommandUtils.fail(sender, "Could not send message; A RabbitMQ error has occurred.");
		}
	}

	public static void deleteMessage(CommandSender moderator, UUID messageId) {
		Message message = getMessage(messageId);
		if (message == null) {
			NetworkChatPlugin.logModChatAction(moderator.getName(), "deleted unknown message " + messageId);
		} else {
			NamespacedKey senderType = message.getSenderType();
			String senderTypeStr = (senderType == null) ? "console?" : senderType.toString();
			String sender = message.getSenderName();
			String shownMessage = MessagingUtils.plainText(message.shownMessage(moderator));

			NetworkChatPlugin.logModChatAction(
				moderator.getName(),
				"deleted message from " +
					senderTypeStr +
					" " +
					sender +
					": " +
					shownMessage
			);
		}

		JsonObject object = new JsonObject();
		object.addProperty("id", messageId.toString());
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_DELETE_MESSAGE,
				                                         object,
				                                         NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			MMLog.warning("Catch exception sending " + NETWORK_CHAT_DELETE_MESSAGE + " reason: " + e.getMessage());
		}
	}

	public static void deleteMessagesFromSender(UUID senderId) {
		JsonObject object = new JsonObject();
		object.addProperty("id", senderId.toString());
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_DELETE_FROM_SENDER,
				object,
				NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			MMLog.warning("Catch exception sending " + NETWORK_CHAT_DELETE_FROM_SENDER + " reason: " + e.getMessage());
		}
	}

	public static void clearChat() {
		try {
			NetworkRelayAPI.sendExpiringBroadcastMessage(NETWORK_CHAT_CLEAR_CHAT,
				new JsonObject(),
				NetworkChatPlugin.getMessageTtl());
		} catch (Exception e) {
			MMLog.warning("Catch exception sending " + NETWORK_CHAT_CLEAR_CHAT + " reason: " + e.getMessage());
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void networkRelayMessageEvent(NetworkRelayMessageEvent event) {
		JsonObject data;
		switch (event.getChannel()) {
			case NETWORK_CHAT_MESSAGE -> {
				data = event.getData();
				if (data == null) {
					MMLog.severe("Got " + NETWORK_CHAT_MESSAGE + " message with null data");
					return;
				}
				receiveMessageHandler(data);
			}
			case NETWORK_CHAT_DELETE_MESSAGE -> {
				data = event.getData();
				if (data == null) {
					MMLog.severe("Got " + NETWORK_CHAT_DELETE_MESSAGE + " message with null data");
					return;
				}
				deleteMessageHandler(data);
			}
			case NETWORK_CHAT_DELETE_FROM_SENDER -> {
				data = event.getData();
				if (data == null) {
					MMLog.severe("Got " + NETWORK_CHAT_DELETE_FROM_SENDER + " message with null data");
					return;
				}
				deleteFromSenderHandler(data);
			}
			case NETWORK_CHAT_CLEAR_CHAT -> clearChatHandler();
			default -> {
			}
		}
	}

	public void receiveMessageHandler(JsonObject object) {
		Message message;
		try {
			message = Message.fromJson(object);
		} catch (Exception e) {
			MMLog.severe("Could not read Message from json:");
			String exceptionMessage = e.getMessage();
			MMLog.severe(Objects.requireNonNullElse(exceptionMessage, "Exception had no message set"));
			return;
		}

		Channel channel = message.getChannel();
		if (channel == null) {
			UUID channelId = message.getChannelUniqueId();
			if (channelId != null) {
				ChannelManager.loadChannel(channelId, message);
			}
		} else {
			channel.distributeMessage(message);
		}
	}

	public void deleteMessageHandler(JsonObject object) {
		Message message;
		try {
			UUID messageId = UUID.fromString(object.getAsJsonPrimitive("id").getAsString());
			message = getMessage(messageId);
		} catch (Exception e) {
			MMLog.severe("Could not read Message deletion request from json:");
			String exceptionMessage = e.getMessage();
			MMLog.severe(Objects.requireNonNullElse(exceptionMessage, "No message set for this exception"));
			return;
		}

		if (message != null) {
			message.markDeleted();
			for (PlayerState state : PlayerStateManager.getPlayerStates().values()) {
				state.refreshChat();
			}
		}
	}

	public void deleteFromSenderHandler(JsonObject object) {
		UUID senderId;
		try {
			senderId = UUID.fromString(object.getAsJsonPrimitive("id").getAsString());
		} catch (Exception e) {
			MMLog.severe("Could not read delete from sender request from json:");
			String exceptionMessage = e.getMessage();
			MMLog.severe(Objects.requireNonNullElse(exceptionMessage, "No message was found with this exception"));
			return;
		}

		boolean changeFound = false;
		List<WeakReference<Message>> messagesCopy = new ArrayList<>(mMessages.values());
		for (WeakReference<Message> messageWeakReference : messagesCopy) {
			@Nullable Message message = messageWeakReference.get();
			if (message == null || message.isDeleted()) {
				continue;
			}

			if (senderId.equals(message.getSenderId())) {
				message.markDeleted();
				changeFound = true;
			}
		}

		if (changeFound) {
			for (PlayerState state : PlayerStateManager.getPlayerStates().values()) {
				state.refreshChat();
			}
		}
	}

	public void clearChatHandler() {
		for (PlayerState state : PlayerStateManager.getPlayerStates().values()) {
			state.clearChat();
		}
	}

	// Internal use only; used to clean up registered Messages.
	protected static Cleaner cleaner() {
		return mCleaner;
	}

	// Internal use only; register a weak reference to a Message for command use
	protected static void registerMessage(Message message) {
		UUID messageId = message.getUniqueId();
		if (messageId == null) {
			return;
		}
		if (mMessages.containsKey(messageId)) {
			MMLog.severe("Attempting to register previously registered message ID!");
		}
		mMessages.put(messageId, new WeakReference<>(message));
		MMLog.finest(() -> "New message ID " + messageId + ", tracked message IDs: " + mMessages.size());
	}

	// Internal use only; unregister a weak reference to a Message when the Message is finalized
	protected static void unregisterMessage(UUID messageId) {
		if (messageId == null) {
			return;
		}
		MMLog.finest(() -> "unregistering message ID " + messageId + ", tracked message IDs: " + mMessages.size());
		mMessages.remove(messageId);
	}
}
