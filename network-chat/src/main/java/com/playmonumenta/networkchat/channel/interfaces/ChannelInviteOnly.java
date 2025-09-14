package com.playmonumenta.networkchat.channel.interfaces;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.CommandUtils;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public interface ChannelInviteOnly {
	default void participantsFromJson(Set<UUID> participants, JsonObject channelJson) {
		JsonArray participantsJson = channelJson.getAsJsonArray("participants");
		for (JsonElement participantJson : participantsJson) {
			participants.add(UUID.fromString(participantJson.getAsString()));
		}
	}

	default void participantsToJson(JsonObject object, Set<UUID> participants) {
		JsonArray participantsJson = new JsonArray();
		for (UUID playerId : participants) {
			participantsJson.add(playerId.toString());
		}

		object.add("participants", participantsJson);
	}

	default void addPlayer(UUID playerId) {
		addPlayer(playerId, true);
	}

	void addPlayer(UUID playerId, boolean save);

	void removePlayer(UUID playerId);

	default boolean isParticipant(CommandSender sender) {
		CommandSender callee = CommandUtils.getCallee(sender);
		if (!(callee instanceof Player player)) {
			return false;
		} else {
			return isParticipant(player);
		}
	}

	default boolean isParticipant(Player player) {
		return isParticipant(player.getUniqueId());
	}

	boolean isParticipant(UUID playerId);

	default boolean isParticipantOrModerator(CommandSender sender) {
		if (sender.hasPermission("networkchat.moderator")) {
			return true;
		}
		return isParticipant(CommandUtils.getCallee(sender));
	}

	List<UUID> getParticipantIds();
	// return new ArrayList<>(mParticipants);

	default List<String> getParticipantNames() {
		List<String> names = new ArrayList<>();
		for (UUID playerId : getParticipantIds()) {
			String name = MonumentaRedisSyncAPI.cachedUuidToName(playerId);
			if (name != null) {
				names.add(name);
			}
		}
		return names;
	}
}
