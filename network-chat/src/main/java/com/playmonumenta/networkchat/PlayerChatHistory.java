package com.playmonumenta.networkchat;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

// Container for chat messages to be replayed if a message is deleted.
// This is discarded if the player has been offline long enough.
public class PlayerChatHistory {
	protected static final String NETWORK_CHAT_PLAYER_CHAT_HISTORY = "com.playmonumenta.networkchat.player_chat_history";

	// From vanilla client limits
	public static final int MAX_DISPLAYED_MESSAGES = 100;
	protected static final long MAX_OFFLINE_HISTORY_SECONDS = 10;

	private final UUID mPlayerId;
	private boolean mIsReplayingChat = false;
	private boolean mIsDisplayingMessage = false;
	// Messages the player has seen
	private List<Message> mSeenMessages = new ArrayList<>(MAX_DISPLAYED_MESSAGES);
	// Messages the player should see, but has not yet due to paused chat
	private List<Message> mUnseenMessages = new ArrayList<>(MAX_DISPLAYED_MESSAGES);
	// Messages received while chat is being refreshed, to be sorted into seen/unseen
	private List<Message> mUnprocessedMessages = new ArrayList<>(MAX_DISPLAYED_MESSAGES);

	public PlayerChatHistory(UUID playerId) {
		mPlayerId = playerId;
	}

	public void updateFromJson(JsonObject obj) {
		JsonArray seenMessagesJson = obj.getAsJsonArray("seenMessages");
		if (seenMessagesJson != null) {
			mSeenMessages = new ArrayList<>(MAX_DISPLAYED_MESSAGES);
			for (JsonElement messageJson : seenMessagesJson) {
				if (messageJson instanceof JsonObject messageJsonObject) {
					Message message = Message.fromJson(messageJsonObject);
					mSeenMessages.add(message);
				}
			}
		}

		JsonArray unseenMessagesJson = obj.getAsJsonArray("unseenMessages");
		if (unseenMessagesJson != null) {
			mUnseenMessages = new ArrayList<>(MAX_DISPLAYED_MESSAGES);
			for (JsonElement messageJson : unseenMessagesJson) {
				if (messageJson instanceof JsonObject messageJsonObject) {
					Message message = Message.fromJson(messageJsonObject);
					while (mUnseenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
						mUnseenMessages.remove(0);
					}
					mUnseenMessages.add(message);
				}
			}
		}

		JsonArray unprocessedMessagesJson = obj.getAsJsonArray("unprocessedMessages");
		if (unprocessedMessagesJson != null) {
			mUnprocessedMessages = new ArrayList<>(MAX_DISPLAYED_MESSAGES);
			for (JsonElement messageJson : unprocessedMessagesJson) {
				if (messageJson instanceof JsonObject messageJsonObject) {
					Message message = Message.fromJson(messageJsonObject);
					while (mUnprocessedMessages.size() >= MAX_DISPLAYED_MESSAGES) {
						mUnprocessedMessages.remove(0);
					}
					mUnprocessedMessages.add(message);
				}
			}
		}
	}

	public JsonObject toJson() {
		List<Message> seenMessagesCopy = new ArrayList<>(mSeenMessages);
		JsonArray seenMessages = new JsonArray();
		for (Message message : seenMessagesCopy) {
			seenMessages.add(message.toJson());
		}

		List<Message> unseenMessagesCopy = new ArrayList<>(mUnseenMessages);
		JsonArray unseenMessages = new JsonArray();
		for (Message message : unseenMessagesCopy) {
			unseenMessages.add(message.toJson());
		}

		List<Message> unprocessedMessagesCopy = new ArrayList<>(mUnseenMessages);
		JsonArray unprocessedMessages = new JsonArray();
		for (Message message : unprocessedMessagesCopy) {
			unprocessedMessages.add(message.toJson());
		}

		JsonObject result = new JsonObject();
		result.addProperty("playerId", mPlayerId.toString());
		result.add("seenMessages", seenMessages);
		result.add("unseenMessages", unseenMessages);
		result.add("unprocessedMessages", unprocessedMessages);

		return result;
	}

	public Player getPlayer() {
		return Bukkit.getPlayer(mPlayerId);
	}

	public PlayerState getPlayerState() {
		PlayerState playerState = PlayerStateManager.getPlayerState(mPlayerId);
		if (playerState == null) {
			throw new RuntimeException("PlayerState is somehow null before it should be!");
		}
		return playerState;
	}

	/**
	 * Receives a message that is from a chat channel
	 * @param message The message to display
	 */
	public void receiveMessage(Message message) {
		if (message.isDeleted()) {
			return;
		}

		if (mIsReplayingChat) {
			while (mUnprocessedMessages.size() >= MAX_DISPLAYED_MESSAGES) {
				// The message may be used elsewhere
				//noinspection resource
				mUnprocessedMessages.remove(0);
			}
			mUnprocessedMessages.add(message);
			return;
		}

		if (getPlayerState().isPaused()) {
			while (mUnseenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
				// The message may be used elsewhere
				//noinspection resource
				mUnseenMessages.remove(0);
			}
			mUnseenMessages.add(message);
		} else {
			while (mSeenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
				// The message may be used elsewhere
				//noinspection resource
				mSeenMessages.remove(0);
			}
			mIsDisplayingMessage = true;
			message.showMessage(getPlayer());
			mIsDisplayingMessage = false;
			mSeenMessages.add(message);
		}
	}

	/**
	 * Receives a message that isn't from a chat channel
	 * @param message The message to display
	 */
	public void receiveExternalMessage(Message message) {
		if (mIsDisplayingMessage) {
			return;
		}

		if (mIsReplayingChat) {
			while (mUnprocessedMessages.size() >= MAX_DISPLAYED_MESSAGES) {
				// The message may be used elsewhere
				//noinspection resource
				mUnprocessedMessages.remove(0);
			}
			mUnprocessedMessages.add(message);
			return;
		}

		while (mSeenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
			// The message may be used elsewhere
			//noinspection resource
			mSeenMessages.remove(0);
		}
		mSeenMessages.add(message);
	}

	/**
	 * Displays messages that were received while refreshing chat
	 */
	public void processUnprocessedMessages() {
		while (!mUnprocessedMessages.isEmpty()) {
			Message message = mUnprocessedMessages.remove(0);

			if (message.isDeleted()) {
				continue;
			}

			if (message.getChannelUniqueId() != null) {
				// Channel message
				receiveMessage(message);
			} else {
				// External message
				mIsDisplayingMessage = true;
				message.showMessage(getPlayer());
				mIsDisplayingMessage = false;
				receiveExternalMessage(message);
			}
		}
	}

	// Re-show chat with deleted messages removed, even while paused.
	public void refreshChat() {
		mIsReplayingChat = true;
		mSeenMessages.removeIf(message -> {
			if (message.senderIsPlayer() && mPlayerId.equals(message.getSenderId())) {
				// Show deleted messages to the original sender
				return false;
			}

			return message.isDeleted();
		});

		Component emptyMessage = Component.empty();

		mIsDisplayingMessage = true;
		for (int i = mSeenMessages.size(); i < MAX_DISPLAYED_MESSAGES; ++i) {
			getPlayer().sendMessage(emptyMessage);
		}
		for (Message message : mSeenMessages) {
			message.showMessage(getPlayer());
		}
		mIsDisplayingMessage = false;

		mIsReplayingChat = false;
		if (!getPlayerState().isPaused()) {
			showUnseen();
		}
		processUnprocessedMessages();
	}

	public void clearChat() {
		mUnseenMessages.clear();
		mSeenMessages.clear();
		mSeenMessages.add(Message.createRawMessage(null, null, Component.text("Chat has been cleared.", NamedTextColor.RED, TextDecoration.BOLD)));
		refreshChat();
	}

	public boolean isReplayingChat() {
		return mIsReplayingChat;
	}

	public boolean isPaused() {
		return getPlayerState().isPaused();
	}

	public void pauseChat() {
		getPlayerState().setPauseState(true);
	}

	public void unpauseChat() {
		// Delete old seen messages
		if (mUnseenMessages.size() + mSeenMessages.size() > MAX_DISPLAYED_MESSAGES) {
			int newSeenStartIndex = Integer.min(MAX_DISPLAYED_MESSAGES, mUnseenMessages.size() + mSeenMessages.size() - MAX_DISPLAYED_MESSAGES);
			int newSeenToIndex = Integer.min(MAX_DISPLAYED_MESSAGES, mSeenMessages.size());
			mSeenMessages = mSeenMessages.subList(newSeenStartIndex, newSeenToIndex);
		}
		showUnseen();
	}

	private void showUnseen() {
		while (mUnseenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
			mUnseenMessages.remove(0);
		}

		mIsDisplayingMessage = true;
		Player player = getPlayer();
		for (Message message : mUnseenMessages) {
			message.showMessage(player);
		}
		mSeenMessages.addAll(mUnseenMessages);
		mUnseenMessages.clear();
		mIsDisplayingMessage = false;

		getPlayerState().setPauseState(false);
		while (mSeenMessages.size() >= MAX_DISPLAYED_MESSAGES) {
			mSeenMessages.remove(0);
		}
	}
}
