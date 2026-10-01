package com.playmonumenta.networkchat;

import com.playmonumenta.networkchat.commands.ChatCommand;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;

public class NetworkChatBootstrap implements PluginBootstrap {
	@Override
	public void bootstrap(BootstrapContext context) {
		ChatCommand.register();
	}

	@Override
	public JavaPlugin createPlugin(PluginProviderContext context) {
		return new NetworkChatPlugin();
	}
}
