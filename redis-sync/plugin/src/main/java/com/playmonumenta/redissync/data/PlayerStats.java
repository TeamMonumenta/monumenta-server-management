package com.playmonumenta.redissync.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class PlayerStats {
	private static final long PRESENT_MODULUS = 1L << 30;
	private final UUID mUuid;
	private final Map<String, Map<String, Long>> mTruth = new HashMap<>();
	private final Map<String, Map<String, Integer>> mPresented = new HashMap<>();

	private PlayerStats(UUID uuid) {
		mUuid = uuid;
	}

	public static @Nullable PlayerStats fromJson(UUID uuid, JsonObject root) {
		JsonObject stats = root.getAsJsonObject("stats");
		if (stats == null) {
			return null;
		}

		PlayerStats res = new PlayerStats(uuid);

		for (Map.Entry<String, JsonElement> namespaceEntry : stats.entrySet()) {
			if (!namespaceEntry.getValue().isJsonObject()) {
				continue;
			}

			Map<String, Long> truthNamespace = new HashMap<>();
			Map<String, Integer> presentedNamespace = new HashMap<>();
		
			for (Map.Entry<String, JsonElement> statEntry : namespaceEntry.getValue().getAsJsonObject().entrySet()) {
				long value = statEntry.getValue().getAsLong();
				int marker = present(value);

				truthNamespace.put(statEntry.getKey(), value);
				presentedNamespace.put(statEntry.getKey(),marker);
				statEntry.setValue(new JsonPrimitive(marker));
			}
			res.mTruth.put(namespaceEntry.getKey(), truthNamespace);
			res.mPresented.put(namespaceEntry.getKey(), presentedNamespace);
		} 
		return res;
	}

	public void updateSavedValue(JsonObject root) {
		JsonObject stats = root.getAsJsonObject("stats");
		if (stats == null) {
			stats = new JsonObject();
			root.add("stats", stats);
		}

		Map<String, Map<String, Integer>> nextPresented = copyPresented(mPresented);

		for (Map.Entry<String, JsonElement> namespaceEntry : stats.entrySet()) {
			if (!namespaceEntry.getValue().isJsonObject()) {
				continue;
			}

			String namespace = namespaceEntry.getKey();
			Map<String, Long> truthNamespace = mTruth.computeIfAbsent(namespace, k -> new HashMap<>());
			Map<String, Integer> presentedNamespace = mPresented.get(namespace);
			Map<String, Integer> nextPresentedNamespace = new HashMap<>();

			for (Map.Entry<String, JsonElement> statEntry : namespaceEntry.getValue().getAsJsonObject().entrySet()) {
				String stat = statEntry.getKey();
				long savedValue = statEntry.getValue().getAsLong();

				if (isTimeSince(namespace, stat)) {
					truthNamespace.put(stat, savedValue);
				} else {
					Integer baseline = presentedNamespace == null ? null : presentedNamespace.get(stat);

					long delta = baseline == null ? savedValue : savedValue - baseline;
					long curr = truthNamespace.getOrDefault(stat, 0L);

					truthNamespace.put(stat, curr + delta);
				}
				nextPresentedNamespace.put(stat, present(savedValue));
			}
			nextPresented.put(namespace, nextPresentedNamespace);
		}

		for (Map.Entry<String, Map<String, Long>> truthNamespaceEntry : mTruth.entrySet()) {
			JsonObject namespaceObj = stats.has(truthNamespaceEntry.getKey()) 
			? stats.getAsJsonObject(truthNamespaceEntry.getKey()) 
			: new JsonObject();

			for (Map.Entry<String, Long> truthStatEntry : truthNamespaceEntry.getValue().entrySet()) {
				namespaceObj.add(truthStatEntry.getKey(), new JsonPrimitive(truthStatEntry.getValue()));
			}

			stats.add(truthNamespaceEntry.getKey(), namespaceObj);
		}

		mPresented.clear();
		mPresented.putAll(nextPresented);
	}

	public UUID getUuid() {
		return mUuid;
	}

	private static int present(long truth) {
		return (int) Math.floorMod(truth, PRESENT_MODULUS);
	}	
	
	private static Map<String, Map<String, Integer>> copyPresented(Map<String, Map<String, Integer>> presented)  { 
		Map<String, Map<String, Integer>> out = new HashMap<>();
		
		if (presented == null) {
			return out;
		}
		for (Map.Entry<String, Map<String, Integer>> inner : presented.entrySet()) {
			out.put(inner.getKey(), new HashMap<>(inner.getValue()));
		}

		return out;
	} 

	private static boolean isTimeSince(String namespace, String stat) {
		return namespace.equals("minecraft:custom") 
		&& stat.startsWith("minecraft:time_since");
	}
}
