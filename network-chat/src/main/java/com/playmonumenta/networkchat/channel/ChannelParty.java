package com.playmonumenta.networkchat.channel;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.ChannelPredicate;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.interfaces.ChannelInviteOnly;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.arguments.StringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

// A channel for invited players
public class ChannelParty extends Channel implements ChannelInviteOnly {
	public static final String CHANNEL_CLASS_ID = "party";

	protected final Set<UUID> mParticipants = new TreeSet<>();

	private ChannelParty(UUID channelId, Instant lastUpdate, String name) {
		super(channelId, lastUpdate, name);
	}

	public ChannelParty(String name) {
		this(UUID.randomUUID(), Instant.now(), name);
	}

	protected ChannelParty(JsonObject channelJson) throws Exception {
		super(channelJson);
		participantsFromJson(mParticipants, channelJson);
	}

	@Override
	public JsonObject toJson() {
		JsonObject result = super.toJson();
		participantsToJson(result, mParticipants);
		return result;
	}

	public static void registerNewChannelCommands(LiteralArgument newArg, Argument<String> channelArg) {
		Argument<String> channelManageArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_MANAGE
			.and(ChannelPredicate.channelType(CHANNEL_CLASS_ID)));
		Argument<String> channelListenArg = ChannelManager.getChannelNameArgument(ChannelPredicate.MAY_LISTEN
			.and(ChannelPredicate.channelType(CHANNEL_CLASS_ID)));
		Argument<String> playerArg = new StringArgument("Player").replaceSuggestions(ChatCommand.SUGGESTIONS_VISIBLE_PLAYER_NAMES);

		ChatCommand.getBaseCommand()
			.withArguments(newArg)
			.withArguments(channelArg)
			.withArguments(new MultiLiteralArgument("Channel Type", CHANNEL_CLASS_ID))
			.executesNative((sender, args) -> {
				if (!CommandUtils.hasPermission(sender, "networkchat.new.party")) {
					throw CommandUtils.fail(sender, "You do not have permission to create party channels.");
				}

				String channelName = args.getByArgument(channelArg);
				ChannelParty newChannel;

				try {
					newChannel = new ChannelParty(channelName);
				} catch (Exception e) {
					throw CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
				}
				// Add the sender to the party if they're a player
				CommandSender callee = CommandUtils.getCallee(sender);
				if (callee instanceof Player player) {
					newChannel.addPlayer(player.getUniqueId(), false);
				}
				// Throws an exception if the channel already exists, failing the command.
				ChannelManager.registerNewChannel(sender, newChannel);
			})
			.register();

		ChatCommand.getBaseCommand()
			.withArguments(new MultiLiteralArgument("Channel Type", CHANNEL_CLASS_ID))
			.withArguments(channelManageArg)
			.withArguments(new LiteralArgument("invite"))
			.withArguments(playerArg)
			.executesNative((sender, args) -> {
				String channelName = args.getByArgument(channelManageArg);
				Channel ch = ChannelManager.getChannel(channelName);
				if (ch == null) {
					throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
				}
				if (!(ch instanceof ChannelParty channel)) {
					throw CommandUtils.fail(sender, "Channel " + channelName + " is not a party channel.");
				} else {
					if (!channel.isParticipant(sender)) {
						throw CommandUtils.fail(sender, "You are not a participant of " + channelName + ".");
					}

					String playerName = args.getByArgument(playerArg);
					UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
					if (playerId == null) {
						throw CommandUtils.fail(sender, "No such player " + playerName + ".");
					}

					sender.sendMessage(Component.text("Added " + playerName + " to " + channelName + ".", NamedTextColor.GRAY));
					channel.addPlayer(playerId);
				}
			})
			.register();

		ChatCommand.getBaseCommand()
			.withArguments(new MultiLiteralArgument("Channel Type", CHANNEL_CLASS_ID))
			.withArguments(channelManageArg)
			.withArguments(new LiteralArgument("kick"))
			.withArguments(playerArg)
			.executesNative((sender, args) -> {
				String channelName = args.getByArgument(channelManageArg);
				Channel ch = ChannelManager.getChannel(channelName);
				if (ch == null) {
					throw CommandUtils.fail(sender, "No such channel " + channelName + ".");
				}
				if (!(ch instanceof ChannelParty channel)) {
					throw CommandUtils.fail(sender, "Channel " + channelName + " is not a party channel.");
				} else {
					if (!channel.isParticipant(sender)) {
						throw CommandUtils.fail(sender, "You are not a participant of " + channelName + ".");
					}

					String playerName = args.getByArgument(playerArg);
					UUID playerId = MonumentaRedisSyncAPI.cachedNameToUuid(playerName);
					if (playerId == null) {
						throw CommandUtils.fail(sender, "No such player " + playerName + ".");
					}

					channel.removePlayer(playerId);
					sender.sendMessage(Component.text("Kicked " + playerName + " from " + channelName + ".", NamedTextColor.GRAY));
				}
			})
			.register();

		ChatCommand.getBaseCommand()
			.withArguments(new MultiLiteralArgument("Channel Type", CHANNEL_CLASS_ID))
			.withArguments(channelListenArg)
			.withArguments(new LiteralArgument("leave"))
			.executesNative((sender, args) -> {
				String channelId = args.getByArgument(channelListenArg);
				Channel ch = ChannelManager.getChannel(channelId);
				if (ch == null) {
					throw CommandUtils.fail(sender, "No such channel " + channelId + ".");
				}
				if (!(ch instanceof ChannelParty channel)) {
					throw CommandUtils.fail(sender, "Channel " + channelId + " is not a party channel.");
				} else {
					if (!channel.isParticipant(sender)) {
						throw CommandUtils.fail(sender, "You are not a participant of " + channelId + ".");
					}
					Player player = (Player) sender;

					channel.removePlayer(player.getUniqueId());
					sender.sendMessage(Component.text("You have left " + channelId + ".", NamedTextColor.GRAY));
				}
			})
			.register();
	}

	@Override
	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	@Override
	public void addPlayer(UUID playerId, boolean save) {
		mParticipants.add(playerId);
		PlayerState state = PlayerStateManager.getPlayerState(playerId);
		if (state != null) {
			if (!state.isWatchingChannelId(mId)) {
				state.joinChannel(this);
			}
		}
		if (save) {
			ChannelManager.saveChannel(this);
		}
	}

	@Override
	public void removePlayer(UUID playerId) {
		mParticipants.remove(playerId);
		if (mParticipants.isEmpty()) {
			try {
				ChannelManager.deleteChannel(getName());
			} catch (Exception e) {
				MMLog.info("Failed to delete empty channel " + getName());
			}
		} else {
			ChannelManager.saveChannel(this);
		}
	}

	@Override
	public boolean isParticipant(UUID playerId) {
		return mParticipants.contains(playerId);
	}

	@Override
	public List<UUID> getParticipantIds() {
		return new ArrayList<>(mParticipants);
	}

	@Override
	public boolean mayManage(CommandSender sender) {
		return isParticipantOrModerator(sender);
	}

	@Override
	public boolean mayChat(CommandSender sender) {
		if (!mayListen(sender)) {
			return false;
		}

		if (!CommandUtils.hasPermission(sender, "networkchat.say.party")) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!isParticipant(callee)) {
			return false;
		}
		if (!(callee instanceof Player player)) {
			return false;
		} else {
			UUID playerId = player.getUniqueId();
			ChannelAccess playerAccess = mPlayerAccess.get(playerId);
			if (playerAccess == null) {
				return !Boolean.FALSE.equals(mDefaultAccess.mayChat());
			} else {
				return !Boolean.FALSE.equals(playerAccess.mayChat());
			}
		}
	}

	@Override
	public boolean mayListen(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.see.party")) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!isParticipant(callee)) {
			return false;
		}
		if (!(callee instanceof Player player)) {
			return true;
		} else {
			UUID playerId = player.getUniqueId();
			ChannelAccess playerAccess = mPlayerAccess.get(playerId);
			if (playerAccess == null) {
				return !Boolean.FALSE.equals(mDefaultAccess.mayListen());
			} else {
				return !Boolean.FALSE.equals(playerAccess.mayListen());
			}
		}
	}

	@Override
	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.party")) {
			throw CommandUtils.fail(sender, "You do not have permission to talk in party chat.");
		}

		if (!mayChat(sender)) {
			throw CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		WrapperCommandSyntaxException notListeningEx = isListeningCheck(sender);
		if (notListeningEx != null) {
			throw notListeningEx;
		}

		@Nullable Message message = Message.createMessage(this, sender, null, messageText);
		if (message == null) {
			return;
		}

		MessageManager.getInstance().broadcastMessage(sender, message);
	}

	@Override
	public void distributeMessage(Message message) {
		showMessage(Bukkit.getConsoleSender(), message);
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			PlayerState state = playerStateEntry.getValue();
			Player player = state.getPlayer();
			if (player == null || !mayListen(player)) {
				continue;
			}

			if (state.isListening(this)) {
				// This accounts for players who have paused their chat
				state.receiveMessage(message);
			}
		}
	}

	@Override
	public Component shownMessage(CommandSender recipient, Message message) {
		TextColor channelColor;
		if (mMessageColor != null) {
			channelColor = mMessageColor;
		} else {
			channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		}
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID);
		if (prefix == null) {
			prefix = "";
		}
		prefix = prefix
			.replace("<message_gui_cmd>", message.getGuiCommand())
			.replace("<channel_description>", mDescription)
			.replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)) + " ";

		return Component.empty()
			.append(MessagingUtils.getSenderFmtMinimessage().deserialize(prefix,
				Placeholder.unparsed("channel_name", mName),
				Placeholder.component("sender", message.getSenderComponent())))
			.append(Component.empty().color(channelColor).append(message.getMessage()));
	}

	@Override
	public void showMessage(CommandSender recipient, Message message) {
		UUID senderUuid = message.getSenderId();
		recipient.sendMessage(shownMessage(recipient, message));
		if (recipient instanceof Player player && !player.getUniqueId().equals(senderUuid)) {
			@Nullable PlayerState playerState = PlayerStateManager.getPlayerState(player);
			if (playerState == null) {
				player.sendMessage(MessagingUtils.noChatState(player));
				return;
			}
			playerState.playMessageSound(message);
		}
	}
}
