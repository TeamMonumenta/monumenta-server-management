package com.playmonumenta.networkrelay;

import com.playmonumenta.networkrelay.commands.BroadcastCommand;
import com.playmonumenta.networkrelay.commands.DebugHeartbeatCommand;
import com.playmonumenta.networkrelay.commands.ListShardsCommand;
import com.playmonumenta.networkrelay.commands.RemotePlayerAPICommand;
import com.playmonumenta.networkrelay.commands.SendCommand;
import com.playmonumenta.networkrelay.commands.WhereIsCommand;
import com.playmonumenta.networkrelay.shardhealth.ShardHealthManager;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class NetworkRelayBootstrap implements PluginBootstrap {
	private BroadcastCommand mBroadcastCommand = null;

	@Override
	public void bootstrap(@NotNull BootstrapContext context) {
		mBroadcastCommand = new BroadcastCommand();
		new SendCommand();
		DebugHeartbeatCommand.registerCommands();
		ListShardsCommand.register();
		RemotePlayerAPICommand.register();
		WhereIsCommand.register();
		ShardHealthManager.init();
	}

	@Override
	public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
		return new NetworkRelay(mBroadcastCommand);
	}
}
