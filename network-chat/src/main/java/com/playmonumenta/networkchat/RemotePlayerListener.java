package com.playmonumenta.networkchat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.playmonumenta.networkchat.utils.MMLog;
import com.playmonumenta.networkchat.utils.MessagingUtils;
import com.playmonumenta.networkrelay.GatherRemotePlayerDataEvent;
import com.playmonumenta.networkrelay.NetworkRelayAPI;
import com.playmonumenta.networkrelay.RemotePlayerAPI;
import com.playmonumenta.networkrelay.RemotePlayerData;
import com.playmonumenta.networkrelay.RemotePlayerLoadedEvent;
import com.playmonumenta.networkrelay.RemotePlayerMinecraft;
import com.playmonumenta.networkrelay.RemotePlayerUnloadedEvent;
import com.playmonumenta.networkrelay.RemotePlayerUpdatedEvent;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListMap;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.Nullable;

public class RemotePlayerListener implements Listener {
	private static @Nullable RemotePlayerListener INSTANCE = null;
	private static final Map<UUID, Component> mPlayerComponents = new HashMap<>();

	private RemotePlayerListener() {
	}

	public static RemotePlayerListener getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new RemotePlayerListener();
		}
		return INSTANCE;
	}

	public static void refreshLocalPlayers() {
		for (Player player : Bukkit.getOnlinePlayers()) {
			refreshLocalPlayer(player);
		}
	}

	public static void refreshLocalPlayer(Player player) {
		RemotePlayerAPI.refreshPlayer(player.getUniqueId());
	}

	public static Component getPlayerComponent(UUID playerUuid) {
		Component playerComponent = mPlayerComponents.get(playerUuid);
		if (playerComponent != null) {
			return playerComponent;
		}

		@Nullable String playerName = MonumentaRedisSyncAPI.cachedUuidToName(playerUuid);
		if (playerName == null) {
			return Component.empty();
		}

		return Component.text(playerName, NamedTextColor.RED)
			.hoverEvent(Component.text("Offline", NamedTextColor.RED));
	}

	public static void showOnlinePlayers(Audience audience) {
		String localShardName = NetworkChatPlugin.getShardName();
		boolean lightRow = true;
		boolean firstName;
		// Sorts as it goes
		Map<String, Component> shardPlayers;

		audience.sendMessage(Component.text("Online players:", NamedTextColor.DARK_BLUE));

		// Local first
		shardPlayers = new ConcurrentSkipListMap<>();
		for (RemotePlayerData remotePlayerData : RemotePlayerAPI.getVisiblePlayersOnServer(localShardName)) {
			UUID playerUuid = remotePlayerData.mUuid;
			Component playerComponent = mPlayerComponents.get(playerUuid);
			if (remotePlayerData.isHidden() || playerComponent == null) {
				continue;
			}

			shardPlayers.put(remotePlayerData.mName, playerComponent);
		}
		firstName = true;
		Component line = Component.text(localShardName + ": ").color(NamedTextColor.BLUE);
		for (Component playerComp : shardPlayers.values()) {
			if (!firstName) {
				line = line.append(Component.text(", "));
			}
			line = line.append(playerComp);
			firstName = false;
		}
		audience.sendMessage(line);

		// Remote shards
		for (String remoteShardName : NetworkRelayAPI.getOnlineShardNames()) {
			if (localShardName.equals(remoteShardName)) {
				continue;
			}
			if (!"minecraft".equals(NetworkRelayAPI.getOnlineDestinationType(remoteShardName))) {
				continue;
			}
			shardPlayers.clear();
			lightRow = !lightRow;
			for (RemotePlayerData remotePlayerData : RemotePlayerAPI.getVisiblePlayersOnServer(remoteShardName)) {
				UUID playerUuid = remotePlayerData.mUuid;
				Component playerComponent = mPlayerComponents.get(playerUuid);
				if (remotePlayerData.isHidden() || playerComponent == null) {
					continue;
				}
				String playerName = remotePlayerData.mName;
				shardPlayers.put(playerName, playerComponent);
			}
			firstName = true;
			line = Component.text(remoteShardName + ": ").color(lightRow ? NamedTextColor.BLUE : NamedTextColor.DARK_BLUE);
			for (Component playerComp : shardPlayers.values()) {
				if (!firstName) {
					line = line.append(Component.text(", "));
				}
				line = line.append(playerComp);
				firstName = false;
			}
			audience.sendMessage(line);
		}
	}

	private static void updatePlayerComponent(RemotePlayerMinecraft minecraftPlayerData) {
		String pluginName = NetworkChatPlugin.getInstance().getName();
		UUID playerUuid = minecraftPlayerData.getUuid();
		JsonObject chatMinecraftData = minecraftPlayerData.getPluginData(pluginName);
		if (chatMinecraftData == null) {
			unsetPlayerComponent(minecraftPlayerData);
			return;
		}
		JsonElement componentJson = chatMinecraftData.get("playerComponent");
		if (componentJson == null) {
			unsetPlayerComponent(minecraftPlayerData);
			return;
		}
		Component playerComponent = MessagingUtils.fromJson(componentJson);
		mPlayerComponents.put(playerUuid, playerComponent);
	}

	private static void unsetPlayerComponent(RemotePlayerMinecraft minecraftPlayerData) {
		UUID playerUuid = minecraftPlayerData.getUuid();
		mPlayerComponents.remove(playerUuid);
	}

	// Player ran a command
	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void playerCommandPreprocessEvent(PlayerCommandPreprocessEvent event) {
		String command = event.getMessage();

		if (command.startsWith("/team ")
			|| command.contains(" run team ")) {
			new BukkitRunnable() {
				@Override
				public void run() {
					refreshLocalPlayers();
				}
			}.runTaskLater(NetworkChatPlugin.getInstance(), 0);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void gatherRemotePlayerDataEvent(GatherRemotePlayerDataEvent event) {
		UUID playerId = event.mRemotePlayer.getUuid();
		String playerName = event.mRemotePlayer.getName();
		MMLog.fine("[RPM Listener] Refreshing " + playerName);

		Player player = Bukkit.getPlayer(playerId);
		if (player == null) {
			MMLog.fine("[RPM Listener] Player not found, not entering NetworkChat data: " + playerName);
			return;
		}

		JsonObject playerJson = new JsonObject();

		Component component;
		Boolean nullableIsHidden = event.mRemotePlayer.isHidden();
		boolean isHidden = nullableIsHidden == null || nullableIsHidden;
		if (isHidden) {
			component = Component.text(playerName, NamedTextColor.RED)
			.hoverEvent(Component.text("Offline", NamedTextColor.RED));
		} else {
			component = MessagingUtils.playerComponent(player);
		}
		playerJson.add("playerComponent", MessagingUtils.toJson(component));

		event.setPluginData(NetworkChatPlugin.getInstance().getName(), playerJson);
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void remotePlayerLoadedEvent(RemotePlayerLoadedEvent event) {
		if (event.mRemotePlayer instanceof RemotePlayerMinecraft minecraftPlayerData) {
			updatePlayerComponent(minecraftPlayerData);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void remotePlayerUpdatedEvent(RemotePlayerUpdatedEvent event) {
		if (event.mRemotePlayer instanceof RemotePlayerMinecraft minecraftPlayerData) {
			updatePlayerComponent(minecraftPlayerData);
		}
	}

	@EventHandler(ignoreCancelled = true, priority = EventPriority.LOW)
	public void remotePlayerUnloadedEvent(RemotePlayerUnloadedEvent event) {
		if (event.mRemotePlayer instanceof RemotePlayerMinecraft minecraftPlayerData) {
			unsetPlayerComponent(minecraftPlayerData);
		}
	}
}
