package com.playmonumenta.redissync;

import com.playmonumenta.common.event.PlayerTransferFailEvent;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Stash get, rollback and loadFromPlayer replace a player's saved data while they are online:
 * save, lock the player, wait for the save to commit, write the replacement, then kick them so
 * they rejoin with it. Nothing the player does after the lock may be saved over the replacement.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class DataHandoffTest {
	/**
	 * Stash the player, change them, load the stash, and leave and come straight back before the
	 * stash's write has committed. The login waits for that write, or the player would load their
	 * old state and their next save would bury the stash.
	 */
	@Test
	void stashGetIsWhatTheNextLoginLoadsEvenIfItIsStillBeingWritten(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("Tester");
		harness.setProgress(first, 20);
		stashPut(harness, first);

		harness.setProgress(first, 21);
		MonumentaRedisSyncAPI.stashGet(first, null);
		harness.awaitSavedWithoutTicking(first);
		/* The next tick reads the stash and hands redis the write, which the pause holds */
		harness.pauseRedisWrites(1000);
		harness.tick();
		assertTrue(PlayerSessions.hasPendingSaves(first), "the stash is still being written");
		harness.disconnect(first);

		assertEquals(20, harness.progress(harness.join("Tester")));
	}

	/**
	 * A command inside a stash get's write fails without failing the transaction, so the rest lands.
	 * The player's in-memory state can no longer be saved over what is there, so they are kicked to
	 * rejoin with whatever redis holds, and told it failed rather than that it worked.
	 */
	@Test
	void stashGetWhoseWritePartlyFailsKicksThePlayerAndSaysSo(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("Tester");
		stashPut(harness, first);

		MonumentaRedisSyncAPI.stashGet(first, null);
		harness.awaitSavedWithoutTicking(first);
		/* The stash is read from its own hash; only the write touches the player's history list */
		harness.redisBreakKey(MonumentaRedisSyncAPI.getRedisHistoryPath(first));
		harness.awaitWithTicks("player kicked", () -> !first.isPlaced());
		assertTrue(first.wasTold("Failed to load stash data"), "told it failed: " + first.getChat());
		assertFalse(first.wasTold("loaded successfully"));
	}

	/** A handoff with nothing to hand off unlocks the player straight away, rather than when the lock times out. */
	@Test
	void stashGetWithNoStashUnlocksThePlayerAtOnce(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.stashGet(player, "no_such_stash");
		assertTrue(MonumentaRedisSyncAPI.isPlayerTransferring(player));

		harness.awaitWithTicks("player unlocked", () -> !MonumentaRedisSyncAPI.isPlayerTransferring(player), 150);
		assertTrue(player.isPlaced());
	}

	@Test
	void rollbackPastTheEndOfHistoryUnlocksThePlayerAtOnce(RedisSyncTestHarness harness) throws Exception {
		TestPlayer moderator = harness.join("Moderator");
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.playerRollback(moderator, player, 50);

		harness.awaitWithTicks("player unlocked", () -> !MonumentaRedisSyncAPI.isPlayerTransferring(player), 150);
		assertTrue(player.isPlaced());
	}

	/**
	 * The index counts back from the newest save as it was when the rollback was asked for. Rollback
	 * first saves the current state as a way back and accounts for that entry, which it can only do
	 * once that save has committed, so redis is stalled to make sure it waits.
	 */
	@Test
	void rollbackRestoresTheRequestedSave(RedisSyncTestHarness harness) throws Exception {
		TestPlayer moderator = harness.join("Moderator");
		TestPlayer first = savedTwice(harness, 30, 31);

		harness.setProgress(first, 32);
		harness.pauseRedisWrites(1000);
		MonumentaRedisSyncAPI.playerRollback(moderator, first, 1);
		/* A rollback that did not wait would act on one of these ticks */
		harness.tick(5);
		harness.awaitSavedWithoutTicking(first);
		harness.awaitWithTicks("player kicked to apply the rollback", () -> !first.isPlaced());

		assertEquals(30, harness.progress(harness.join("Tester")));
	}

	/** If rollback's own save is skipped (here because the player is still joining), it must not count it. */
	@Test
	void rollbackWhoseOwnSaveWasSkippedRestoresTheRequestedSave(RedisSyncTestHarness harness) throws Exception {
		TestPlayer moderator = harness.join("Moderator");
		for (int progress = 40; progress <= 41; progress++) {
			TestPlayer player = harness.join("Tester");
			harness.setProgress(player, progress);
			harness.disconnect(player);
		}

		TestPlayer joining = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.login(joining));
		MonumentaRedisSyncAPI.playerRollback(moderator, joining, 1);
		harness.awaitWithTicks("player kicked to apply the rollback", () -> !joining.isPlaced());

		assertEquals(40, harness.progress(harness.join("Tester")), "index 1 counts back from the newest save, 41");
	}

	/**
	 * Once a rollback has started writing, the player stays locked until it is done, even past the
	 * lock's timeout. Unlocked, they would save again, and the kick that applies the rollback would
	 * save their current state right back over it while the moderator is told it worked.
	 */
	@Test
	void rollbackStalledPastTheLockTimeoutStillApplies(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger failures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer moderator = harness.join("Moderator");
		TestPlayer first = savedTwice(harness, 30, 31);

		MonumentaRedisSyncAPI.playerRollback(moderator, first, 1);
		harness.awaitSavedWithoutTicking(first);
		/* The next tick starts the rollback's read and write; the pause holds the write past the timeout */
		harness.pauseRedisWrites(3000);
		harness.tick();
		harness.tick(SessionLock.TIMEOUT_TICKS + 5);
		assertTrue(MonumentaRedisSyncAPI.isPlayerTransferring(first), "still locked while the rollback is being written");
		assertEquals(0, failures.get());

		harness.awaitWithTicks("player kicked to apply the rollback", () -> !first.isPlaced());
		assertEquals(30, harness.progress(harness.join("Tester")));
	}

	/**
	 * Another plugin cancels the kick that applies a rollback, so the player stays online with their
	 * old state in memory. They stay locked, nothing of it may be saved over the rollback, and every
	 * save it would make tries the kick again.
	 */
	@Test
	void rollbackSurvivesItsKickBeingCancelled(RedisSyncTestHarness harness) throws Exception {
		AtomicBoolean cancelKicks = new AtomicBoolean(true);
		harness.onEvent(PlayerKickEvent.class, event -> event.setCancelled(cancelKicks.get()));
		TestPlayer moderator = harness.join("Moderator");
		TestPlayer first = savedTwice(harness, 30, 31);

		MonumentaRedisSyncAPI.playerRollback(moderator, first, 1);
		harness.awaitWithTicks("rollback written", () -> moderator.wasTold("rolled back successfully"));
		harness.tick(SessionLock.TIMEOUT_TICKS + 5);
		assertTrue(first.isPlaced(), "the kick was cancelled");
		assertTrue(MonumentaRedisSyncAPI.isPlayerTransferring(first), "still locked: their data is no longer theirs to play with");
		assertFalse(first.wasTold("Transferring timed out"), "a handoff that wrote has not failed");

		cancelKicks.set(false);
		MonumentaRedisSyncAPI.savePlayer(first);
		harness.tick();
		assertFalse(first.isPlaced(), "kicked again once kicks go through");
		assertEquals(30, harness.progress(harness.join("Tester")));
	}

	@Test
	void loadFromPlayerCopiesTheOtherPlayersSave(RedisSyncTestHarness harness) throws Exception {
		TestPlayer source = harness.join("Source");
		TestPlayer target = harness.join("Target");
		harness.setProgress(source, 40);
		harness.setProgress(target, 2);

		MonumentaRedisSyncAPI.savePlayer(source);
		MonumentaRedisSyncAPI.playerLoadFromPlayer(target, source, 0);
		harness.awaitWithTicks("target kicked to load the copy", () -> !target.isPlaced());

		assertEquals(40, harness.progress(harness.join("Target")));
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
