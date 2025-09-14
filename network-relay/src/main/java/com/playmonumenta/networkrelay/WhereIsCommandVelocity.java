package com.playmonumenta.networkrelay;

import com.velocitypowered.api.command.CommandSource;
import com.velocitypowered.api.command.SimpleCommand;
import com.velocitypowered.api.proxy.Player;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

public class WhereIsCommandVelocity implements SimpleCommand {
	@Override
	public void execute(final Invocation invocation) {
		CommandSource sender = invocation.source();
		// Get the arguments after the command alias
		String[] args = invocation.arguments();
		RemotePlayerData data = null;
		if (sender instanceof Player player) {
			boolean isSender = args.length == 0;
			if (isSender) {
				data = RemotePlayerAPI.getRemotePlayer(player.getUniqueId());
			} else if (args.length == 1) {
				String name = args[0];
				data = RemotePlayerAPI.getRemotePlayer(name);
			}
			if (data != null && !(!isSender && data.isHidden())) {
				sender.sendMessage(Component.text(data.friendlyString()));
			} else {
				sender.sendMessage(Component.text("No data found!", NamedTextColor.RED));
			}
		}
	}

	// This method allows you to control who can execute the command.
	// If the executor does not have the required permission,
	// the execution of the command and the control of its autocompletion
	// will be sent directly to the server on which the sender is located
	@Override
	public boolean hasPermission(final Invocation invocation) {
		return invocation.source().hasPermission("monumenta.networkrelay.whereis");
	}

	// Here you can offer argument suggestions in the same way as the previous method,
	// but asynchronously. It is recommended to use this method instead of the previous one
	// especially in cases where you make a more extensive logic to provide the suggestions
	@Override
	public CompletableFuture<List<String>> suggestAsync(final Invocation invocation) {
		return CompletableFuture.completedFuture(NetworkRelayAPI.getVisiblePlayerNames().stream().collect(Collectors.toList()));
	}
}
