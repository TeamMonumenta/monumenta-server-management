package com.playmonumenta.networkrelay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.PostOrder;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.plugin.PluginManager;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.proxy.server.ServerInfo;
import com.viaversion.viaversion.api.Via;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class NetworkMessageListenerVelocity {
	private static final List<NetworkRelayAPI.ServerType> ACCEPTED_SERVER_TYPES = Arrays.asList(NetworkRelayAPI.ServerType.ALL, NetworkRelayAPI.ServerType.PROXY);

	private final Logger mLogger;
	private final ProxyServer mServer;
	private final boolean mRunReceivedCommands;
	private final boolean mAutoRegisterServersToBungee;
	private final boolean mAutoUnregisterInactiveServersFromBungee;

	protected NetworkMessageListenerVelocity(boolean runReceivedCommands, boolean autoRegisterServersToBungee, boolean autoUnregisterInactiveServersFromBungee) {
		mLogger = NetworkRelayVelocity.getInstance().mLogger;
		mServer = NetworkRelayVelocity.getInstance().mServer;
		mRunReceivedCommands = runReceivedCommands;
		mAutoRegisterServersToBungee = autoRegisterServersToBungee;
		mAutoUnregisterInactiveServersFromBungee = autoUnregisterInactiveServersFromBungee;
	}

	@Subscribe
	public void gatherHeartbeatData(GatherHeartbeatDataEventVelocity event) {
		JsonObject data = new JsonObject();
		data.addProperty("server-type", "proxy");
		data.add("shard-pings", VelocityShardPingManager.getShardPingsJson());
		event.setPluginData(NetworkRelayAPI.NETWORK_RELAY_HEARTBEAT_IDENTIFIER, data);
	}

	@Subscribe
	public void gatherRemotePlayerData(GatherRemotePlayerDataEventVelocity event) {
		JsonObject data = new JsonObject();

		PluginManager pluginManager = NetworkRelayVelocity.getInstance().mServer.getPluginManager();
		if (pluginManager.isLoaded("viaversion")) {
			int protocolVersionNumber = Via.getAPI().getPlayerVersion(event.mRemotePlayer.mUuid);
			data.addProperty("protocol_version", protocolVersionNumber);
		}

		event.setPluginData("networkrelay", data);
	}

	@Subscribe(order = PostOrder.FIRST)
	public @Nullable EventTask networkRelayMessageEvent(NetworkRelayMessageEventGeneric event) {
		if (!mRunReceivedCommands) {
			return null;
		}

		if (!event.getChannel().equals(NetworkRelayAPI.COMMAND_CHANNEL)) {
			return null;
		}

		return EventTask.async(() -> {
			JsonObject data = event.getData();
			if (!data.has("command") || !data.get("command").isJsonPrimitive() || !data.getAsJsonPrimitive("command").isString()) {
				mLogger.warn("Got invalid command message with no actual command");
				return;
			}

			boolean warnLegacyServerType = false;
			JsonPrimitive serverTypeJson = data.getAsJsonPrimitive("server-type");
			if (serverTypeJson != null) {
				String serverTypeString = serverTypeJson.getAsString();
				if (serverTypeString != null) {
					if ("bungee".equals(serverTypeString)) {
						warnLegacyServerType = true;
					}
					NetworkRelayAPI.ServerType commandType = NetworkRelayAPI.ServerType.fromString(serverTypeString);
					if (!ACCEPTED_SERVER_TYPES.contains(commandType)) {
						return;
					}
				}
			}

			final String command = data.get("command").getAsString();
			if (warnLegacyServerType) {
				mLogger.warn("Executing command'" + command + "' from source '" + event.getSource() + "'; legacy server type 'bungee' was requested");
			} else {
				mLogger.info("Executing command'" + command + "' from source '" + event.getSource() + "'");
			}

			// TODO: we really shouldn't be using getConsoleCommandSource, fix this in the future - usb
			try {
				mServer.getCommandManager().executeAsync(mServer.getConsoleCommandSource(), data.get("command").getAsString());
			} catch (Exception ex) {
				mLogger.error("Error occured when trying to execute" + data.get("command").getAsString(), ex);
			}
		});
	}

	@Subscribe(order = PostOrder.NORMAL)
	public void destOnline(DestOnlineEventGeneric event) {
		if (!mAutoRegisterServersToBungee) {
			return;
		}

		String name = event.getDest();

		JsonObject data = NetworkRelayAPI.getHeartbeatPluginData(name, NetworkRelayAPI.NETWORK_RELAY_HEARTBEAT_IDENTIFIER);

		if (data != null && data.has("server-type")) {
			JsonElement element = data.get("server-type");
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				String serverType = element.getAsString();
				if (!"minecraft".equals(serverType)) {
					// Only add Minecraft servers as servers, not bungee or other shards
					return;
				}
			}
		}

		String serverAddress = null;
		if (data != null && data.has("server-address")) {
			JsonElement element = data.get("server-address");
			if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isString()) {
				String addr = element.getAsString();
				if (addr != null && !addr.isEmpty()) {
					serverAddress = addr;
				}
			}
		}
		if (serverAddress == null) {
			mLogger.warn("auto-register-servers-to-bungee=true but shard '" + name + "' is misconfigured and didn't send a server address");
			return;
		}

		InetSocketAddress addr;
		try {
			// can't actually make an address through API anymore
			addr = getAddr(serverAddress);
		} catch (Exception ex) {
			addr = null;
		}
		if (addr == null) {
			mLogger.warn("Tried to add server '" + name + "' but address '" + serverAddress + "' is invalid");
			return;
		}

		// move duplicate server check after to ensure that addresses are the same
		RegisteredServer server = mServer.getServer(name).orElse(null);
		ServerInfo info = server == null ? null : server.getServerInfo();
		if (info != null) {
			InetSocketAddress previousAddress = info.getAddress();
			// check if addresses are different
			if (previousAddress != null && !previousAddress.equals(addr)) {
				// if addresses are different, remove the old address
				mLogger.warn("Server: '" + name + "' has different address now (Old: '" + previousAddress + "') (New: '" + addr + "')");
				mLogger.warn("Removing old server with different address...");
				mServer.unregisterServer(info); // Deprecation note: This whole class is deprecated to discourage use, but no other options exist
			} else {
				// otherwise complain and ignore
				mLogger.info("Tried to add server '" + name + "' due to heartbeat auto-registration but it already exists");
				return;
			}
		}

		mLogger.info("Adding newly detected server name=" + name + " address=" + serverAddress + " to bungee's list of servers");

		ServerInfo serverInfo = new ServerInfo(name, addr);

		mServer.registerServer(serverInfo); // Deprecation note: This whole class is deprecated to discourage use, but no other options exist
	}

	@Subscribe(order = PostOrder.NORMAL)
	public void destOffline(DestOfflineEventGeneric event) {
		if (!mAutoUnregisterInactiveServersFromBungee) {
			return;
		}

		String name = event.getDest();
		@Nullable
		RegisteredServer server = mServer.getServer(name).orElse(null);
		@Nullable
		ServerInfo info = server == null ? null : server.getServerInfo();
		if (info == null || server == null) {
			return;
		}

		// should be fine but who cares
		mLogger.info("Removing offline server '" + name + "' from proxy's list of servers");

		for (Player p : server.getPlayersConnected()) {
			p.disconnect(Component.text("The server '" + name + "' you were connected to stopped or was otherwise disconnected from bungeecord", NamedTextColor.RED));
		}

		mServer.unregisterServer(info); // Deprecation note: This whole class is deprecated to discourage use, but no other options exist
	}

	// Copied from bungee
	public static InetSocketAddress getAddr(String hostline) {
		URI uri = null;
		try {
			uri = new URI(hostline);
		} catch (URISyntaxException ex) {
			// ignored
			uri = null;
		}

		/*
		if (uri != null && "unix".equals(uri.getScheme())) {
			return new DomainSocketAddress(uri.getPath());
		}
		*/

		if (uri == null || uri.getHost() == null) {
			try {
				uri = new URI("tcp://" + hostline);
			} catch (URISyntaxException ex) {
				throw new IllegalArgumentException("Bad hostline: " + hostline, ex);
			}
		}

		if (uri.getHost() == null) {
			throw new IllegalArgumentException("Invalid host/address: " + hostline);
		}

		return new InetSocketAddress(uri.getHost(), uri.getPort() == -1 ? 25565 : uri.getPort());
	}
}
