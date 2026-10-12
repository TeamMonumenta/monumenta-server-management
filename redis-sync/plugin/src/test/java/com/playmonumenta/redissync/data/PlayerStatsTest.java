package com.playmonumenta.redissync.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The 64-bit stats arithmetic on its own, as a login and its saves drive it */
public class PlayerStatsTest {
	private static final String CUSTOM = "minecraft:custom";
	private static final String MINED = "minecraft:mined";
	private static final long TWO_TO_THE_30 = 1L << 30;

	@Test
	void loadWrapsCountsAndClampsTimeSince() {
		JsonObject root = stats("{\"minecraft:custom\":{\"minecraft:jump\":5000000000,\"minecraft:time_since_death\":5000000000,"
			+ "\"minecraft:time_since_rest\":7},\"minecraft:mined\":{\"minecraft:stone\":12}}");
		PlayerStats stats = PlayerStats.load(root);

		assertEquals(5_000_000_000L % TWO_TO_THE_30, value(root, CUSTOM, "minecraft:jump"));
		assertEquals(Integer.MAX_VALUE, value(root, CUSTOM, "minecraft:time_since_death"));
		assertEquals(7, value(root, CUSTOM, "minecraft:time_since_rest"));
		assertEquals(12, value(root, MINED, "minecraft:stone"));
		assertEquals(5_000_000_000L, stats.getStat(CUSTOM, "minecraft:jump"));
	}

	@Test
	void savesAddWhatTheServerCountedSinceTheLastLoadOrSave() {
		JsonObject loaded = stats("{\"minecraft:custom\":{\"minecraft:jump\":5000000000}}");
		PlayerStats stats = PlayerStats.load(loaded);
		long held = value(loaded, CUSTOM, "minecraft:jump");

		JsonObject first = stats("{\"minecraft:custom\":{\"minecraft:jump\":" + (held + 10) + "}}");
		stats.save(first);
		assertEquals(5_000_000_010L, value(first, CUSTOM, "minecraft:jump"));

		JsonObject second = stats("{\"minecraft:custom\":{\"minecraft:jump\":" + (held + 25) + "}}");
		stats.save(second);
		assertEquals(5_000_000_025L, value(second, CUSTOM, "minecraft:jump"));
	}

	/** The server's value passing 2^30 must not be wrapped again for the next baseline */
	@Test
	void countingPast2To30IsCountedOnce() {
		JsonObject loaded = stats("{\"minecraft:custom\":{\"minecraft:jump\":" + (TWO_TO_THE_30 - 5) + "}}");
		PlayerStats stats = PlayerStats.load(loaded);
		for (int save = 1; save <= 3; save++) {
			JsonObject saved = stats("{\"minecraft:custom\":{\"minecraft:jump\":" + (TWO_TO_THE_30 - 5 + 10 * save) + "}}");
			stats.save(saved);
			assertEquals(TWO_TO_THE_30 - 5 + 10L * save, value(saved, CUSTOM, "minecraft:jump"), "save " + save);
		}
	}

	/** A plugin lowering a stat lowers it by as much, even where the server holds a wrapped value */
	@Test
	void decreasesAreCountedAsDeltasToo() {
		JsonObject loaded = stats("{\"minecraft:custom\":{\"minecraft:jump\":5000000000,\"minecraft:walk\":20}}");
		PlayerStats stats = PlayerStats.load(loaded);
		long held = value(loaded, CUSTOM, "minecraft:jump");

		JsonObject saved = stats("{\"minecraft:custom\":{\"minecraft:jump\":" + (held - 100) + ",\"minecraft:walk\":0}}");
		stats.save(saved);
		assertEquals(4_999_999_900L, value(saved, CUSTOM, "minecraft:jump"));
		assertEquals(0, value(saved, CUSTOM, "minecraft:walk"), "below 2^30 the server's value is the real one, so setting it works");
	}

	@Test
	void negativeSavedValuesWrapIntoRange() {
		JsonObject root = stats("{\"minecraft:custom\":{\"minecraft:jump\":-3}}");
		PlayerStats stats = PlayerStats.load(root);
		assertEquals(TWO_TO_THE_30 - 3, value(root, CUSTOM, "minecraft:jump"));
		JsonObject saved = stats("{\"minecraft:custom\":{\"minecraft:jump\":" + (TWO_TO_THE_30 - 3 + 5) + "}}");
		stats.save(saved);
		assertEquals(2, value(saved, CUSTOM, "minecraft:jump"));
	}

	@Test
	void statsNewThisSessionCountFromZeroAndStatsNotReportedAreKept() {
		PlayerStats stats = PlayerStats.load(stats("{\"minecraft:mined\":{\"minecraft:stone\":3000000000}}"));
		JsonObject saved = stats("{\"minecraft:custom\":{\"minecraft:jump\":4}}");
		stats.save(saved);

		assertEquals(4, value(saved, CUSTOM, "minecraft:jump"));
		assertEquals(3_000_000_000L, value(saved, MINED, "minecraft:stone"), "kept though the server did not report it");
	}

	@Test
	void timeSinceStatsAreSavedAsTheServerHasThem() {
		PlayerStats stats = PlayerStats.load(stats("{\"minecraft:custom\":{\"minecraft:time_since_death\":500}}"));
		JsonObject saved = stats("{\"minecraft:custom\":{\"minecraft:time_since_death\":20}}");
		stats.save(saved);
		assertEquals(20, value(saved, CUSTOM, "minecraft:time_since_death"));
	}

	@Test
	void statsWithNothingLoadedAreTakenAsTheServerHasThem() {
		PlayerStats stats = new PlayerStats();
		JsonObject saved = stats("{\"minecraft:custom\":{\"minecraft:jump\":40}}");
		stats.save(saved);
		assertEquals(40, value(saved, CUSTOM, "minecraft:jump"));

		JsonObject next = stats("{\"minecraft:custom\":{\"minecraft:jump\":45}}");
		stats.save(next);
		assertEquals(45, value(next, CUSTOM, "minecraft:jump"));
	}

	@Test
	void clampForReadingClampsRatherThanWraps() {
		JsonObject root = stats("{\"minecraft:custom\":{\"minecraft:jump\":5000000000,\"minecraft:walk\":12}}");
		PlayerStats.clampForReading(root);
		assertEquals(Integer.MAX_VALUE, value(root, CUSTOM, "minecraft:jump"));
		assertEquals(12, value(root, CUSTOM, "minecraft:walk"));
	}

	@Test
	void entriesThatAreNotNumbersAreLeftAlone() {
		JsonObject root = stats("{\"minecraft:custom\":{\"minecraft:jump\":\"lots\",\"minecraft:walk\":5000000000},\"odd\":3}");
		PlayerStats stats = PlayerStats.load(root);
		assertEquals("lots", root.getAsJsonObject("stats").getAsJsonObject(CUSTOM).get("minecraft:jump").getAsString());
		assertEquals(5_000_000_000L, stats.getStat(CUSTOM, "minecraft:walk"));
	}

	private static JsonObject stats(String inner) {
		return JsonParser.parseString("{\"stats\":" + inner + ",\"DataVersion\":3700}").getAsJsonObject();
	}

	private static long value(JsonObject root, String namespace, String stat) {
		return root.getAsJsonObject("stats").getAsJsonObject(namespace).get(stat).getAsLong();
	}
}
