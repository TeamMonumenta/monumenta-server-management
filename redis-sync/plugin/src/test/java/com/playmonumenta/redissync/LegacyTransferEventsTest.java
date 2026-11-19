package com.playmonumenta.redissync;

import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Scheduled for removal with the deprecated transfer events and isPlayerTransferring, which are
 * bridged only so plugins built against older versions still load and nominally work.
 */
@SuppressWarnings("removal")
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class LegacyTransferEventsTest {
	private static final String TARGET = "other_shard";

	@Test
	void requestFiresBothTransferEvents(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger redisSync = harness.countEvents(com.playmonumenta.redissync.event.PlayerServerTransferEvent.class);
		AtomicInteger common = harness.countEvents(com.playmonumenta.common.event.PlayerServerTransferEvent.class);
		TestPlayer player = harness.join("Tester");
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(player));

		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		assertEquals(1, redisSync.get());
		assertEquals(1, common.get());
		assertTrue(MonumentaRedisSyncAPI.isPlayerTransferring(player));
	}

	@Test
	void cancellingTheRedisSyncEventCancelsTheTransfer(RedisSyncTestHarness harness) throws Exception {
		harness.onEvent(com.playmonumenta.redissync.event.PlayerServerTransferEvent.class, event -> event.setCancelled(true));
		assertCancels(harness);
	}

	@Test
	void cancellingTheCommonEventCancelsTheTransfer(RedisSyncTestHarness harness) throws Exception {
		harness.onEvent(com.playmonumenta.common.event.PlayerServerTransferEvent.class, event -> event.setCancelled(true));
		assertCancels(harness);
	}

	@Test
	void timeoutFiresBothFailEvents(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger redisSync = harness.countEvents(com.playmonumenta.redissync.event.PlayerTransferFailEvent.class);
		AtomicInteger common = harness.countEvents(com.playmonumenta.common.event.PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);

		harness.tick(MonumentaRedisSyncAPI.OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS);
		assertEquals(1, redisSync.get());
		assertEquals(1, common.get());
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(player));
	}

	@Test
	void completedTransferFiresNoFailEvent(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger fails = harness.countEvents(com.playmonumenta.common.event.PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		harness.disconnect(player);

		harness.tick(MonumentaRedisSyncAPI.OFFLINE_TARGET_TRANSFER_TIMEOUT_TICKS + 20);
		assertEquals(0, fails.get());
	}

	@Test
	void requestThatThrowsFiresBothFailEvents(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger redisSync = harness.countEvents(com.playmonumenta.redissync.event.PlayerTransferFailEvent.class);
		AtomicInteger common = harness.countEvents(com.playmonumenta.common.event.PlayerTransferFailEvent.class);
		TestPlayer player = harness.join("Tester");
		player.failPluginMessages();

		assertThrows(RuntimeException.class, () -> MonumentaRedisSyncAPI.sendPlayer(player, TARGET));
		assertEquals(1, redisSync.get());
		assertEquals(1, common.get());
	}

	private static void assertCancels(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		MonumentaRedisSyncAPI.sendPlayer(player, TARGET);
		assertTrue(player.getPluginMessages().isEmpty(), "proxy never asked");
		assertFalse(MonumentaRedisSyncAPI.isPlayerTransferring(player));
	}
}
