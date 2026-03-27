package com.playmonumenta.redissync;

import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.player.KickedFromServerEvent;
import com.velocitypowered.api.event.player.PlayerChooseInitialServerEvent;
import com.velocitypowered.api.event.player.ServerPostConnectEvent;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;

public class VelocityListener {
	public static final String uuidToNamePath = "uuid2name";
	public static final String nameToUUIDPath = "name2uuid";

	private final MonumentaRedisSyncVelocity mPlugin;

	public VelocityListener(MonumentaRedisSyncVelocity plugin) {
		mPlugin = plugin;
	}

	@Subscribe(priority = -16384)
	public void playerChooseInitialServerEvent(PlayerChooseInitialServerEvent event) {
		Player player = event.getPlayer();
		@Nullable RegisteredServer server = event.getInitialServer().orElse(null);
		@Nullable String storedServerName = null;

		CompletableFuture<String> serverFuture;
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			serverFuture = conn.hget(locationsKey(), player.getUniqueId().toString()).toCompletableFuture();
		}
		try {
			storedServerName = serverFuture.get(6, TimeUnit.SECONDS);
		} catch (Exception ex) {
			mPlugin.mLogger.warn("Exception while getting player location for '{}': {}", player.getUsername(), ex.getMessage(), ex);
		}

		String defaultServer = ProxyConfigAPI.getDefaultServer();
		if (storedServerName == null) {
			/* Player has never connected before */
			server = mPlugin.mServer.getServer(defaultServer).orElse(null);
			storedServerName = defaultServer;
			/*
			 * If mDefaultServer is empty, no default specified - let
			 * bungee handle this based on its own config file
			 */
		} else {
			/* Player has connected before */
			server = mPlugin.mServer.getServer(storedServerName).orElse(null);
		}

		if (server != null) {
			event.setInitialServer(server);
		} else {
			player.sendMessage(Component.text("Failed to send you to '" + storedServerName + "'. Server was not found!", NamedTextColor.RED));
			mPlugin.mLogger.warn("Failed to connect player '{}' to last server '{}' Server was not found!", player.getUsername(), storedServerName);
		}
	}

	// Fix for kicks from the server
	@Subscribe(priority = 16383)
	public void kickedFromServerEvent(KickedFromServerEvent event) {
		@Nullable Component kickReason = event.getServerKickReason().orElse(null);
		// exclude servers such as purgatory
		if (
			!event.kickedDuringServerConnect() &&
				kickReason != null &&
				// We assume that if there is a message component inside RedirectPlayer, it is being done by a proxy plugin
				event.getResult() instanceof KickedFromServerEvent.RedirectPlayer redirectResult &&
				redirectResult.getMessageComponent() == null
		) {
			event.setResult(KickedFromServerEvent.DisconnectPlayer.create(kickReason));
		}
	}

	@Subscribe
	public void serverConnectEvent(ServerPostConnectEvent event) {
		Player player = event.getPlayer();
		ServerConnection server = player.getCurrentServer().orElse(null);
		if (server == null) {
			return;
		}
		String reconnectServer = server.getServerInfo().getName();
		// exclude servers such as purgatory
		if (reconnectServer == null || ProxyConfigAPI.getExcludedServers().contains(reconnectServer)) {
			return;
		}
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.hset(locationsKey(), player.getUniqueId().toString(), reconnectServer);
		}
	}

	@Subscribe
	public void postLoginEvent(PostLoginEvent event) {
		Player player = event.getPlayer();

		String name = player.getUsername();
		UUID uuid = player.getUniqueId();

		RedisAPI.multi(conn -> {
			conn.hset(uuidToNamePath, uuid.toString(), name);
			conn.hset(nameToUUIDPath, name, uuid.toString());
		});
	}

	private String locationsKey() {
		return String.format("%s:bungee:locations", CommonConfig.getServerDomain());
	}
}
