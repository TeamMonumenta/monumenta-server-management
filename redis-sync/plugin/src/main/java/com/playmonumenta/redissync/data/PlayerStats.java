package com.playmonumenta.redissync.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.util.HashMap;
import java.util.Map;

/**
 * A player's stats as the 64-bit values saved in redis, and the 32-bit values handed to the server
 * in their place.
 *
 * <p>The server and its protocol hold stats as ints, which stop counting at Integer.MAX_VALUE. So a
 * load hands the server each stat wrapped into [0, 2^30), leaving at least 2^30 to count up from,
 * and remembers what it handed over as the baseline. Each save adds what the server has counted
 * since (its value minus the baseline) to the 64-bit value, and the server's value becomes the next
 * baseline. time_since stats are not counts, so they are handed over as they are (clamped) and
 * saved as the server has them.
 */
public final class PlayerStats {
	private static final long PRESENT_MODULUS = 1L << 30;

	/* namespace -> stat -> 64-bit value */
	private Map<String, Map<String, Long>> mTruth = new HashMap<>();
	/* namespace -> stat -> what the server holds, as of the last load or save */
	private Map<String, Map<String, Integer>> mBaseline = new HashMap<>();

	/** Stats for a player with none saved: whatever the server has is taken as it is */
	public PlayerStats() {
	}

	/**
	 * Takes the 64-bit values from saved stats JSON, and rewrites {@code root} in place to the
	 * values to hand the server, for a player whose stats will be saved back through {@link #save}.
	 */
	public static PlayerStats load(JsonObject root) {
		PlayerStats result = new PlayerStats();
		forEachStat(root, (namespace, stat, entry) -> {
			long value = entry.getValue().getAsLong();
			int presented = isTimeSince(namespace, stat) ? clamp(value) : (int) Math.floorMod(value, PRESENT_MODULUS);
			result.mTruth.computeIfAbsent(namespace, k -> new HashMap<>()).put(stat, value);
			result.mBaseline.computeIfAbsent(namespace, k -> new HashMap<>()).put(stat, presented);
			entry.setValue(new JsonPrimitive(presented));
		});
		return result;
	}

	/**
	 * Rewrites saved stats JSON in place to values the server can hold, for a load that is never
	 * saved back, such as another plugin reading an offline player's stats. Each is clamped rather
	 * than wrapped, so a reader sees the nearest value to the real one.
	 */
	public static void clampForReading(JsonObject root) {
		forEachStat(root, (namespace, stat, entry) -> entry.setValue(new JsonPrimitive(clamp(entry.getValue().getAsLong()))));
	}

	/**
	 * Rewrites the server's stats JSON in place to the 64-bit values to save, and takes the server's
	 * values as the baseline for the next save. Stats the server no longer reports are kept.
	 */
	public void save(JsonObject root) {
		Map<String, Map<String, Long>> truth = deepCopy(mTruth);
		Map<String, Map<String, Integer>> baseline = deepCopy(mBaseline);
		Map<String, Map<String, Integer>> held = new HashMap<>();
		forEachStat(root, (namespace, stat, entry) -> {
			long value = entry.getValue().getAsLong();
			Map<String, Long> namespaceTruth = truth.computeIfAbsent(namespace, k -> new HashMap<>());
			if (isTimeSince(namespace, stat)) {
				namespaceTruth.put(stat, value);
			} else {
				/* No baseline: the server started counting this stat from 0 */
				long counted = value - baseline.getOrDefault(namespace, Map.of()).getOrDefault(stat, 0);
				namespaceTruth.merge(stat, counted, Long::sum);
			}
			/* The server keeps counting up from what it has, not from a re-wrapped value, until the player loads again */
			held.computeIfAbsent(namespace, k -> new HashMap<>()).put(stat, (int) value);
		});
		baseline.putAll(held);

		JsonElement statsElement = root.get("stats");
		JsonObject stats;
		if (statsElement != null && statsElement.isJsonObject()) {
			stats = statsElement.getAsJsonObject();
		} else {
			stats = new JsonObject();
			root.add("stats", stats);
		}
		for (Map.Entry<String, Map<String, Long>> namespace : truth.entrySet()) {
			JsonElement existing = stats.get(namespace.getKey());
			JsonObject out = existing != null && existing.isJsonObject() ? existing.getAsJsonObject() : new JsonObject();
			for (Map.Entry<String, Long> stat : namespace.getValue().entrySet()) {
				out.addProperty(stat.getKey(), stat.getValue());
			}
			stats.add(namespace.getKey(), out);
		}

		mTruth = truth;
		mBaseline = baseline;
	}

	/** The 64-bit value as of the last load or save, or 0 if there is none */
	public long getStat(String namespace, String stat) {
		return mTruth.getOrDefault(namespace, Map.of()).getOrDefault(stat, 0L);
	}

	private interface StatVisitor {
		void visit(String namespace, String stat, Map.Entry<String, JsonElement> entry);
	}

	/* Every numeric stat in stats JSON; anything else is left for the server to make of */
	private static void forEachStat(JsonObject root, StatVisitor visitor) {
		JsonElement stats = root.get("stats");
		if (stats == null || !stats.isJsonObject()) {
			return;
		}
		for (Map.Entry<String, JsonElement> namespace : stats.getAsJsonObject().entrySet()) {
			if (!namespace.getValue().isJsonObject()) {
				continue;
			}
			for (Map.Entry<String, JsonElement> stat : namespace.getValue().getAsJsonObject().entrySet()) {
				if (stat.getValue().isJsonPrimitive() && stat.getValue().getAsJsonPrimitive().isNumber()) {
					visitor.visit(namespace.getKey(), stat.getKey(), stat);
				}
			}
		}
	}

	private static int clamp(long value) {
		return (int) Math.max(Integer.MIN_VALUE, Math.min(value, Integer.MAX_VALUE));
	}

	private static <T> Map<String, Map<String, T>> deepCopy(Map<String, Map<String, T>> map) {
		Map<String, Map<String, T>> out = new HashMap<>();
		for (Map.Entry<String, Map<String, T>> inner : map.entrySet()) {
			out.put(inner.getKey(), new HashMap<>(inner.getValue()));
		}
		return out;
	}

	/* time_since_death and the like count ticks since an event, so are not added up across saves */
	private static boolean isTimeSince(String namespace, String stat) {
		return namespace.equals("minecraft:custom") && stat.startsWith("minecraft:time_since");
	}
}
