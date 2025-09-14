package com.playmonumenta.networkrelay;

import dev.jorel.commandapi.CommandAPICommand;
import dev.jorel.commandapi.CommandPermission;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

public class ListShardsCommand {
	protected static void register() {
		CommandAPICommand innerCommand = new CommandAPICommand("listShards")
			.withPermission(CommandPermission.fromString("monumenta.networkrelay.listshards"))
			.executes((sender, args) -> {
				String senderProxy;
				if (sender instanceof Player player) {
					senderProxy = RemotePlayerAPI.getPlayerProxy(player.getUniqueId());
				} else {
					senderProxy = null;
				}

				for (String serverType : new TreeSet<>(NetworkRelayAPI.getOnlineDestinationTypes())) {
					String pluralServerType = switch (serverType) {
						case "minecraft" -> "shards";
						case "proxy" -> "proxies";
						default -> serverType + "s";
					};

					List<Component> shardComponents = new ArrayList<>();
					for (String serverName : new TreeSet<>(NetworkRelayAPI.getOnlineDestinationsOfType(serverType))) {
						if (senderProxy == null || !"minecraft".equals(serverType)) {
							shardComponents.add(Component.text(serverName));
						} else {
							Long pingMs = NetworkRelayAPI.getProxyToShardPingMs(senderProxy, serverName);
							Component pingComponent;
							if (pingMs == null) {
								pingComponent = Component.text("Timed Out (" + senderProxy + ")");
							} else {
								pingComponent = Component.text(pingMs + " ms (" + senderProxy + ")");
							}
							shardComponents.add(Component.text(serverName)
								.hoverEvent(pingComponent));
						}
					}

					sender.sendMessage(Component.text("Online " + pluralServerType + ": ", NamedTextColor.GOLD)
						.append(Component.join(JoinConfiguration.commas(true), shardComponents))
					);
				}
			});

		// Register first under the monumenta -> networkRelay namespace
		new CommandAPICommand("monumenta")
			.withSubcommand(new CommandAPICommand("networkRelay")
				.withSubcommand(innerCommand)
			).register();

		// Then directly, for convenience
		innerCommand.register();
	}
}
