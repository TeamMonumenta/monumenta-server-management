package com.playmonumenta.networkchat.channel;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.interfaces.ChannelAutoJoin;
import com.playmonumenta.networkchat.channel.interfaces.ChannelPermissionNode;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.commands.ChatCommand;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.arguments.Argument;
import dev.jorel.commandapi.arguments.BooleanArgument;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.arguments.LiteralArgument;
import dev.jorel.commandapi.arguments.MultiLiteralArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import dev.jorel.commandapi.wrappers.NativeProxyCommandSender;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.BlockCommandSender;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

// A channel visible only to this world (and moderators who opt in from elsewhere)
public class ChannelWorld extends Channel implements ChannelAutoJoin, ChannelPermissionNode {
	public static final String CHANNEL_CLASS_ID = "world";

	protected boolean mAutoJoin = true;
	private @Nullable String mChannelPermission = null;

	private ChannelWorld(UUID channelId, Instant lastUpdate, String name) {
		super(channelId, lastUpdate, name);
	}

	public ChannelWorld(String name) {
		this(UUID.randomUUID(), Instant.now(), name);
	}

	protected ChannelWorld(JsonObject channelJson) throws Exception {
		super(channelJson);
		mAutoJoin = autoJoinFromJson(channelJson);
		mChannelPermission = permissionFromJson(channelJson);
	}

	@Override
	public JsonObject toJson() {
		JsonObject result = super.toJson();
		autoJoinToJson(result, mAutoJoin);
		permissionToJson(result, mChannelPermission);
		return result;
	}

	@Override
	public boolean defaultAutoJoinState() {
		return true;
	}

	@Override
	public boolean getAutoJoin() {
		return mAutoJoin;
	}

	@Override
	public void setAutoJoin(boolean autoJoin) {
		mAutoJoin = autoJoin;
	}

	public static void registerNewChannelCommands(LiteralArgument newArg, Argument<String> channelArg) {
		BooleanArgument autoJoinArg = new BooleanArgument("Auto Join");
		GreedyStringArgument permissionArg = new GreedyStringArgument("Channel Permission");

		ChatCommand.getBaseCommand()
			.withArguments(newArg)
			.withArguments(channelArg)
			.withArguments(new MultiLiteralArgument("Channel Type", CHANNEL_CLASS_ID))
			.withOptionalArguments(autoJoinArg)
			.withOptionalArguments(permissionArg)
			.executesNative((sender, args) -> {
				if (!CommandUtils.hasPermission(sender, "networkchat.new.world")) {
					throw CommandUtils.fail(sender, "You do not have permission to create world channels.");
				}

				String channelName = args.getByArgument(channelArg);
				ChannelWorld newChannel;

				try {
					newChannel = new ChannelWorld(channelName);
					Boolean autoJoin = args.getByArgument(autoJoinArg);
					if (autoJoin != null) {
						newChannel.setAutoJoin(autoJoin);
					}
					String permission = args.getByArgument(permissionArg);
					if (permission != null && !permission.isEmpty()) {
						newChannel.setChannelPermission(permission);
					}
				} catch (Exception e) {
					throw CommandUtils.fail(sender, "Could not create new channel " + channelName + ": Could not connect to RabbitMQ.");
				}
				// Throws an exception if the channel already exists, failing the command.
				ChannelManager.registerNewChannel(sender, newChannel);
			})
			.register();
	}

	@Override
	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	@Override
	public boolean shouldAutoJoin(PlayerState state) {
		Player player = state.getPlayer();
		return getAutoJoin() && player != null && mayListen(player);
	}

	@Override
	public @Nullable String getChannelPermission() {
		return mChannelPermission;
	}

	@Override
	public boolean hasPermission(CommandSender sender) {
		return mChannelPermission == null || sender.hasPermission(mChannelPermission);
	}

	@Override
	public void setChannelPermission(@Nullable String newPerm) {
		mChannelPermission = newPerm;
	}

	@Override
	public boolean mayChat(CommandSender sender) {
		if (!mayListen(sender)) {
			return false;
		}

		if (!CommandUtils.hasPermission(sender, "networkchat.say.world")) {
			return false;
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return true;
		} else {
			ChannelAccess playerAccess = mPlayerAccess.get(player.getUniqueId());
			if (playerAccess == null) {
				return !Boolean.FALSE.equals(mDefaultAccess.mayChat());
			} else {
				return !Boolean.FALSE.equals(playerAccess.mayChat());
			}
		}
	}

	@Override
	public boolean mayListen(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.see.world")) {
			return false;
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
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
		if (!CommandUtils.hasPermission(sender, "networkchat.say.world")) {
			throw CommandUtils.fail(sender, "You do not have permission to talk in world chat.");
		}
		if (mChannelPermission != null && !CommandUtils.hasPermission(sender, mChannelPermission)) {
			throw CommandUtils.fail(sender, "You do not have permission to talk in " + mName + ".");
		}

		if (!mayChat(sender)) {
			throw CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		WrapperCommandSyntaxException notListeningEx = isListeningCheck(sender);
		if (notListeningEx != null) {
			throw notListeningEx;
		}

		World world;
		if (sender instanceof NativeProxyCommandSender nativeSender) {
			world = nativeSender.getWorld();
		} else if (sender instanceof Entity entity) {
			world = entity.getWorld();
		} else if (sender instanceof BlockCommandSender blockCommandSender) {
			world = blockCommandSender.getBlock().getWorld();
		} else {
			MMLog.warning("Unable to get world for world channel message for sender " + sender);
			throw CommandUtils.fail(sender, "Unable to get world for world channel message.");
		}

		JsonObject extraData = new JsonObject();
		extraData.addProperty("fromShard", NetworkChatPlugin.getShardName());
		extraData.addProperty("fromWorld", world.getName());

		@Nullable Message message = Message.createMessage(this, sender, extraData, messageText);
		if (message == null) {
			return;
		}

		MessageManager.getInstance().broadcastMessage(sender, message);
	}

	@Override
	public void distributeMessage(Message message) {
		JsonObject extraData = message.getExtraData();
		if (extraData == null) {
			MMLog.warning("Got world chat message with no fromShard, ignoring.");
			return;
		}

		String fromShard;
		JsonElement fromShardJsonElement = extraData.get("fromShard");
		if (!fromShardJsonElement.isJsonPrimitive()) {
			MMLog.warning("Got world chat message with invalid fromShard json, ignoring.");
			return;
		}
		JsonPrimitive fromShardJsonPrimitive = fromShardJsonElement.getAsJsonPrimitive();
		if (!fromShardJsonPrimitive.isString()) {
			MMLog.warning("Got world chat message with invalid fromShard json, ignoring.");
			return;
		}
		fromShard = fromShardJsonPrimitive.getAsString();
		if (!fromShard.equals(NetworkChatPlugin.getShardName())) {
			// TODO Chat spy here
			return;
		}

		String fromWorld;
		JsonElement fromWorldJsonElement = extraData.get("fromWorld");
		if (!fromWorldJsonElement.isJsonPrimitive()) {
			MMLog.warning("Got world chat message with invalid fromWorld json, ignoring.");
			return;
		}
		JsonPrimitive fromWorldJsonPrimitive = fromWorldJsonElement.getAsJsonPrimitive();
		if (!fromWorldJsonPrimitive.isString()) {
			MMLog.warning("Got world chat message with invalid fromWorld json, ignoring.");
			return;
		}
		fromWorld = fromWorldJsonPrimitive.getAsString();

		showMessage(Bukkit.getConsoleSender(), message);
		for (Map.Entry<UUID, PlayerState> playerStateEntry : PlayerStateManager.getPlayerStates().entrySet()) {
			PlayerState state = playerStateEntry.getValue();
			Player player = state.getPlayer();
			if (player == null || !player.getWorld().getName().equals(fromWorld) || !mayListen(player)) {
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
