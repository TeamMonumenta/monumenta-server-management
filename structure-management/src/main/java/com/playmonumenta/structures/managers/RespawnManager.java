package com.playmonumenta.structures.managers;

import com.playmonumenta.common.zones.Zone;
import com.playmonumenta.common.zones.ZoneFragment;
import com.playmonumenta.common.zones.ZoneManager;
import com.playmonumenta.common.zones.ZoneNamespace;
import com.playmonumenta.structures.StructuresPlugin;
import com.playmonumenta.structures.utils.MMLog;
import com.playmonumenta.structures.utils.MessagingUtils;
import dev.jorel.commandapi.arguments.ArgumentSuggestions;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.SortedMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.stream.Collectors;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import org.jetbrains.annotations.Nullable;

public class RespawnManager {
	public static final String ZONE_NAMESPACE_INSIDE = "Respawning Structures Inside";
	public static final String ZONE_NAMESPACE_NEARBY = "Respawning Structures Nearby";

	private static @Nullable RespawnManager INSTANCE = null;
	public static ArgumentSuggestions<CommandSender> SUGGESTIONS_STRUCTURES = ArgumentSuggestions.strings((info) -> {
		if (INSTANCE == null) {
			return new String[]{};
		}
		return INSTANCE.listStructures();
	});

	private final StructuresPlugin mPlugin;
	private final World mWorld;

	private final SortedMap<String, RespawningStructure> mRespawns = new ConcurrentSkipListMap<>();
	private final int mTickPeriod;
	private final BukkitRunnable mRunnable = new BukkitRunnable() {
		@Override
		public void run() {
			for (RespawningStructure struct : mRespawns.values()) {
				struct.tick(mTickPeriod);
			}
		}
	};
	private boolean mTaskScheduled = false;
	private boolean mStructuresLoaded = false;
	protected ZoneNamespace mZoneNamespaceInside;
	protected ZoneNamespace mZoneNamespaceNearby;
	private final Map<Zone, RespawningStructure> mStructuresByZone = new LinkedHashMap<>();

	public RespawnManager(StructuresPlugin plugin, World world, YamlConfiguration config, YamlConfiguration shardState) {
		INSTANCE = this;
		mPlugin = plugin;
		mWorld = world;

		mZoneNamespaceInside = new ZoneNamespace(ZONE_NAMESPACE_INSIDE);
		mZoneNamespaceNearby = new ZoneNamespace(ZONE_NAMESPACE_NEARBY, true);
		mStructuresByZone.clear();

		// Load the frequency that the plugin should check for respawning structures
		if (!config.isInt("check_respawn_period")) {
			MMLog.warning("No check_respawn_period setting specified - using default 20");
			mTickPeriod = 20;
		} else {
			mTickPeriod = config.getInt("check_respawn_period");
		}

		// Load the respawning structures configuration section
		if (!config.isConfigurationSection("respawning_structures")) {
			MMLog.info("No respawning structures defined");
			mStructuresLoaded = true;
			return;
		}

		// Load the structures asynchronously so this doesn't hold up the start of the server
		ConfigurationSection respawnSection = config.getConfigurationSection("respawning_structures");
		@Nullable ConfigurationSection shardStateSection = shardState.getConfigurationSection("respawning_structures");

		Set<String> keys;
		if (respawnSection == null) {
			keys = new HashSet<>();
		} else {
			keys = respawnSection.getKeys(false);
		}

		final List<CompletableFuture<?>> futures = new ArrayList<>();

		// Iterate over all the respawning entries (shallow list at this level)
		for (String key : keys) {
			if (!respawnSection.isConfigurationSection(key)) {
				MMLog.warning("respawning_structures entry '" + key + "' is not a configuration " +
					"section!");
				continue;
			}

			@Nullable ConfigurationSection shardSection = shardStateSection != null
				? shardStateSection.getConfigurationSection(key) : null;

			futures.add(
				RespawningStructure.fromConfig(mPlugin, mWorld, key, respawnSection.getConfigurationSection(key), shardSection)
					.whenComplete((structure, ex) -> {
						if (ex != null) {
							MMLog.warning("Failed to load respawning structure entry '" + key + "'", ex);
							MessagingUtils.sendStackTrace(Bukkit.getConsoleSender(), ex);
						} else {
							mRespawns.put(key, structure);
							MMLog.info("Successfully loaded respawning structure '" + key + "': ");
						}
					})
			);
		}

		CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).whenCompleteAsync((unused, throwable) -> {
			// Enable the plugin zone namespaces that have been populated (but not registered) during the reload
			reloadZones().whenCompleteAsync((unused2, throwable2) -> {
				mRunnable.runTaskTimer(mPlugin, 0, mTickPeriod);

				mTaskScheduled = true;
				mStructuresLoaded = true;
			}, StructuresPlugin.getInstance());
		}, StructuresPlugin.getInstance());
	}

	public static RespawnManager getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("Attempted to get RespawnManager before it loaded");
		}
		return INSTANCE;
	}

	public CompletableFuture<Void> addStructure(int extraRadius, String configLabel, String name, String path,
												Vector loadPos, int respawnTime) {

		return RespawningStructure.withParameters(
			mPlugin, mWorld, extraRadius, configLabel,
			name, Collections.singletonList(path), loadPos,
			respawnTime, respawnTime, null, null,
			null, null
		).thenApply((structure) -> {
			mRespawns.put(configLabel, structure);
			mPlugin.saveConfig();
			reloadZones();
			return null;
		});
	}

	public void removeStructure(String configLabel) throws Exception {
		if (!mRespawns.containsKey(configLabel)) {
			throw new Exception("Structure '" + configLabel + "' does not exist");
		}

		mRespawns.remove(configLabel);
		mPlugin.saveConfig();

		mZoneNamespaceInside = new ZoneNamespace(ZONE_NAMESPACE_INSIDE);
		mZoneNamespaceNearby = new ZoneNamespace(ZONE_NAMESPACE_NEARBY, true);
		mStructuresByZone.clear();

		for (RespawningStructure struct : mRespawns.values()) {
			struct.registerZone();
		}

		reloadZones();
	}

	public List<RespawningStructure> getStructures(Vector loc, boolean includeNearby) {
		ZoneFragment zoneFragment = ZoneManager.getInstance().getZoneFragment(loc);
		if (zoneFragment == null) {
			return new ArrayList<>();
		}
		String namespace = includeNearby ? ZONE_NAMESPACE_NEARBY : ZONE_NAMESPACE_INSIDE;
		return zoneFragment.getParentAndEclipsed(namespace)
			.stream()
			.map(mStructuresByZone::get)
			.filter(Objects::nonNull)
			.collect(Collectors.toCollection(ArrayList::new));
	}

	/* Human-readable */
	public void listStructures(CommandSender sender) {
		if (mRespawns.isEmpty()) {
			sender.sendMessage(Component.text("No respawning structures registered"));
			return;
		}

		sender.sendMessage(Component.text("Respawning Structure List", NamedTextColor.GOLD, TextDecoration.BOLD));
		StringBuilder structuresString = new StringBuilder();
		for (String key : mRespawns.keySet()) {
			structuresString.append(key).append("  ");
		}
		sender.sendMessage(Component.text(structuresString.toString(), NamedTextColor.GREEN));
	}

	/* Machine-readable list */
	public String[] listStructures() {
		return mRespawns.keySet().toArray(new String[]{});
	}

	public void structureInfo(CommandSender sender, String label) throws Exception {
		sender.sendMessage(Component.empty()
			.append(Component.text(label + " : ", NamedTextColor.GREEN))
			.append(Component.text(getStructure(label).getInfoString()))
		);
	}

	public void setTimer(String label, int ticksUntilRespawn) throws Exception {
		getStructure(label).setRespawnTimer(ticksUntilRespawn);
	}

	public void setTimerPeriod(String label, int ticksUntilRespawn) throws Exception {
		getStructure(label).setRespawnTimerPeriod(ticksUntilRespawn);
	}

	public void setPostRespawnCommand(String label, @Nullable String command) throws Exception {
		getStructure(label).setPostRespawnCommand(command);
		mPlugin.saveConfig();
	}

	public void setSpawnerBreakTrigger(String label, @Nullable SpawnerBreakTrigger trigger) throws Exception {
		getStructure(label).setSpawnerBreakTrigger(trigger);
		mPlugin.saveConfig();
	}

	public void activateSpecialStructure(String label, @Nullable String nextRespawnPath) throws Exception {
		getStructure(label).activateSpecialStructure(nextRespawnPath);
	}

	public void tellNearbyRespawnTimes(Player player) {
		boolean nearbyStruct = false;
		for (RespawningStructure struct : mRespawns.values()) {
			if (struct.isNearby(player)) {
				struct.tellRespawnTime(player);
				nearbyStruct = true;
			}
		}

		if (!nearbyStruct) {
			player.sendMessage(Component.text("You are not within range of a respawning area", NamedTextColor.RED,
				TextDecoration.BOLD));
		}
	}

	public void compassRespawn(Player player, String label) throws Exception {
		getStructure(label).forcedRespawn(player, false);
	}

	public void forceConquerRespawn(String label) throws Exception {
		getStructure(label).conquerStructure();
	}

	public void cleanup() {
		if (mTaskScheduled) {
			mRunnable.cancel();
			mTaskScheduled = false;
		}
		mRespawns.clear();
	}

	public YamlConfiguration getConfig() throws Exception {
		if (!mStructuresLoaded) {
			throw new Exception("Structures haven't finished loading yet!");
		}

		YamlConfiguration config = new YamlConfiguration();
		config.set("check_respawn_period", mTickPeriod);
		ConfigurationSection respawnConfig = config.createSection("respawning_structures");
		for (Map.Entry<String, RespawningStructure> entry : mRespawns.entrySet()) {
			respawnConfig.createSection(entry.getKey(), entry.getValue().getConfig());
		}
		return config;
	}

	public YamlConfiguration getShardState() throws Exception {
		if (!mStructuresLoaded) {
			throw new Exception("Structures haven't finished loading yet!");
		}

		YamlConfiguration state = new YamlConfiguration();
		ConfigurationSection structures = state.createSection("respawning_structures");
		for (Map.Entry<String, RespawningStructure> entry : mRespawns.entrySet()) {
			structures.createSection(entry.getKey(), entry.getValue().getShardState());
		}
		return state;
	}

	public void spawnerBreakEvent(Location loc) {
		for (RespawningStructure struct : mRespawns.values()) {
			struct.spawnerBreakEvent(loc);
		}
	}

	protected void registerRespawningStructureZone(Zone zone, RespawningStructure structure) {
		mStructuresByZone.put(zone, structure);
	}

	private RespawningStructure getStructure(String label) throws Exception {
		RespawningStructure struct = mRespawns.get(label);
		if (struct == null) {
			throw new Exception("Structure '" + label + "' not found!");
		}
		return struct;
	}

	private CompletableFuture<Void> reloadZones() {
		ZoneManager zoneManager = ZoneManager.getInstance();
		Set<String> loadedNamespaces = zoneManager.getNamespaceNames();

		Set<ZoneNamespace> newNamespaces = new HashSet<>();
		Set<ZoneNamespace> replacedNamespaces = new HashSet<>();

		if (loadedNamespaces.contains(ZONE_NAMESPACE_INSIDE)) {
			replacedNamespaces.add(mZoneNamespaceInside);
		} else {
			newNamespaces.add(mZoneNamespaceInside);
		}

		if (loadedNamespaces.contains(ZONE_NAMESPACE_NEARBY)) {
			replacedNamespaces.add(mZoneNamespaceNearby);
		} else {
			newNamespaces.add(mZoneNamespaceNearby);
		}

		return zoneManager.bulkPluginZoneNamespaceChanges(newNamespaces, replacedNamespaces, Set.of());
	}
}
