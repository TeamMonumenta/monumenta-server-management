package com.playmonumenta.structures;

import com.playmonumenta.structures.commands.ActivateSpecialStructure;
import com.playmonumenta.structures.commands.AddRespawningStructure;
import com.playmonumenta.structures.commands.CompassRespawn;
import com.playmonumenta.structures.commands.ForceConquerRespawn;
import com.playmonumenta.structures.commands.ForceloadLazy;
import com.playmonumenta.structures.commands.ListRespawningStructures;
import com.playmonumenta.structures.commands.LoadStructure;
import com.playmonumenta.structures.commands.ReloadStructures;
import com.playmonumenta.structures.commands.RemoveRespawningStructure;
import com.playmonumenta.structures.commands.RespawnStructure;
import com.playmonumenta.structures.commands.SaveStructure;
import com.playmonumenta.structures.commands.SetPostRespawnCommand;
import com.playmonumenta.structures.commands.SetRespawnTimer;
import com.playmonumenta.structures.commands.SetSpawnerBreakTrigger;
import io.papermc.paper.plugin.bootstrap.BootstrapContext;
import io.papermc.paper.plugin.bootstrap.PluginBootstrap;
import io.papermc.paper.plugin.bootstrap.PluginProviderContext;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

public class StructuresBootstrap implements PluginBootstrap {
	@Override
	public void bootstrap(@NotNull BootstrapContext context) {
		ActivateSpecialStructure.register();
		AddRespawningStructure.register();
		CompassRespawn.register();
		ForceConquerRespawn.register();
		ForceloadLazy.register();
		ListRespawningStructures.register();
		LoadStructure.register();
		ReloadStructures.register();
		RemoveRespawningStructure.register();
		RespawnStructure.register();
		SaveStructure.register();
		SetPostRespawnCommand.register();
		SetRespawnTimer.register();
		SetSpawnerBreakTrigger.register();
	}

	@Override
	public @NotNull JavaPlugin createPlugin(@NotNull PluginProviderContext context) {
		return new StructuresPlugin();
	}
}
