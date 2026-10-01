package com.playmonumenta.redissync;

import com.playmonumenta.redissync.commands.ContentCommand;
import com.playmonumenta.redissync.commands.PlayerHistory;
import com.playmonumenta.redissync.commands.PlayerLoadFromPlayer;
import com.playmonumenta.redissync.commands.PlayerRollback;
import com.playmonumenta.redissync.commands.PlayerTransferHistory;
import com.playmonumenta.redissync.commands.RboardCommand;
import com.playmonumenta.redissync.commands.RemoteDataCommand;
import com.playmonumenta.redissync.commands.SetLocationOnShardCommand;
import com.playmonumenta.redissync.commands.Stash;
import com.playmonumenta.redissync.commands.TransferServer;
import com.playmonumenta.redissync.commands.UpgradeAllPlayers;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;

public class MonumentaRedisSyncBootstrap implements PluginBootstrap {
	@Override
	public void bootstrap(BootstrapContext context) {
		TransferServer.register();
		Stash.register();
		PlayerHistory.register();
		PlayerRollback.register();
		PlayerLoadFromPlayer.register();
		PlayerTransferHistory.register();
		UpgradeAllPlayers.register();
		RboardCommand.register();
		RemoteDataCommand.register();
		ContentCommand.register();
		SetLocationOnShardCommand.register();
	}

	@Override
	public JavaPlugin createPlugin(PluginProviderContext context) {
		return new MonumentaRedisSync();
	}
}
