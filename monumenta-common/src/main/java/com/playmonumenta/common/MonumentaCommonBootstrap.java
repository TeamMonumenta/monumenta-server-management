package com.playmonumenta.common;

import com.playmonumenta.common.commands.GetDateCommand;
import com.playmonumenta.common.commands.RefreshTimeCommand;
import com.playmonumenta.common.commands.TimeWarpCommand;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class MonumentaCommonBootstrap implements PluginBootstrap {
	@Override
	public void bootstrap(@NotNull BootstrapContext context) {
		TimeWarpCommand.register();
		GetDateCommand.register();
		RefreshTimeCommand.register();
	}

	@Override
	public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
		return new MonumentaCommonPlugin();
	}
}
