package com.playmonumenta.redissync;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.ServerStatsDataSaveEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import com.playmonumenta.redissync.adapters.TestVersionAdapter;
import java.io.File;
import java.util.List;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.playmonumenta.redissync.RedisSyncTestHarness.CUSTOM_STATS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stats are saved as 64-bit values, though the server only holds ints: each login hands it values
 * it can count up from, and each save adds what it counted. Everything else about stats (history
 * alignment, handoffs, offline edits) rides on the progress the other tests already check, which
 * the harness keeps in a stat too.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class StatsTest {
	private static final String JUMP = "minecraft:jump";
	private static final String TIME_SINCE_DEATH = "minecraft:time_since_death";
	private static final long TWO_TO_THE_30 = 1L << 30;

	@Test
	void statsBeyondWhatTheServerCanHoldAreCountedAs64Bit(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 1);
		long big = 5_000_000_000L;
		setSavedStat(harness, saved, JUMP, big);

		TestPlayer player = harness.join("Tester");
		assertEquals((int) (big % TWO_TO_THE_30), harness.stat(player, CUSTOM_STATS, JUMP), "handed over wrapped, with room to count up");
		harness.incrementStat(player, CUSTOM_STATS, JUMP, 10);
		harness.disconnect(player);
		harness.awaitSaved(player);
		assertEquals(big + 10, harness.savedStat(player, CUSTOM_STATS, JUMP));

		TestPlayer again = harness.join("Tester");
		assertEquals((int) ((big + 10) % TWO_TO_THE_30), harness.stat(again, CUSTOM_STATS, JUMP));
	}

	/**
	 * The server keeps counting up from what it holds, past the 2^30 it was handed values below.
	 * Each save must count from what the server held at the last one, not from that value wrapped
	 * again, or every save after crossing 2^30 would add another 2^30.
	 */
	@Test
	void statThatCrosses2To30WithinASessionIsCountedOnce(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 1);
		long start = 3 * TWO_TO_THE_30 - 5;
		setSavedStat(harness, saved, JUMP, start);

		TestPlayer player = harness.join("Tester");
		for (int save = 1; save <= 3; save++) {
			harness.incrementStat(player, CUSTOM_STATS, JUMP, 10);
			MonumentaRedisSyncAPI.savePlayer(player);
			harness.awaitSaved(player);
			assertEquals(start + 10L * save, harness.savedStat(player, CUSTOM_STATS, JUMP), "after save " + save);
		}
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(start + 30, harness.savedStat(player, CUSTOM_STATS, JUMP), "a save with nothing counted changes nothing");
	}

	/** time_since stats count ticks since an event rather than adding up, so they are neither wrapped nor added to */
	@Test
	void timeSinceStatsAreHandedOverAndSavedAsTheyAre(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 1);
		setSavedStat(harness, saved, TIME_SINCE_DEATH, TWO_TO_THE_30 + 7);

		TestPlayer player = harness.join("Tester");
		assertEquals((int) (TWO_TO_THE_30 + 7), harness.stat(player, CUSTOM_STATS, TIME_SINCE_DEATH), "not wrapped");
		harness.setStat(player, CUSTOM_STATS, TIME_SINCE_DEATH, 100);
		harness.disconnect(player);
		harness.awaitSaved(player);
		assertEquals(100L, harness.savedStat(player, CUSTOM_STATS, TIME_SINCE_DEATH), "saved as the server has it, a death having reset it");

		setSavedStat(harness, player, TIME_SINCE_DEATH, 5_000_000_000L);
		assertEquals(Integer.MAX_VALUE, harness.stat(harness.join("Tester"), CUSTOM_STATS, TIME_SINCE_DEATH), "clamped when too big to hold");
	}

	/**
	 * Each connection's saves count from what that connection was handed. A reconnect that takes
	 * over from a connection still in the game must not mix the two up.
	 */
	@Test
	void reconnectWhileTheOldConnectionIsInTheGameCountsEachFromItsOwnLoad(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 1);
		long big = 5_000_000_000L;
		setSavedStat(harness, saved, JUMP, big);

		TestPlayer old = harness.join("Tester");
		harness.incrementStat(old, CUSTOM_STATS, JUMP, 3);
		TestPlayer reconnect = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.login(reconnect));
		assertFalse(old.isPlaced(), "the old connection was closed, saving it");
		harness.tick();

		harness.incrementStat(reconnect, CUSTOM_STATS, JUMP, 4);
		harness.disconnect(reconnect);
		harness.awaitSaved(reconnect);
		assertEquals(big + 7, harness.savedStat(reconnect, CUSTOM_STATS, JUMP));
	}

	/**
	 * Before stats were saved in redis the server kept them in its own files. A player with none in
	 * redis yet starts from the server's file, which their first save then puts in redis. Their
	 * stats history is shorter than the others from then on, but lined up with the newest entries.
	 */
	@Test
	void playerWithNoStatsInRedisStartsFromTheServersOwnFile(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 3);
		harness.redisDelete(MonumentaRedisSyncAPI.getRedisStatsPath(saved));
		JsonObject local = RedisSyncTestHarness.statsWith(CUSTOM_STATS, JUMP, 40);
		RedisSyncTestHarness.putStat(local, CUSTOM_STATS, RedisSyncTestHarness.PROGRESS_STAT, 3);
		harness.setLocalStatsFile(saved.getUniqueId(), local);

		TestPlayer player = harness.join("Tester");
		assertEquals(40, harness.stat(player, CUSTOM_STATS, JUMP));
		assertEquals(3, harness.progress(player));
		harness.incrementStat(player, CUSTOM_STATS, JUMP, 1);
		harness.setProgress(player, 4);
		harness.disconnect(player);
		harness.awaitSaved(player);

		assertEquals(41L, harness.savedStat(player, CUSTOM_STATS, JUMP));
		assertEquals(List.of(4, 3), harness.savedProgressHistoryExceptStats(player));
		assertEquals(List.of(4), harness.savedStatProgressHistory(player));
	}

	/*
	 * MockBukkit rethrows a listener's exception to whoever fired the event, which is what the
	 * assertThrows below see. Paper instead logs it, and the plugin that fired the event sees none.
	 */

	/**
	 * CraftOfflinePlayer's stat methods load a player's stats when they are not online here, and
	 * its setters save them straight back. The read is served from redis, clamped rather than
	 * wrapped as nothing will count from it, and the save is refused loudly: it is not this shard's
	 * to make, and would pair with nothing on the other history lists.
	 */
	@Test
	void statsOfAPlayerNotOnlineAreReadButNeverSaved(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 3);
		setSavedStat(harness, saved, JUMP, 5_000_000_000L);
		harness.tick(2);
		UUID uuid = saved.getUniqueId();

		JsonObject read = harness.offlineStats(uuid);
		assertEquals((long) Integer.MAX_VALUE, RedisSyncTestHarness.statIn(read, CUSTOM_STATS, JUMP), "clamped");
		assertEquals(3L, RedisSyncTestHarness.statIn(read, CUSTOM_STATS, RedisSyncTestHarness.PROGRESS_STAT));

		RedisSyncTestHarness.putStat(read, CUSTOM_STATS, JUMP, 1);
		ServerStatsDataSaveEvent save = new ServerStatsDataSaveEvent(TestVersionAdapter.statsFile(uuid), read.toString());
		assertThrows(IllegalStateException.class, () -> harness.callEvent(save));
		assertTrue(save.isCancelled(), "nor written to the server's own file");
		harness.awaitPluginCommandsExecuted();
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisStatsPath(uuid)));

		/* The read left nothing behind for the next login to trip over */
		TestPlayer player = harness.join("Tester");
		assertEquals(3, harness.progress(player));
		assertEquals((int) (5_000_000_000L % TWO_TO_THE_30), harness.stat(player, CUSTOM_STATS, JUMP));
	}

	/** A stats save that is not part of a full save is refused even for a player who is online */
	@Test
	void statsSaveOutsideAFullSaveIsNeverTakenForThePlayers(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 5);
		ServerStatsDataSaveEvent save = new ServerStatsDataSaveEvent(TestVersionAdapter.statsFile(player.getUniqueId()),
			harness.getAdapter().getLiveStats(player).toString());
		assertThrows(IllegalStateException.class, () -> harness.callEvent(save));

		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(List.of(5), harness.savedProgressHistory(player), "only the full save's");
	}

	/**
	 * NPC plugins construct fake players, which load stats like any other but are never saved. They
	 * get values clamped rather than wrapped, as nothing counts from them.
	 */
	@Test
	void fakePlayersStatsAreClamped(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 3);
		setSavedStat(harness, saved, JUMP, 5_000_000_000L);
		harness.tick(2);

		TestPlayer npc = harness.newPlayer("Tester");
		JsonObject stats = harness.offlineStats(npc.getUniqueId());
		harness.callEvent(new PlayerAdvancementDataLoadEvent(npc, new File("unused-in-tests")));
		assertEquals((long) Integer.MAX_VALUE, RedisSyncTestHarness.statIn(stats, CUSTOM_STATS, JUMP));
		assertNull(PlayerSessions.getShardData(npc.getUniqueId()), "no session was kept");
		MonumentaRedisSyncAPI.savePlayer(npc);
		harness.awaitPluginCommandsExecuted();
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisStatsPath(npc)), "the fake player was not saved");

		TestPlayer player = harness.join("Tester");
		assertEquals(3, harness.progress(player), "a real login after it loads as usual");
		assertEquals((int) (5_000_000_000L % TWO_TO_THE_30), harness.stat(player, CUSTOM_STATS, JUMP));
	}

	/* Sets a stat in the player's newest saved stats, keeping the rest */
	private static void setSavedStat(RedisSyncTestHarness harness, Player player, String stat, long value) throws Exception {
		String path = MonumentaRedisSyncAPI.getRedisStatsPath(player);
		String saved = harness.redisListEntry(path, 0);
		JsonObject stats = saved == null ? TestVersionAdapter.emptyStats() : JsonParser.parseString(saved).getAsJsonObject();
		RedisSyncTestHarness.putStat(stats, CUSTOM_STATS, stat, value);
		harness.redisSetListEntry(path, 0, stats.toString());
	}
}
