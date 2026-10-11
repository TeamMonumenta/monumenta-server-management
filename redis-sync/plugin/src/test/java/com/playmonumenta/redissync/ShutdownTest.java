package com.playmonumenta.redissync;

import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import org.bukkit.util.Vector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Closing redis with commands still queued drops them, so the plugin waits for every write it has
 * in flight before it does. Each test stalls redis, so those writes are still queued at shutdown,
 * and reads back what was committed as soon as shutdown returns.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class ShutdownTest {
	/** The daily restart stops a shard as soon as it is empty, so the last player's final save can still be in flight. */
	@Test
	void saveInFlightWhenThePluginIsDisabledCommits(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 9);

		harness.pauseRedisWrites(1000);
		harness.disconnect(player);
		assertTrue(PlayerSessions.hasPendingSaves(player), "the final save is still in flight");
		harness.disablePlugin();

		assertEquals(9, harness.savedProgress(player));
	}

	/** With monumenta-mixins the server saves and removes everyone online, then disables plugins only 100ms later. */
	@Test
	void playersOnlineWhenTheServerStopsAreSaved(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("First");
		TestPlayer second = harness.join("Second");
		harness.setProgress(first, 11);
		harness.setProgress(second, 12);

		harness.pauseRedisWrites(1000);
		harness.stopServer();

		assertEquals(11, harness.savedProgress(first));
		assertEquals(12, harness.savedProgress(second));
	}

	/** Writes made through the API rather than a save (here a location set for another shard) count too. */
	@Test
	void apiWriteInFlightWhenThePluginIsDisabledCommits(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.pauseRedisWrites(1000);
		MonumentaRedisSyncAPI.setPlayerLocationOnWorld(player, "elsewhere", new Vector(1, 2, 3), 0, 0);
		harness.disablePlugin();

		String shardData = harness.redisHashGet(MonumentaRedisSyncAPI.getRedisPerShardDataPath(player),
			MonumentaRedisSyncAPI.getRedisPerShardDataWorldKey("elsewhere"));
		assertTrue(shardData != null && shardData.contains("[1.0,2.0,3.0]"), "location committed: " + shardData);
	}

	/**
	 * A rollback mid-write holds its player's lock until the write is done, so the shutdown save is
	 * skipped. Shutdown waits for the rollback's write, which is tracked like a save, and for
	 * nothing more: the kick that would follow it is never run.
	 */
	@Test
	void shutdownDuringARollbackKeepsTheRollbackWithoutStalling(RedisSyncTestHarness harness) throws Exception {
		TestPlayer moderator = harness.join("Moderator");
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 30);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		harness.setProgress(player, 31);

		MonumentaRedisSyncAPI.playerRollback(moderator, player, 0);
		harness.awaitSavedWithoutTicking(player);
		harness.pauseRedisWrites(1000);
		harness.tick();
		assertTrue(PlayerSessions.hasPendingSaves(player), "the rollback is still being written");

		long start = System.currentTimeMillis();
		harness.stopServer();
		assertTrue(System.currentTimeMillis() - start < MonumentaRedisSyncAPI.TIMEOUT_SECONDS * 1000 / 2, "shutdown did not wait out the timeout");
		assertEquals(30, harness.savedProgress(player), "the rollback is the newest save");
	}
}
