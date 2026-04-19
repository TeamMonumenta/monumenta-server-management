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
import com.playmonumenta.structures.managers.EventListener;
import com.playmonumenta.structures.managers.RespawnManager;
import com.playmonumenta.structures.utils.CommandUtils;
import com.playmonumenta.structures.utils.MMLog;
import java.io.File;
import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitWorker;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class StructuresPlugin extends JavaPlugin implements Executor {
	public @Nullable RespawnManager mRespawnManager = null;

	private static @Nullable StructuresPlugin INSTANCE = null;

	@Override
	public void onLoad() {
		MMLog.init(getName());
		com.playmonumenta.common.MMLogPaper.registerCommand(MMLog.getLog());

		ActivateSpecialStructure.register(this);
		AddRespawningStructure.register(this);
		CompassRespawn.register();
		ForceConquerRespawn.register();
		ForceloadLazy.register();
		ListRespawningStructures.register();
		LoadStructure.register();
		ReloadStructures.register(this);
		RemoveRespawningStructure.register();
		RespawnStructure.register();
		SaveStructure.register();
		SetPostRespawnCommand.register();
		SetRespawnTimer.register();
		SetSpawnerBreakTrigger.register();
	}

	@Override
	public void onEnable() {
		INSTANCE = this;

		//TODO: Command to add an alternate generic structure

		PluginManager manager = getServer().getPluginManager();
		manager.registerEvents(new EventListener(), this);

		reloadConfig();
	}

	@Override
	public void onDisable() {
		// Persist per-shard runtime state (timers, spawner counts, etc.)
		saveState();

		// Cancel structure respawning and clear list
		if (mRespawnManager != null) {
			mRespawnManager.cleanup();
			mRespawnManager = null;
		}

		// Log any async threads that haven't finished yet
		for (BukkitWorker worker : Bukkit.getScheduler().getActiveWorkers()) {
			if (!worker.getOwner().equals(this)) {
				continue;
			}

			Thread thread = worker.getThread();
			StringBuilder builder = new StringBuilder("Unterminated thread stacktrace:");
			for (StackTraceElement traceElement : thread.getStackTrace()) {
				builder.append("\n\tat ").append(traceElement);
			}
			MMLog.severe(builder.toString());
		}
		// Cancel any remaining tasks
		getServer().getScheduler().cancelTasks(this);

		INSTANCE = null;
	}

	public File getConfigFile() {
		return new File(getDataFolder(), "config.yml");
	}

	public File getShardStateFile() {
		return new File(getDataFolder(), "shard_state.yml");
	}

	@Override
	public void reloadConfig() {
		if (mRespawnManager != null) {
			mRespawnManager.cleanup();
			mRespawnManager = null;
		}

		// loadConfiguration returns empty config if file doesn't exist
		YamlConfiguration config = YamlConfiguration.loadConfiguration(getConfigFile());
		YamlConfiguration shardState = YamlConfiguration.loadConfiguration(getShardStateFile());

		CommandUtils.reloadStructurePathSuggestions();

		/* TODO: Non-hardcoded worlds! These should be saved into the respawning structure */
		mRespawnManager = new RespawnManager(this, Bukkit.getWorlds().get(0), config, shardState);
	}

	/** Saves config.yml (structure definitions). Called when static config changes. */
	@Override
	public void saveConfig() {
		if (mRespawnManager != null) {
			File configFile = getConfigFile();
			try {
				mRespawnManager.getConfig().save(configFile);
			} catch (Exception ex) {
				MMLog.severe("Could not save config to " + configFile, ex);
			}
		}
	}

	/** Saves shard_state.yml (timers, spawner counts). Called at shutdown. */
	public void saveState() {
		if (mRespawnManager != null) {
			File shardStateFile = getShardStateFile();
			try {
				mRespawnManager.getShardState().save(shardStateFile);
			} catch (Exception ex) {
				MMLog.severe("Could not save shard state to " + shardStateFile, ex);
			}
		}
	}

	/** @deprecated Use {@link MMLog} static methods instead. */
	@Deprecated
	@Override
	public java.util.logging.Logger getLogger() {
		return super.getLogger();
	}

	public static StructuresPlugin getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("Attempted to get StructurePlugin before it finished loading");
		}
		return INSTANCE;
	}

	public static RespawnManager getRespawnManager() {
		StructuresPlugin plugin = getInstance();
		if (plugin.mRespawnManager == null) {
			throw new RuntimeException("Attempted to get StructurePlugin before it finished loading");
		}
		return plugin.mRespawnManager;
	}

	@Override
	public void execute(@NotNull Runnable command) {
		Bukkit.getScheduler().runTask(this, command);
	}
}
