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
 * Transfers to another shard. The shard only asks the proxy (BungeeCord "Connect"); the proxy takes
 * the player off this shard, which saves them as for any logout, and the target's login waits for
 * that save through the session lease. Nothing is locked meanwhile, so whatever the player does
 * until they leave is saved.
 *
 * <p>Network relay does not run in tests, so no shard counts as an online target and transfers use
 * the short {@link MonumentaRedisSyncAPI#OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS} timeout.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class TransferTest {
	private static final String TARGET = "other_shard";

	@Test
	void proxyIsAskedAtOnce(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger failures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		assertEquals(1, player.getPluginMessages().size(), "Connect sent straight away");
		assertTrue(player.getPluginMessages().get(0).contains(TARGET));
		assertTrue(MonumentaRedisSyncAPI.isPlayerTransferring(player));
		assertEquals(0, failures.get());
	}

	/** The save made as the player leaves is the one the next login gets, with whatever they did after the request */
	@Test
	void theLogoutSaveIsWhatTheTargetLoads(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 11);
		startTransfer(player);
		harness.setProgress(player, 12);
		harness.disconnect(player);
		assertEquals(12, harness.progress(harness.join("Tester")));
	}

	/** The return location rides in the logout save */
	@Test
	void returnLocationIsInTheLogoutSave(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET, new Location(player.getWorld(), 1000, 64, 1000));
		harness.disconnect(player);

		String shardData = harness.redisHashGet(MonumentaRedisSyncAPI.getRedisPerShardDataPath(player),
			MonumentaRedisSyncAPI.getRedisPerShardDataWorldKey(player.getWorld()));
		JsonArray pos = JsonParser.parseString(String.valueOf(shardData)).getAsJsonObject().getAsJsonArray("Pos");
		assertEquals(1000.0, pos.get(0).getAsDouble(), "saved at the return location");
		assertEquals(1000.0, pos.get(2).getAsDouble());
	}

	/** A completed transfer ends with the connection closing, which must cancel the timeout rather than report a failure */
	@Test
	void completedTransferIsNotReportedAsFailed(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger failures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		startTransfer(player);
		harness.disconnect(player);

		harness.tick(MonumentaRedisSyncAPI.OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS + 20);
		assertEquals(0, failures.get());
	}

	/** A transfer the proxy never carries out times out: the player is told, and the return location is dropped */
	@Test
	void transferThatNeverHappensTimesOut(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger failures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		JsonArray pos = new JsonArray();
		pos.add(5.0);
		pos.add(64.0);
		pos.add(5.0);
		harness.getAdapter().getLiveData(player).add("Pos", pos);
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET, new Location(player.getWorld(), 1000, 64, 1000));

		harness.tick(MonumentaRedisSyncAPI.OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS);
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(player));
		assertEquals(1, failures.get());
		assertTrue(player.wasTold("Transferring timed out"));

		harness.setProgress(player, 12);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(12, harness.savedProgress(player));
		String shardData = harness.redisHashGet(MonumentaRedisSyncAPI.getRedisPerShardDataPath(player),
			MonumentaRedisSyncAPI.getRedisPerShardDataWorldKey(player.getWorld()));
		assertEquals(pos, JsonParser.parseString(String.valueOf(shardData)).getAsJsonObject().get("Pos"), "saved where the player is");
	}

	/**
	 * The proxy acts just as the timeout runs out: the connection has closed, but the server has not
	 * processed the quit yet. That is not a failure, and the logout save still carries the return location.
	 */
	@Test
	void timeoutWhileTheConnectionIsClosingIsNotAFailure(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger failures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET, new Location(player.getWorld(), 1000, 64, 1000));
		player.loseConnection();
		harness.tick(MonumentaRedisSyncAPI.OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS + 5);
		assertEquals(0, failures.get());
		assertTrue(MonumentaRedisSyncAPI.isPlayerTransferring(player));

		harness.disconnect(player);
		String shardData = harness.redisHashGet(MonumentaRedisSyncAPI.getRedisPerShardDataPath(player),
			MonumentaRedisSyncAPI.getRedisPerShardDataWorldKey(player.getWorld()));
		JsonArray pos = JsonParser.parseString(String.valueOf(shardData)).getAsJsonObject().getAsJsonArray("Pos");
		assertEquals(1000.0, pos.get(0).getAsDouble(), "saved at the return location");
	}

	/** A second request while one is pending does nothing */
	@Test
	void secondRequestIsIgnored(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger transfers = harness.countEvents(PlayerServerTransferEvent.class);
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		MonumentaRedisSyncAPI.sendPlayer(player, "third_shard");
		assertEquals(1, transfers.get());
		assertEquals(1, player.getPluginMessages().size());
	}

	/** A transfer that throws as it asks the proxy is not left pending */
	@Test
	void transferThatThrowsIsNotLeftPending(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		player.failPluginMessages();
		assertThrows(RuntimeException.class, () -> MonumentaRedisSyncAPI.sendPlayer(player, TARGET));
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

	/** A plugin holding a Player from an earlier connection cannot transfer the current one, and no transfer events fire */
	@Test
	void transferThroughAnEarlierConnectionDoesNothing(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger transfers = harness.countEvents(PlayerServerTransferEvent.class);
		TestPlayer stale = harness.join("Tester");
		harness.disconnect(stale);
		TestPlayer current = harness.join("Tester");

		MonumentaRedisSyncAPI.sendPlayer(stale, TARGET);
		harness.tick(5);
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(current));
		assertEquals(0, transfers.get());
		assertTrue(stale.getPluginMessages().isEmpty(), "proxy never told to move the earlier connection");
		assertTrue(current.getPluginMessages().isEmpty(), "proxy never told to move the current connection");
	}

	/** A task scheduled before the connection closed finds nothing to transfer */
	@Test
	void transferAfterTheConnectionClosedDoesNothing(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger transfers = harness.countEvents(PlayerServerTransferEvent.class);
		TestPlayer player = harness.join("Tester");
		harness.disconnect(player);

		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		harness.tick(5);
		assertEquals(0, transfers.get());
		assertTrue(player.getPluginMessages().isEmpty(), "proxy never told to move them");
	}

	/** Out of the game but not yet closed, the session is still open, yet there is no one left to move and the final save is already made */
	@Test
	void transferBetweenQuitAndConnectionCloseDoesNothing(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger transfers = harness.countEvents(PlayerServerTransferEvent.class);
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 11);
		harness.leaveGame(player);
		harness.setProgress(player, 12);

		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(player));
		harness.closeConnection(player);
		harness.tick(5);
		assertEquals(0, transfers.get());
		assertTrue(player.getPluginMessages().isEmpty(), "proxy never told to move them");
		assertEquals(11, harness.progress(harness.join("Tester")), "the final save is what the next login gets");
	}

	private static void startTransfer(TestPlayer player) throws Exception {
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		assertEquals(1, player.getPluginMessages().size(), "Connect sent to the proxy");
	}
}
