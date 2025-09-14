package com.playmonumenta.networkchat.channel;

import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.ChannelManager;
import com.playmonumenta.networkchat.Message;
import com.playmonumenta.networkchat.MessageManager;
import com.playmonumenta.networkchat.NetworkChatPlugin;
import com.playmonumenta.networkchat.PlayerState;
import com.playmonumenta.networkchat.PlayerStateManager;
import com.playmonumenta.networkchat.channel.property.ChannelAccess;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import dev.jorel.commandapi.CommandAPI;
import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.arguments.GreedyStringArgument;
import dev.jorel.commandapi.exceptions.WrapperCommandSyntaxException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Team;

// A channel visible to all shards
public class ChannelTeam extends Channel {
	public static final String CHANNEL_CLASS_ID = "team";
	private static final String[] TEAM_COMMANDS = {"teammsg", "tm"};

	private final String mTeamName;

	private ChannelTeam(UUID channelId, Instant lastUpdate, String teamName) {
		super(channelId, lastUpdate, "Team_" + teamName);
		mTeamName = teamName;
	}

	public ChannelTeam(String teamName) {
		this(UUID.randomUUID(), Instant.now(), teamName);
	}

	protected ChannelTeam(JsonObject channelJson) throws Exception {
		super(channelJson);
		mTeamName = channelJson.getAsJsonPrimitive("team").getAsString();
	}

	@Override
	public JsonObject toJson() {
		JsonObject result = super.toJson();
		result.addProperty("team", mTeamName);
		return result;
	}

	public static void registerNewChannelCommands() {
		// Setting up new team channels will be done via /teammsg, /tm, and similar,
		// not through /chat new Blah team. The provided arguments are ignored.
		GreedyStringArgument messageArg = new GreedyStringArgument("message");

		for (String command : TEAM_COMMANDS) {
			CommandAPI.unregister(command);

			new CommandAPICommand(command)
				.executesNative((sender, args) -> {
					return runCommandSet(sender);
				})
				.register();

			new CommandAPICommand(command)
				.withArguments(messageArg)
				.executesNative((sender, args) -> {
					return runCommandSay(sender, args.getByArgument(messageArg));
				})
				.register();
		}
	}

	private static int runCommandSet(CommandSender sender) throws WrapperCommandSyntaxException {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player sendingPlayer)) {
			sender.sendMessage(Component.translatable("permissions.requires.player"));
			throw CommandUtils.fail(sender, "A player is required to run this command here");
		} else {
			Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(sendingPlayer.getName());
			if (team == null) {
				sender.sendMessage(Component.translatable("commands.teammsg.failed.noteam"));
				throw CommandUtils.fail(sender, sendingPlayer.getName() + " must be on a team to message their team.");
			}
			String teamName = team.getName();

			Channel channel = ChannelManager.getChannel("Team_" + teamName);
			if (channel == null) {
				try {
					channel = new ChannelTeam(teamName);
				} catch (Exception e) {
					throw CommandUtils.fail(sender, "Could not create new team channel: Could not connect to RabbitMQ.");
				}
				ChannelManager.registerNewChannel(sender, channel);
			}

			@Nullable PlayerState senderState = PlayerStateManager.getPlayerState(sendingPlayer);
			if (senderState == null) {
				sendingPlayer.sendMessage(MessagingUtils.noChatState(sendingPlayer));
				return 0;
			}
			senderState.setActiveChannel(channel);
			sender.sendMessage(Component.text("You are now typing to team ", NamedTextColor.GRAY).append(team.displayName()));
		}
		return 1;
	}

	private static int runCommandSay(CommandSender sender, String message) throws WrapperCommandSyntaxException {
		@Nullable PlayerState playerState = null;
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Entity sendingEntity)) {
			sender.sendMessage(Component.translatable("permissions.requires.entity"));
			throw CommandUtils.fail(sender, "An entity is required to run this command here");
		} else {
			Team team;
			if (sendingEntity instanceof Player player) {
				playerState = PlayerStateManager.getPlayerState(player);
				if (playerState == null) {
					throw CommandUtils.fail(sender, MessagingUtils.noChatStateStr(player));
				} else if (playerState.isPaused()) {
					throw CommandUtils.fail(sender, "You cannot chat with chat paused (/chat unpause)");
				}
				team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(sendingEntity.getName());
			} else {
				team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(sendingEntity.getUniqueId().toString());
			}
			if (team == null) {
				sender.sendMessage(Component.translatable("commands.teammsg.failed.noteam"));
				throw CommandUtils.fail(sender, sendingEntity.getName() + " must be on a team to message their team.");
			}
			String teamName = team.getName();

			Channel channel = ChannelManager.getChannel("Team_" + teamName);
			if (channel == null) {
				try {
					channel = new ChannelTeam(teamName);
				} catch (Exception e) {
					throw CommandUtils.fail(sender, "Could not create new team channel: Could not connect to RabbitMQ.");
				}
				ChannelManager.registerNewChannel(sender, channel);
			}

			if (playerState != null) {
				playerState.joinChannel(channel);
			}
			channel.sendMessage(sendingEntity, message);
		}
		return 1;
	}

	@Override
	public String getClassId() {
		return CHANNEL_CLASS_ID;
	}

	@Override
	public void setName(String name) throws WrapperCommandSyntaxException {
		throw CommandAPI.failWithString("Team channels may not be named.");
	}

	@Override
	public String getName() {
		return "Team_" + mTeamName;
	}

	@Override
	public @Nullable
	TextColor color() {
		return null;
	}

	@Override
	public void color(CommandSender sender, @Nullable TextColor color) throws WrapperCommandSyntaxException {
		throw CommandUtils.fail(sender, "Team channels do not support custom text colors.");
	}

	@Override
	public boolean shouldAutoJoin(PlayerState state) {
		return true;
	}

	@Override
	public boolean mayChat(CommandSender sender) {
		if (!mayListen(sender)) {
			return false;
		}

		if (!CommandUtils.hasPermission(sender, "networkchat.say.team")) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return true;
		} else {
			ChannelAccess playerAccess = mPlayerAccess.get(player.getUniqueId());
			if (playerAccess == null) {
				if (Boolean.FALSE.equals(mDefaultAccess.mayChat())) {
					return false;
				}
			} else if (Boolean.FALSE.equals(playerAccess.mayChat())) {
				return false;
			}

			Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
			if (team == null) {
				return false;
			}
			String teamName = team.getName();
			return mTeamName.equals(teamName);
		}
	}

	@Override
	public boolean mayListen(CommandSender sender) {
		if (!CommandUtils.hasPermission(sender, "networkchat.see.team")) {
			return false;
		}

		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return false;
		} else {
			UUID playerId = player.getUniqueId();

			ChannelAccess playerAccess = mPlayerAccess.get(playerId);
			if (playerAccess == null) {
				if (Boolean.FALSE.equals(mDefaultAccess.mayListen())) {
					return false;
				}
			} else if (Boolean.FALSE.equals(playerAccess.mayListen())) {
				return false;
			}

			Team team = Bukkit.getScoreboardManager().getMainScoreboard().getEntryTeam(player.getName());
			if (team == null) {
				return false;
			}
			String teamName = team.getName();
			return mTeamName.equals(teamName);
		}
	}

	@Override
	public void sendMessage(CommandSender sender, String messageText) throws WrapperCommandSyntaxException {
		if (!CommandUtils.hasPermission(sender, "networkchat.say.team")) {
			throw CommandUtils.fail(sender, "You do not have permission to talk to a team.");
		}

		if (!mayChat(sender)) {
			throw CommandUtils.fail(sender, "You do not have permission to chat in this channel.");
		}

		WrapperCommandSyntaxException notListeningEx = isListeningCheck(sender);
		if (notListeningEx != null) {
			throw notListeningEx;
		}

		JsonObject extraData = new JsonObject();
		extraData.addProperty("team", mTeamName);

		@Nullable Message message = Message.createMessage(this, sender, extraData, messageText);
		if (message == null) {
			return;
		}

		MessageManager.getInstance().broadcastMessage(sender, message);
	}

	@Override
	public void distributeMessage(Message message) {
		showMessage(Bukkit.getConsoleSender(), message);

		JsonObject extraData = message.getExtraData();
		if (extraData == null) {
			MMLog.warning("Could not get Team from Message; no extraData provided");
			return;
		}
		String teamName;
		try {
			teamName = extraData.getAsJsonPrimitive("team").getAsString();
		} catch (Exception e) {
			MMLog.warning("Could not get Team from Message; reason: " + e.getMessage());
			return;
		}
		Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
		if (team == null) {
			MMLog.finer("No such team " + teamName + " on this shard, ignoring.");
			return;
		}

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
		JsonObject extraData = message.getExtraData();
		if (extraData == null) {
			MMLog.warning("Could not get Team from Message; no extraData provided");
			return Component.text("[Could not get team from Message]", NamedTextColor.RED, TextDecoration.BOLD);
		}
		String teamName;
		try {
			teamName = extraData.getAsJsonPrimitive("team").getAsString();
		} catch (Exception e) {
			MMLog.warning("Could not get Team from Message; reason: " + e.getMessage());
			MessagingUtils.sendStackTrace(Bukkit.getConsoleSender(), e);
			return Component.text("[Could not get team from Message]", NamedTextColor.RED, TextDecoration.BOLD);
		}

		Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(teamName);
		TextColor color;
		Component teamPrefix;
		Component teamDisplayName;
		Component teamSuffix;
		try {
			if (team != null) {
				color = team.color();
				teamPrefix = team.prefix();
				teamDisplayName = team.displayName();
				teamSuffix = team.suffix();
			} else {
				color = null;
				teamPrefix = Component.empty();
				teamDisplayName = Component.empty();
				teamSuffix = Component.empty();
			}
		} catch (Exception e) {
			color = null;
			teamPrefix = Component.empty();
			teamDisplayName = Component.empty();
			teamSuffix = Component.empty();
		}

		TextColor channelColor = NetworkChatPlugin.messageColor(CHANNEL_CLASS_ID);
		String prefix = NetworkChatPlugin.messageFormat(CHANNEL_CLASS_ID);
		if (prefix == null) {
			prefix = "";
		}
		prefix = prefix
			.replace("<message_gui_cmd>", message.getGuiCommand())
			.replace("<channel_color>", MessagingUtils.colorToMiniMessage(channelColor)
			.replace("<channel_description>", mDescription)) + " ";

		return Component.empty()
			.append(MessagingUtils.getSenderFmtMinimessage().deserialize(prefix,
				Placeholder.component("sender", message.getSenderComponent()),
				Placeholder.parsed("team_color", (color == null) ? "" : "<" + color.asHexString() + ">"),
				Placeholder.component("team_prefix", teamPrefix),
				Placeholder.component("team_displayname", teamDisplayName),
				Placeholder.component("team_suffix", teamSuffix)))
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
