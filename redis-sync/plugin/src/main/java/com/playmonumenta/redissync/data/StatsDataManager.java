package com.playmonumenta.redissync.data;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.jetbrains.annotations.Nullable;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.redissync.utils.MMLog;

public class StatsDataManager {
	private static final Map<UUID, Map<String, Map<String, Long>>> mTruth = new HashMap<>();
	private static final Map<UUID, Map<String, Map<String, Integer>>> mPresented = new HashMap<>();
	private static final long PRESENT_MODULUS = 1L << 30; // around 1.073b
	
	private StatsDataManager() {

	}

	public static @Nullable String load(UUID uuid, @Nullable String storedJson) {
		if (storedJson == null || storedJson.isBlank()) {
			return null;
		}

		// Try to parse storedJson. If operation fails, log it
		JsonObject root;
		try {
			root = JsonParser.parseString(storedJson).getAsJsonObject();
		} catch (Exception e){
			MMLog.warning("Could not parse stats for " + uuid, e);
			return null;
		}
		
		JsonObject stats = root.getAsJsonObject("stats");
		if (stats == null) {
			return null;
		}

		Map<String, Map<String, Long>> truth = new HashMap<>();
		Map<String, Map<String, Integer>> presented = new HashMap<>();
		
		// Iterate through every namespace entry in our object
		for (Map.Entry<String, JsonElement> namespaceEntry : stats.entrySet()) {
			if (!namespaceEntry.getValue().isJsonObject()) {
				continue;
			}

			Map<String, Long> truthNamespace = new HashMap<>();
			Map<String, Integer> presentedNamespace = new HashMap<>();
			
			// For every namespace stat, get their real value and generate a marker value
			for (Map.Entry<String, JsonElement> statEntry : namespaceEntry.getValue().getAsJsonObject().entrySet()) {
				long value = statEntry.getValue().getAsLong();
				int marker = (int) present(value);

				truthNamespace.put(statEntry.getKey(), value);
				presentedNamespace.put(statEntry.getKey(), marker);

				statEntry.setValue(new JsonPrimitive(marker));
			}

			truth.put(namespaceEntry.getKey(), truthNamespace);
			presented.put(namespaceEntry.getKey(), presentedNamespace);
		}
		
		mTruth.put(uuid, truth);
		mPresented.put(uuid, presented);

		return root.toString();
	}

	public static String save(UUID uuid, String bukkitJson) {
		Map<String, Map<String, Long>> truth = mTruth.get(uuid);

		// Return bukkit json if there's no entry recorded
		if (truth == null) {
			return bukkitJson;
		}

		// Parse bukkitJson into an object
		JsonObject root;
		try {
			root = JsonParser.parseString(bukkitJson).getAsJsonObject();
		} catch (Exception e) {
			MMLog.warning("Could not save player stats for " + uuid, e);
			return bukkitJson;
		}

		JsonObject stats = root.getAsJsonObject("stats");
		if (stats == null) {
			stats = new JsonObject();
			root.add("stats", stats);
		}

		Map<String, Map<String, Integer>> presented = mPresented.get(uuid);
		Map<String, Map<String, Integer>> nextPresented = new HashMap<>();

		for (Map.Entry<String, JsonElement> namespaceEntry : stats.entrySet()) {
			if (!namespaceEntry.getValue().isJsonObject()) {
				continue;
			}

			String namespace = namespaceEntry.getKey();
			Map<String, Long> truthNamespace = truth.computeIfAbsent(namespace, key -> new HashMap<>());
			Map<String, Integer> presentedNamespace = presented == null ? null : presented.get(namespace);
			Map<String, Integer> nextPresentedNamespace = new HashMap<>();

			for (Map.Entry<String, JsonElement> statEntry : namespaceEntry.getValue().getAsJsonObject().entrySet()) {
				String stat = statEntry.getKey();
				long bukkitValue = statEntry.getValue().getAsLong();

				if (isTimeSince(namespace, stat)) {
					truthNamespace.put(stat, bukkitValue);
				} else {
					Integer baseline = presentedNamespace == null ? null : presentedNamespace.get(stat);

					long delta = baseline == null ? bukkitValue : bukkitValue - baseline;
					long curr = truthNamespace.getOrDefault(stat, 0L);

					truthNamespace.put(stat, curr + delta);
				}

				nextPresentedNamespace.put(stat, (int) bukkitValue);
			}

			nextPresented.put(namespace, nextPresentedNamespace);
		}

		for (Map.Entry<String, Map<String, Long>> truthNamespaceEntry : truth.entrySet()) {
			JsonObject namespaceObj = stats.has(truthNamespaceEntry.getKey()) 
			? stats.getAsJsonObject(truthNamespaceEntry.getKey()) 
			: new JsonObject();

			for (Map.Entry<String, Long> truthStatEntry : truthNamespaceEntry.getValue().entrySet()) {
				namespaceObj.add(truthStatEntry.getKey(), new JsonPrimitive(truthStatEntry.getValue()));
			}

			stats.add(truthNamespaceEntry.getKey(), namespaceObj);
		}

		mPresented.put(uuid, nextPresented);

		return root.toString();
	}

	public static void remove(UUID uuid) {
		mTruth.remove(uuid);
		mPresented.remove(uuid);
	}

	private static long present(long truth) {
		return Math.floorMod(truth, PRESENT_MODULUS);
	}

	private static boolean isTimeSince(String namespace, String stat) {
		return namespace.equals("minecraft:custom") 
		&& stat.startsWith("minecraft:time_since");
	}
}
