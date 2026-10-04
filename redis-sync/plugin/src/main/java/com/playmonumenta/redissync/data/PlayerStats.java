package com.playmonumenta.redissync.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;
/*
 * 64-bit stats for a loaded player + fake 32-bit values to hand off to MC.
 *
 * MC and it's network protocol only support 32-bit stats. Our approach is to
 * hand a truncated value to anything that requests it, then save the value later
 * as a 64-bit value by calculating the difference and adding to our truthy value.
 */
public class PlayerStats {
	private final UUID mUuid;
	/* Map[namespace][stat] -> i64 */
	private final Map<String, Map<String, Long>> mTruth = new HashMap<>();
	/* Map[namespace][stat] -> i32 */
	private final Map<String, Map<String, Integer>> mPresented = new HashMap<>();
	/* Used to keep our presentation values in the range [0..2^30) */
	private static final long PRESENT_MODULUS = 1L << 30;

	private PlayerStats(UUID uuid) {
		mUuid = uuid;
	}

	/*
	 * Build the truthy and presentable values off a player's JSON file.
	 * Rewrites the JSON in place using presentable values to hand it
	 * off to the server.
	 */
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
				presentedNamespace.put(statEntry.getKey(), marker);
				statEntry.setValue(new JsonPrimitive(marker));
			}
			res.mTruth.put(namespaceEntry.getKey(), truthNamespace);
			res.mPresented.put(namespaceEntry.getKey(), presentedNamespace);
		}
		return res;
	}

	/*
	 * Updates the saved truthy and presentable values.
	 * For each stat, we compute the difference between the original
	 * presented value and what we've received, then add this difference
	 * to our truthy value.
	 */
	public void updateSavedValue(JsonObject root) {
		JsonObject stats = root.getAsJsonObject("stats");
		if (stats == null) {
			stats = new JsonObject();
			root.add("stats", stats);
		}

		Map<String, Map<String, Long>> nextTruth = copyTruth(mTruth);
		Map<String, Map<String, Integer>> nextPresented = copyPresented(mPresented);

		for (Map.Entry<String, JsonElement> namespaceEntry : stats.entrySet()) {
			// Skip anything that isn't a stat block
			if (!namespaceEntry.getValue().isJsonObject()) {
				continue;
			}

			String namespace = namespaceEntry.getKey();
			Map<String, Long> truthNamespace = nextTruth.computeIfAbsent(namespace, k -> new HashMap<>());
			Map<String, Integer> presentedNamespace = mPresented.getOrDefault(namespace, new HashMap<>());
			Map<String, Integer> nextPresentedNamespace = new HashMap<>();

			for (Map.Entry<String, JsonElement> statEntry : namespaceEntry.getValue().getAsJsonObject().entrySet()) {
				String stat = statEntry.getKey();
				long savedValue = statEntry.getValue().getAsLong();

				if (isTimeSince(namespace, stat)) {
					truthNamespace.put(stat, savedValue);
				} else {
					Integer baseline = presentedNamespace.get(stat);
					// Null baseline -> we haven't presented it before
					long delta = baseline == null
					? savedValue
					: savedValue - baseline;

					long curr = truthNamespace.getOrDefault(stat, 0L);

					truthNamespace.put(stat, curr + delta);
				}
				nextPresentedNamespace.put(stat, present(savedValue));
			}
			nextPresented.put(namespace, nextPresentedNamespace);
		}

		for (Map.Entry<String, Map<String, Long>> truthNamespaceEntry : nextTruth.entrySet()) {
			JsonObject namespaceObj = stats.has(truthNamespaceEntry.getKey())
			? stats.getAsJsonObject(truthNamespaceEntry.getKey())
			: new JsonObject();

			for (Map.Entry<String, Long> truthStatEntry : truthNamespaceEntry.getValue().entrySet()) {
				namespaceObj.add(truthStatEntry.getKey(), new JsonPrimitive(truthStatEntry.getValue()));
			}

			stats.add(truthNamespaceEntry.getKey(), namespaceObj);
		}

		mTruth.clear();
		mTruth.putAll(nextTruth);
		mPresented.clear();
		mPresented.putAll(nextPresented);
	}

	public UUID getUuid() {
		return mUuid;
	}

	public long getStat(String namespace, String stat) {
		return mTruth
		.getOrDefault(namespace, Map.of())
		.getOrDefault(stat, 0L);
	}

	public Set<String> getNamespaces() {
		return mTruth.keySet();
	}

	public Set<String> getStatKeys(String namespace) {
		Map<String, Long> namespaceEntry = mTruth.get(namespace);
		return namespaceEntry == null
		? Set.of()
		: namespaceEntry.keySet();
	}



	private static int present(long truth) {
		return (int) Math.floorMod(truth, PRESENT_MODULUS);
	}

	private static Map<String, Map<String, Long>> copyTruth(Map<String, Map<String, Long>> truth)  {
		Map<String, Map<String, Long>> out = new HashMap<>();

		if (truth == null) {
			return out;
		}

		for (Map.Entry<String, Map<String, Long>> inner : truth.entrySet()) {
			out.put(inner.getKey(), new HashMap<>(inner.getValue()));
		}

		return out;
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
		// time_since_death, etc; these stats aren't cumulative
		return namespace.equals("minecraft:custom")
		&& stat.startsWith("minecraft:time_since");
	}
}
