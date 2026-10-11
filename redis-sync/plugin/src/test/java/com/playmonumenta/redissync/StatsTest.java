package com.playmonumenta.redissync;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.ServerStatsDataSaveEvent;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import com.playmonumenta.redissync.adapters.TestVersionAdapter;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import java.io.File;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static com.playmonumenta.redissync.RedisSyncTestHarness.CUSTOM_STATS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stats outside a player's own logins and saves. Their round trip, history alignment, handoffs and
 * offline edits ride on the progress the other tests already check, which the harness keeps in a
 * stat too.
 *
 * <p>MockBukkit rethrows a listener's exception to whoever fired the event, which is what the
 * assertThrows below see. Paper instead logs it, and the plugin that fired the event sees none.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class StatsTest {
	private static final String JUMP = "minecraft:jump";

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
		harness.setStat(player, CUSTOM_STATS, JUMP, 41);
		harness.setProgress(player, 4);
		harness.disconnect(player);
		harness.awaitSaved(player);

		assertEquals(41, harness.savedStat(player, CUSTOM_STATS, JUMP));
		assertEquals(List.of(4, 3), harness.savedProgressHistoryExceptStats(player));
		assertEquals(List.of(4), harness.savedStatProgressHistory(player));
	}

	/**
	 * CraftOfflinePlayer's stat methods load a player's stats when they are not online here, and
	 * its setters save them straight back. The read is served from redis, and the save is refused
	 * loudly: it is not this shard's to make, and would pair with nothing on the other history lists.
	 */
	@Test
	void statsOfAPlayerNotOnlineAreReadButNeverSaved(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 3);
		harness.tick(2);
		UUID uuid = saved.getUniqueId();

		JsonObject read = harness.offlineStats(uuid);
		assertEquals(3, RedisSyncTestHarness.statIn(read, CUSTOM_STATS, RedisSyncTestHarness.PROGRESS_STAT));

		RedisSyncTestHarness.putStat(read, CUSTOM_STATS, JUMP, 1);
		ServerStatsDataSaveEvent save = new ServerStatsDataSaveEvent(TestVersionAdapter.statsFile(uuid), read.toString());
		assertThrows(IllegalStateException.class, () -> harness.callEvent(save));
		assertTrue(save.isCancelled(), "nor written to the server's own file");
		harness.awaitPluginCommandsExecuted();
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisStatsPath(uuid)));

		assertEquals(3, harness.progress(harness.join("Tester")), "the next login loads as usual");
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

	/** NPC plugins construct fake players, which load stats like any other but are never saved */
	@Test
	void fakePlayersLoadStatsButAreNotSaved(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 3);
		harness.tick(2);

		TestPlayer npc = harness.newPlayer("Tester");
		JsonObject stats = harness.offlineStats(npc.getUniqueId());
		harness.callEvent(new PlayerAdvancementDataLoadEvent(npc, new File("unused-in-tests")));
		assertEquals(3, RedisSyncTestHarness.statIn(stats, CUSTOM_STATS, RedisSyncTestHarness.PROGRESS_STAT));
		assertNull(PlayerSessions.getShardData(npc.getUniqueId()), "no session was kept");
		MonumentaRedisSyncAPI.savePlayer(npc);
		harness.awaitPluginCommandsExecuted();
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisStatsPath(saved)), "the fake player was not saved");

		assertEquals(3, harness.progress(harness.join("Tester")), "a real login after it loads as usual");
	}

	/**
	 * Stats in redis the server cannot parse are handed to it anyway, as advancements are: it starts
	 * from none, and its next save puts that in redis.
	 */
	@Test
	void statsTheServerCannotParseStartFromNone(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 3);
		harness.redisSetListEntry(MonumentaRedisSyncAPI.getRedisStatsPath(saved), 0, "not json {");

		TestPlayer player = harness.join("Tester");
		assertEquals(3, harness.progressExceptStats(player));
		assertEquals(-1, harness.statProgress(player));
		harness.setProgress(player, 4);
		harness.disconnect(player);
		harness.awaitSaved(player);
		assertEquals(List.of(4, 3), harness.savedProgressHistoryExceptStats(player));
		assertEquals(4, harness.statProgress(harness.join("Tester")));
	}

	/**
	 * Another plugin reading the stats of a player who is here but still configuring, whose read
	 * fails, leaves the failure behind. It must not fail the player's session at a datapack reload,
	 * whose advancement load has no stats load before it.
	 */
	@Test
	void failedStatsReadLeftBehindDoesNotFailAPlayingSession(RedisSyncTestHarness harness) throws Exception {
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
		assertEquals(List.of(5, 5), harness.savedProgressHistory(player), "and still saved");
	}

	/** A plugin saving another player inside a PlayerSaveEvent: each full save's stats go with its own playerdata */
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
	 * A full save whose stats save never comes (spigot.yml's stats.disable-saving stops the server
	 * making it) leaves the stats history behind, which is logged. Nothing is left over for a later
	 * stats save to be taken as part of it.
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
		assertThrows(IllegalStateException.class, () -> harness.callEvent(stray), "not taken as the skipped save's");

		harness.setProgress(player, 6);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(List.of(6, 5), harness.savedProgressHistoryExceptStats(player));
		assertEquals(List.of(6), harness.savedStatProgressHistory(player));
	}
}
