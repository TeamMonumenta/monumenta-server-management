package com.playmonumenta.redissync;

import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stash get, rollback and loadFromPlayer replace a player's saved data while they are online. The
 * replacement is read first, while the player plays on; then the proxy is asked to reconnect them
 * (which the tests do themselves, by disconnecting the player). As they leave, their final save is
 * made as usual, and the replacement is pushed after it once it has committed, so it is what the
 * reconnect loads and the final save stays in history as a way back.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class DataHandoffTest {
	/** What the player does until they leave is in their final save, just below the replacement */
	@Test
	void stashGetIsPushedAfterTheFinalSave(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("Tester");
		harness.setProgress(first, 20);
		stashPut(harness, first);
		harness.setProgress(first, 21);

		MonumentaRedisSyncAPI.stashGet(first, null);
		awaitReconnectAsked(harness, first);
		harness.setProgress(first, 22);
		harness.disconnect(first);

		TestPlayer next = harness.join("Tester");
		assertEquals(20, harness.progress(next));
		assertEquals(List.of(20, 22), harness.savedProgressHistory(next).subList(0, 2), "the final save is just below the stash");
	}

	/** The reconnect gets the replacement even if it is still being written: the login waits for the session lease */
	@Test
	void reconnectWaitsForTheReplacementWrite(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("Tester");
		harness.setProgress(first, 20);
		stashPut(harness, first);
		harness.setProgress(first, 21);

		MonumentaRedisSyncAPI.stashGet(first, null);
		awaitReconnectAsked(harness, first);
		harness.pauseRedisWrites(1000);
		harness.disconnect(first);
		assertTrue(PlayerSessions.hasPendingSaves(first), "the final save and the stash are still being written");

		assertEquals(20, harness.progress(harness.join("Tester")));
	}

	/** A write that fails part way is reported; the reconnect loads whatever redis holds */
	@Test
	void stashGetWhoseWriteFailsSaysSo(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("Tester");
		stashPut(harness, first);

		MonumentaRedisSyncAPI.stashGet(first, null);
		awaitReconnectAsked(harness, first);
		harness.leaveGame(first);
		harness.awaitSaved(first);
		/* Broken after the final save, so only the stash's write fails */
		harness.redisBreakKey(MonumentaRedisSyncAPI.getRedisHistoryPath(first));
		harness.closeConnection(first);
		harness.awaitWithTicks("told it failed", () -> first.wasTold("Failed to load stash data"));
	}

	/** Nothing to hand off: the player is told, and nothing else happens */
	@Test
	void stashGetWithNoStashChangesNothing(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.stashGet(player, "no_such_stash");
		harness.awaitWithTicks("told there is no stash", () -> player.wasTold("No stash data found"));
		assertNull(MonumentaRedisSyncAPI.getDeparture(player));
		assertTrue(player.getPluginMessages().isEmpty(), "proxy never asked to reconnect them");
		assertTrue(player.isPlaced());
	}

	@Test
	void rollbackPastTheEndOfHistoryChangesNothing(RedisSyncTestHarness harness) throws Exception {
		TestPlayer moderator = harness.join("Moderator");
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.playerRollback(moderator, player, 50);
		harness.awaitWithTicks("moderator told", () -> moderator.wasTold("Failed to retrieve player's rollback data"));
		assertNull(MonumentaRedisSyncAPI.getDeparture(player));
		assertTrue(player.getPluginMessages().isEmpty());
	}

	/**
	 * The index counts back from the newest save made before the call, including one still being
	 * written, so redis is stalled to make sure the rollback reads after it.
	 */
	@Test
	void rollbackRestoresTheRequestedSave(RedisSyncTestHarness harness) throws Exception {
		TestPlayer moderator = harness.join("Moderator");
		TestPlayer first = savedTwice(harness, 30, 31);
		harness.setProgress(first, 32);
		harness.pauseRedisWrites(500);
		MonumentaRedisSyncAPI.savePlayer(first);

		MonumentaRedisSyncAPI.playerRollback(moderator, first, 1);
		awaitReconnectAsked(harness, first);
		harness.setProgress(first, 33);
		harness.disconnect(first);
		harness.awaitWithTicks("moderator told", () -> moderator.wasTold("rolled back successfully"));

		TestPlayer next = harness.join("Tester");
		assertEquals(31, harness.progress(next));
		assertEquals(List.of(31, 33, 32, 31, 30), harness.savedProgressHistory(next).subList(0, 5));
	}

	/** The proxy never acts on the reconnect: the request times out, and the player's data stays theirs */
	@Test
	void handoffTheProxyNeverActsOnTimesOut(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("Tester");
		harness.setProgress(first, 20);
		stashPut(harness, first);

		MonumentaRedisSyncAPI.stashGet(first, null);
		awaitReconnectAsked(harness, first);
		harness.tick(MonumentaRedisSyncAPI.TRANSFER_TIMEOUT_TICKS + 5);
		assertTrue(first.wasTold("Loading stash data timed out"));
		assertNull(MonumentaRedisSyncAPI.getDeparture(first));

		harness.setProgress(first, 21);
		harness.disconnect(first);
		assertEquals(21, harness.progress(harness.join("Tester")));
	}

	/** The player leaves while the replacement is still being read: nothing is replaced */
	@Test
	void handoffForAPlayerWhoLeftDuringTheReadDoesNothing(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("Tester");
		harness.setProgress(first, 20);
		stashPut(harness, first);
		harness.setProgress(first, 21);

		MonumentaRedisSyncAPI.stashGet(first, null);
		harness.disconnect(first);
		harness.awaitWithTicks("told it was not loaded", () -> first.wasTold("Not loading stash data"));
		assertEquals(21, harness.progress(harness.join("Tester")));
	}

	@Test
	void loadFromPlayerCopiesTheOtherPlayersNewestSave(RedisSyncTestHarness harness) throws Exception {
		TestPlayer source = harness.join("Source");
		TestPlayer target = harness.join("Target");
		harness.setProgress(source, 40);
		harness.setProgress(target, 2);

		harness.pauseRedisWrites(300);
		MonumentaRedisSyncAPI.savePlayer(source);
		MonumentaRedisSyncAPI.playerLoadFromPlayer(target, source, 0);
		awaitReconnectAsked(harness, target);
		harness.disconnect(target);

		assertEquals(40, harness.progress(harness.join("Target")));
	}

	/** A data handoff can't start while a transfer is pending */
	@Test
	void noHandoffWhileTransferring(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.sendPlayer(player, "other_shard");
		Exception refused = assertThrows(Exception.class, () -> MonumentaRedisSyncAPI.stashGet(player, null));
		assertTrue(String.valueOf(refused.getMessage()).contains("transferring"), refused.getMessage());
	}

	/** A data handoff through an earlier connection is refused outright */
	@Test
	void handoffThroughAnEarlierConnectionIsRefused(RedisSyncTestHarness harness) throws Exception {
		TestPlayer stale = harness.join("Tester");
		harness.disconnect(stale);
		TestPlayer current = harness.join("Tester");

		Exception refused = assertThrows(Exception.class, () -> MonumentaRedisSyncAPI.stashGet(stale, null));
		assertTrue(String.valueOf(refused.getMessage()).contains("not this player's current connection"), refused.getMessage());
		assertNull(MonumentaRedisSyncAPI.getDeparture(current));
	}

	/* The handoff has read its replacement and asked the proxy to reconnect the player */
	private static void awaitReconnectAsked(RedisSyncTestHarness harness, TestPlayer player) throws InterruptedException {
		harness.awaitWithTicks("proxy asked to reconnect", () -> MonumentaRedisSyncAPI.getDeparture(player) != null);
		assertTrue(player.getPluginMessages().stream().anyMatch(message -> message.startsWith(CleanSwitchListener.RECONNECT_CHANNEL)),
			"reconnect sent: " + player.getPluginMessages());
	}

	/* A player whose newest two saves have the given progress, newest last, and who is still online */
	private static TestPlayer savedTwice(RedisSyncTestHarness harness, int older, int newer) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, older);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.setProgress(player, newer);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		return player;
	}

	/* Stashes the player's current state, waiting until the stash is written */
	private static void stashPut(RedisSyncTestHarness harness, TestPlayer player) throws Exception {
		MonumentaRedisSyncAPI.stashPut(player, null);
		harness.awaitWithTicks("stash written", () -> player.wasTold("saved to stash successfully"));
	}
}
