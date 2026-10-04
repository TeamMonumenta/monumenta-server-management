package com.playmonumenta.redissync;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.playmonumenta.common.event.PlayerServerTransferEvent;
import com.playmonumenta.common.event.PlayerTransferFailEvent;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import java.util.concurrent.atomic.AtomicInteger;
import org.bukkit.Location;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transfers to another shard: save, lock the player so nothing saves after that, wait for the save
 * to commit, and only then tell the proxy to move them. The target shard loads from redis, so a
 * player moved sooner would arrive with the save before.
 *
 * <p>Network relay does not run in tests, so no shard counts as an online target and transfers use
 * the short {@link MonumentaRedisSyncAPI#OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS} lock timeout. Tests
 * that race a stalled redis against it advance ticks explicitly, so which one wins is part of the
 * test rather than down to how fast the machine is.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class TransferTest {
	private static final String TARGET = "other_shard";

	@Test
	void proxyIsToldToMoveThePlayerOnTheFirstTickAfterTheirSaveCommits(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger failures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 11);

		harness.pauseRedisWrites(1000);
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		harness.tick(5);
		assertTrue(player.getPluginMessages().isEmpty(), "no Connect before the save commits");

		harness.awaitSavedWithoutTicking(player);
		harness.tick();
		assertEquals(1, player.getPluginMessages().size(), "Connect on the first tick after the commit");
		assertTrue(player.getPluginMessages().get(0).contains(TARGET));
		assertTrue(MonumentaRedisSyncAPI.isPlayerTransferring(player), "still locked when the proxy is told");
		assertEquals(0, failures.get());
		assertEquals(11, harness.savedProgress(player));
	}

	/**
	 * If the save outlasts the transfer lock, the transfer has been reported failed and the player
	 * unlocked, and they may have saved again since. Moving them would hand the target older data.
	 */
	@Test
	void transferIsAbandonedIfTheLockTimesOutBeforeTheSaveCommits(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger failures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");

		harness.pauseRedisWrites(2000);
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		harness.tick(MonumentaRedisSyncAPI.OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS + 5);
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(player), "lock timed out");
		assertEquals(1, failures.get());

		harness.awaitSavedWithoutTicking(player);
		harness.tick(5);
		assertTrue(player.getPluginMessages().isEmpty(), "proxy never told to move them");
	}

	/** Once the transfer save is made the player's data belongs to the target; nothing here, the disconnect included, saves over it. */
	@Test
	void nothingIsSavedAfterTheTransferSave(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 11);
		startTransfer(harness, player);

		harness.setProgress(player, 12);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.disconnect(player);

		assertEquals(11, harness.progress(harness.join("Tester")));
	}

	/** A completed transfer ends with the connection closing, which must cancel the lock's timeout rather than report a failure. */
	@Test
	void completedTransferIsNotReportedAsFailed(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger failures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		startTransfer(harness, player);
		harness.disconnect(player);

		harness.tick(MonumentaRedisSyncAPI.OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS + 20);
		assertEquals(0, failures.get());
	}

	/** A transfer the proxy never carries out times out: the player is unlocked, told, and saves again. */
	@Test
	void transferThatNeverHappensTimesOutAndUnlocks(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger failures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		startTransfer(harness, player);

		harness.tick(MonumentaRedisSyncAPI.OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS);
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(player));
		assertEquals(1, failures.get());
		assertTrue(player.wasTold("Transferring timed out"));

		harness.setProgress(player, 12);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(12, harness.savedProgress(player), "saving resumes after the unlock");
	}

	/** A transfer that throws as it sends the player unlocks them at once, rather than when the lock times out. */
	@Test
	void transferThatThrowsUnlocksThePlayerAtOnce(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		player.failPluginMessages();
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		harness.awaitSavedWithoutTicking(player);
		harness.tick();
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(player));
	}

	/** A transfer another plugin cancels must not leave its return location on the player, or every later save would put them there. */
	@Test
	void cancelledTransferLeavesNoReturnLocationBehind(RedisSyncTestHarness harness) throws Exception {
		harness.onEvent(PlayerServerTransferEvent.class, event -> event.setCancelled(true));
		TestPlayer player = harness.join("Tester");
		JsonArray pos = new JsonArray();
		pos.add(5.0);
		pos.add(64.0);
		pos.add(5.0);
		harness.getAdapter().getLiveData(player).add("Pos", pos);

		MonumentaRedisSyncAPI.sendPlayer(player, TARGET, new Location(player.getWorld(), 1000, 64, 1000));
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(player));
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);

		String shardData = harness.redisHashGet(MonumentaRedisSyncAPI.getRedisPerShardDataPath(player),
			MonumentaRedisSyncAPI.getRedisPerShardDataWorldKey(player.getWorld()));
		assertEquals(pos, JsonParser.parseString(String.valueOf(shardData)).getAsJsonObject().get("Pos"), "saved where the player is");
	}

	/** A plugin holding a Player from an earlier connection cannot lock the current one, which would skip its saves. */
	@Test
	void transferThroughAnEarlierConnectionIsRefused(RedisSyncTestHarness harness) throws Exception {
		TestPlayer stale = harness.join("Tester");
		harness.disconnect(stale);
		TestPlayer current = harness.join("Tester");

		Exception refused = assertThrows(Exception.class, () -> MonumentaRedisSyncAPI.sendPlayer(stale, TARGET));
		assertTrue(String.valueOf(refused.getMessage()).contains("not this player's current connection"), refused.getMessage());
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(current));
	}

	/* Starts a transfer and waits until the proxy has been told to move the player */
	private static void startTransfer(RedisSyncTestHarness harness, TestPlayer player) throws Exception {
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		harness.awaitSavedWithoutTicking(player);
		harness.tick();
		assertEquals(1, player.getPluginMessages().size(), "Connect sent to the proxy");
	}
}
