package com.playmonumenta.redissync;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.ServerStatsDataSaveEvent;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import com.playmonumenta.redissync.adapters.TestVersionAdapter;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
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
 * Stats are stored as 64-bit values, though the server only holds ints: each login gives it values
 * it can count up from, and each save adds what it counted. Round trips, history alignment,
 * handoffs and offline edits are covered by the other tests through progress, which the harness
 * also stores in a stat.
 *
 * <p>MockBukkit rethrows a listener's exception to whoever fired the event, which is what the
 * assertThrows below check. Paper instead logs it, and the plugin that fired the event sees nothing.
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
		assertEquals((int) (big % TWO_TO_THE_30), harness.stat(player, CUSTOM_STATS, JUMP), "wrapped, with room to count up");
		harness.incrementStat(player, CUSTOM_STATS, JUMP, 10);
		harness.disconnect(player);
		harness.awaitSaved(player);
		assertEquals(big + 10, harness.savedStat(player, CUSTOM_STATS, JUMP));

		TestPlayer again = harness.join("Tester");
		assertEquals((int) ((big + 10) % TWO_TO_THE_30), harness.stat(again, CUSTOM_STATS, JUMP));
	}

	/**
	 * The server keeps counting up from what it holds, past the 2^30 that loaded values are wrapped
	 * below. Each save must count from what the server held at the previous save, not from that
	 * value wrapped again, or every save after crossing 2^30 would add another 2^30.
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

	/** time_since stats count ticks since an event rather than accumulating, so they are neither wrapped nor added to */
	@Test
	void timeSinceStatsAreLoadedAndSavedUnchanged(RedisSyncTestHarness harness) throws Exception {
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
	 * Each connection's saves count from what that connection loaded. A reconnect that takes over
	 * from a connection still in the game must not mix the two up.
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
	 * Before stats were stored in redis the server kept them in its own files. A player with none in
	 * redis yet starts from the server's file, which their first save then copies into redis. Their
	 * stats history is shorter than the others from then on, but aligned with the newest entries.
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

	/**
	 * CraftOfflinePlayer's stat methods load a player's stats when they are not online here, and
	 * its setters save them right back. The read is served from redis, clamped rather than wrapped
	 * since nothing will count from it. The save is refused with an exception: this shard does not
	 * own the data, and the entry would not match the other lists.
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
		assertTrue(save.isCancelled(), "also not written to the server's own file");
		harness.awaitPluginCommandsExecuted();
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisStatsPath(uuid)));

		/* The read left nothing behind for the next login to trip over */
		TestPlayer player = harness.join("Tester");
		assertEquals(3, harness.progress(player), "the next login loads as usual");
		assertEquals((int) (5_000_000_000L % TWO_TO_THE_30), harness.stat(player, CUSTOM_STATS, JUMP));
	}

	/** A stats save that is not part of a full save is refused even for a player who is online */
	@Test
	void statsSaveOutsideAFullSaveIsRefused(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 5);
		ServerStatsDataSaveEvent save = new ServerStatsDataSaveEvent(TestVersionAdapter.statsFile(player.getUniqueId()),
			harness.getAdapter().getLiveStats(player).toString());
		assertThrows(IllegalStateException.class, () -> harness.callEvent(save));

		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(List.of(5), harness.savedProgressHistory(player), "only the full save is in the history");
	}

	/**
	 * NPC plugins construct fake players, which load stats like any other but are never saved. They
	 * get values clamped rather than wrapped, since nothing counts from them.
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
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisStatsPath(saved)), "the fake player was not saved");

		TestPlayer player = harness.join("Tester");
		assertEquals(3, harness.progress(player), "a real login after it loads as usual");
		assertEquals((int) (5_000_000_000L % TWO_TO_THE_30), harness.stat(player, CUSTOM_STATS, JUMP));
	}

	/**
	 * A failed stats read by another plugin for a player already on this shard must not fail the
	 * player's session, including on a later datapack reload, whose advancement load has no stats
	 * load before it.
	 */
	@Test
	void failedStatsReadDoesNotFailAPlayingSession(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 5);
		String statsPath = MonumentaRedisSyncAPI.getRedisStatsPath(player);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		harness.redisRename(statsPath, statsPath + ".good");
		harness.redisBreakKey(statsPath);
		harness.offlineStats(player.getUniqueId());
		harness.redisDelete(statsPath);
		harness.redisRename(statsPath + ".good", statsPath);

		harness.reloadDatapacks();
		harness.tick(2);
		assertTrue(player.isPlaced(), "not kicked");
		harness.awaitSaved(player);
		assertEquals(List.of(5, 5), harness.savedProgressHistory(player), "still saved");
	}

	/** A plugin saving another player inside a PlayerSaveEvent: each full save's stats pair with its own playerdata */
	@Test
	void fullSaveNestedInAnotherKeepsBothAligned(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("First");
		TestPlayer second = harness.join("Second");
		harness.setProgress(first, 1);
		harness.setProgress(second, 2);
		AtomicBoolean nested = new AtomicBoolean(false);
		harness.onEvent(PlayerSaveEvent.class, event -> {
			if (event.getPlayer().getUniqueId().equals(first.getUniqueId()) && nested.compareAndSet(false, true)) {
				try {
					MonumentaRedisSyncAPI.savePlayer(second);
				} catch (Exception ex) {
					throw new IllegalStateException(ex);
				}
			}
		});

		MonumentaRedisSyncAPI.savePlayer(first);
		harness.awaitSaved(first);
		harness.awaitSaved(second);
		assertEquals(List.of(1), harness.savedProgressHistory(first));
		assertEquals(List.of(2), harness.savedProgressHistory(second));
	}

	/**
	 * A full save with no stats save (spigot.yml's stats.disable-saving prevents it) leaves the stats
	 * history behind, which is logged. No state is left over that would make a later stray stats
	 * save count as part of it.
	 */
	@Test
	void fullSaveWithoutAStatsSaveLeavesNothingBehind(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 5);
		harness.getAdapter().setSkipStatsSaves(true);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.getAdapter().setSkipStatsSaves(false);

		ServerStatsDataSaveEvent stray = new ServerStatsDataSaveEvent(TestVersionAdapter.statsFile(player.getUniqueId()),
			harness.getAdapter().getLiveStats(player).toString());
		assertThrows(IllegalStateException.class, () -> harness.callEvent(stray), "not treated as part of the earlier full save");

		harness.setProgress(player, 6);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(List.of(6, 5), harness.savedProgressHistoryExceptStats(player));
		assertEquals(List.of(6), harness.savedStatProgressHistory(player));
	}

	/** A full save whose stats save is skipped leaves the baseline alone, so the next stats save counts both */
	@Test
	void skippedStatsSaveIsCountedByTheNextOne(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 1);
		long big = 5_000_000_000L;
		setSavedStat(harness, saved, JUMP, big);

		TestPlayer player = harness.join("Tester");
		harness.incrementStat(player, CUSTOM_STATS, JUMP, 5);
		harness.getAdapter().setSkipStatsSaves(true);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.getAdapter().setSkipStatsSaves(false);
		harness.awaitSaved(player);
		assertEquals(big, harness.savedStat(player, CUSTOM_STATS, JUMP));

		harness.incrementStat(player, CUSTOM_STATS, JUMP, 7);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(big + 12, harness.savedStat(player, CUSTOM_STATS, JUMP));
	}

	/** A datapack reload's advancement load must not replace or reset a playing session's stats */
	@Test
	void datapackReloadKeepsCountingFromTheSessionsLoad(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 1);
		long big = 5_000_000_000L;
		setSavedStat(harness, saved, JUMP, big);

		TestPlayer player = harness.join("Tester");
		harness.incrementStat(player, CUSTOM_STATS, JUMP, 10);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		harness.incrementStat(player, CUSTOM_STATS, JUMP, 10);
		harness.reloadDatapacks();
		harness.tick(2);
		assertTrue(player.isPlaced(), "not kicked");
		harness.incrementStat(player, CUSTOM_STATS, JUMP, 10);
		harness.disconnect(player);
		harness.awaitSaved(player);
		assertEquals(big + 30, harness.savedStat(player, CUSTOM_STATS, JUMP));
	}

	/** Rollback restores the 64-bit values, and the next session counts from them */
	@Test
	void rollbackRestoresThe64BitStats(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 1);
		long big = 5_000_000_000L;
		setSavedStat(harness, saved, JUMP, big);
		TestPlayer moderator = harness.join("Moderator");

		TestPlayer first = harness.join("Tester");
		harness.incrementStat(first, CUSTOM_STATS, JUMP, 5);
		MonumentaRedisSyncAPI.savePlayer(first);
		harness.incrementStat(first, CUSTOM_STATS, JUMP, 7);
		MonumentaRedisSyncAPI.savePlayer(first);
		harness.awaitSaved(first);
		assertEquals(big + 12, harness.savedStat(first, CUSTOM_STATS, JUMP));

		MonumentaRedisSyncAPI.playerRollback(moderator, first, 1);
		harness.awaitWithTicks("player kicked to apply the rollback", () -> !first.isPlaced());

		TestPlayer second = harness.join("Tester");
		assertEquals((int) ((big + 5) % TWO_TO_THE_30), harness.stat(second, CUSTOM_STATS, JUMP));
		harness.incrementStat(second, CUSTOM_STATS, JUMP, 1);
		harness.disconnect(second);
		harness.awaitSaved(second);
		assertEquals(big + 6, harness.savedStat(second, CUSTOM_STATS, JUMP));
	}

	/**
	 * A stash from before stats were stored in redis keeps the current stats, which are pushed
	 * again from redis: the 64-bit values, not the wrapped ones the server holds.
	 */
	@Test
	void stashFromBeforeStatsKeepsThe64BitStats(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 1);
		long big = 5_000_000_000L;
		setSavedStat(harness, saved, JUMP, big);

		TestPlayer first = harness.join("Tester");
		harness.incrementStat(first, CUSTOM_STATS, JUMP, 3);
		MonumentaRedisSyncAPI.stashPut(first, null);
		harness.awaitWithTicks("stash written", () -> first.wasTold("saved to stash successfully"));
		harness.redisHashDelete(MonumentaRedisSyncAPI.getStashPath(), first.getUniqueId() + "-stats");

		harness.incrementStat(first, CUSTOM_STATS, JUMP, 4);
		MonumentaRedisSyncAPI.stashGet(first, null);
		harness.awaitWithTicks("player kicked to load the stash", () -> !first.isPlaced());

		TestPlayer second = harness.join("Tester");
		assertEquals(big + 7, harness.savedStat(second, CUSTOM_STATS, JUMP));
		assertEquals((int) ((big + 7) % TWO_TO_THE_30), harness.stat(second, CUSTOM_STATS, JUMP));
		harness.incrementStat(second, CUSTOM_STATS, JUMP, 1);
		harness.disconnect(second);
		harness.awaitSaved(second);
		assertEquals(big + 8, harness.savedStat(second, CUSTOM_STATS, JUMP));
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
