package com.playmonumenta.networkrelay;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.net.SocketAddress;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Logger;
import net.md_5.bungee.Util;
import net.md_5.bungee.api.ProxyServer;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.config.ServerInfo;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.event.EventPriority;

public class NetworkMessageListenerBungee implements Listener {
	private static final List<NetworkRelayAPI.ServerType> ACCEPTED_SERVER_TYPES = Arrays.asList(
		NetworkRelayAPI.ServerType.ALL,
		NetworkRelayAPI.ServerType.PROXY
	);

	private final Logger mLogger;
	private final boolean mRunReceivedCommands;
	private final boolean mAutoRegisterServersToBungee;
	private final boolean mAutoUnregisterInactiveServersFromBungee;

	protected NetworkMessageListenerBungee(Logger logger, boolean runReceivedCommands, boolean autoRegisterServersToBungee, boolean autoUnregisterInactiveServersFromBungee) {
		mLogger = logger;
		mRunReceivedCommands = runReceivedCommands;
		mAutoRegisterServersToBungee = autoRegisterServersToBungee;
		mAutoUnregisterInactiveServersFromBungee = autoUnregisterInactiveServersFromBungee;
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void gatherHeartbeatData(GatherHeartbeatDataEventBungee event) {
		JsonObject data = new JsonObject();
		data.addProperty("server-type", "proxy");
		event.setPluginData(NetworkRelayAPI.NETWORK_RELAY_HEARTBEAT_IDENTIFIER, data);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void networkRelayMessageEvent(NetworkRelayMessageEventBungee event) {
		if (!mRunReceivedCommands) {
			return;
		}

		if (!event.getChannel().equals(NetworkRelayAPI.COMMAND_CHANNEL)) {
			return;
		}

		JsonObject data = event.getData();
		if (!data.has("command") ||
			!data.get("command").isJsonPrimitive() ||
			!data.getAsJsonPrimitive("command").isString()) {
			mLogger.warning("Got invalid command message with no actual command");
			return;
		}

		boolean warnLegacyServerType = false;
		JsonPrimitive serverTypeJson = data.getAsJsonPrimitive("server-type");
		if (serverTypeJson != null) {
			String serverTypeString = serverTypeJson.getAsString();
			if (serverTypeString != null) {
				if (serverTypeString.equals("bungee")) {
					warnLegacyServerType = true;
				}
				NetworkRelayAPI.ServerType commandType
					= NetworkRelayAPI.ServerType.fromString(serverTypeString);
				if (!ACCEPTED_SERVER_TYPES.contains(commandType)) {
					return;
				}
			}
		}

		final String command = data.get("command").getAsString();
		if (warnLegacyServerType) {
			mLogger.warning("Executing command'" + command + "' from source '" + event.getSource() + "'; legacy server type 'bungee' was requested");
		} else {
			mLogger.fine("Executing command'" + command + "' from source '" + event.getSource() + "'");
		}

		ProxyServer.getInstance().getPluginManager().dispatchCommand(ProxyServer.getInstance().getConsole(), data.get("command").getAsString());
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void destOnline(DestOnlineEventBungee event) {
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
			mLogger.warning("auto-register-servers-to-bungee=true but shard '" + name + "' is misconfigured and didn't send a server address");
			return;
		}

		SocketAddress addr;
		try {
			addr = Util.getAddr(serverAddress);
		} catch (Exception ex) {
			addr = null;
		}
		if (addr == null) {
			mLogger.warning("Tried to add server '" + name + "' but address '" + serverAddress + "' is invalid");
			return;
		}

		// move duplicate server check after to ensure that addresses are the same
		ServerInfo info = ProxyServer.getInstance().getServerInfo(name);
		if (info != null) {
			SocketAddress previousAddress = info.getSocketAddress();
			// check if addresses are different
			if (previousAddress != null && !previousAddress.equals(addr)) {
				// if addresses are different, remove the old address
				mLogger.warning("Server: '" + name + "' has different address now (Old: '" + previousAddress + "') (New: '" + addr + "')");
				mLogger.warning("Removing old server with different address...");
				ProxyServer.getInstance().getConfig().removeServerNamed(name); // Deprecation note: This whole class is deprecated to discourage use, but no other options exist
			} else {
				// otherwise complain and ignore
				mLogger.info("Tried to add server '" + name + "' due to heartbeat auto-registration but it already exists");
				return;
			}
		}

		mLogger.info("Adding newly detected server name=" + name + " address=" + serverAddress + " to bungee's list of servers");

		ServerInfo serverInfo = ProxyServer.getInstance().constructServerInfo(name, addr, "", false);

		ProxyServer.getInstance().getConfig().addServer(serverInfo); // Deprecation note: This whole class is deprecated to discourage use, but no other options exist
	}

	@EventHandler(priority = EventPriority.NORMAL)
	public void destOffline(DestOfflineEventBungee event) {
		if (!mAutoUnregisterInactiveServersFromBungee) {
			return;
		}

		String name = event.getDest();
		ServerInfo info = ProxyServer.getInstance().getServerInfo(name);
		if (info == null) {
			return;
		}

		mLogger.fine("Removing offline server '" + name + "' from bungee's list of servers");

		for (ProxiedPlayer p : info.getPlayers()) {
			p.disconnect(new TextComponent("The server '" + name + "' you were connected to stopped or was otherwise disconnected from bungeecord"));
		}

		ProxyServer.getInstance().getConfig().removeServerNamed(name); // Deprecation note: This whole class is deprecated to discourage use, but no other options exist
	}
}
