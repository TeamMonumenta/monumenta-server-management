package com.playmonumenta.redissync;

import com.google.gson.JsonObject;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI.RedisPlayerData;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import java.util.UUID;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The session lease (see {@link SessionLease}): one session in the domain may save a player's data.
 * A login takes it, waiting for the previous holder; the heartbeat keeps it; every save checks it;
 * the session releases it once its saves have committed.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class SessionLeaseTest {
	private static final String ELSEWHERE = "other_shard:0123456789abcdef";

	@Test
	void loginTakesTheLeaseAndLeavingReleasesIt(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		String holder = lease(harness, player.getUniqueId());
		assertNotNull(holder);
		assertTrue(holder.startsWith("test_shard:"), holder);

		harness.disconnect(player);
		harness.awaitWithTicks("lease released", () -> lease(harness, player.getUniqueId()) == null);
	}

	/** A login waits for the previous holder (another shard still saving the player) to release it */
	@Test
	void loginWaitsForTheLeaseToBeReleased(RedisSyncTestHarness harness) throws Exception {
		harness.savedAndLeft("Tester", 3);
		UUID uuid = harness.newPlayer("Tester").getUniqueId();
		harness.redisSet(SessionLease.key(uuid), ELSEWHERE, 60_000);
		Thread release = new Thread(() -> {
			try {
				Thread.sleep(RedisSyncTestHarness.ACQUIRE_WAIT_MILLIS / 4);
				harness.redisDelete(SessionLease.key(uuid));
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});
		release.start();
		TestPlayer player = harness.join("Tester");
		release.join();
		assertEquals(3, harness.progress(player));
	}

	@Test
	void loginIsRefusedWhileTheLeaseIsHeldElsewhere(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.newPlayer("Tester");
		harness.redisSet(SessionLease.key(player.getUniqueId()), ELSEWHERE, 60_000);
		assertNotEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(player));
		assertEquals(ELSEWHERE, lease(harness, player.getUniqueId()), "the holder keeps it");
	}

	/** A login refused by another plugin after taking the lease gives it back */
	@Test
	void loginRefusedAfterTakingTheLeaseReleasesIt(RedisSyncTestHarness harness) throws Exception {
		harness.onEvent(AsyncPlayerPreLoginEvent.class, EventPriority.HIGHEST, event -> event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, net.kyori.adventure.text.Component.text("no")));
		TestPlayer player = harness.newPlayer("Tester");
		assertNotEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(player));
		harness.awaitWithTicks("lease released", () -> lease(harness, player.getUniqueId()) == null);
	}

	/** A session whose lease another session holds writes nothing, and is kicked */
	@Test
	void savesAreFencedOnceAnotherSessionHoldsTheLease(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 5);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);

		harness.redisSet(SessionLease.key(player.getUniqueId()), ELSEWHERE, 60_000);
		harness.setProgress(player, 6);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitWithTicks("kicked", () -> !player.isPlaced());
		assertEquals(5, harness.savedProgress(player), "the fenced save wrote nothing");
		assertEquals(ELSEWHERE, lease(harness, player.getUniqueId()));
	}

	/** A lease that went missing (expired while the shard was slow, say) is taken again by its session's next save */
	@Test
	void saveRetakesAMissingLease(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		String token = lease(harness, player.getUniqueId());
		harness.redisDelete(SessionLease.key(player.getUniqueId()));
		harness.setProgress(player, 7);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(7, harness.savedProgress(player));
		assertEquals(token, lease(harness, player.getUniqueId()));
	}

	@Test
	void heartbeatRenewsTheLease(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		String key = SessionLease.key(player.getUniqueId());
		String token = lease(harness, player.getUniqueId());
		harness.redisSet(key, String.valueOf(token), 5_000);
		harness.tick((int) SessionLease.HEARTBEAT_TICKS);
		harness.awaitWithTicks("lease renewed", () -> pttl(harness, key) > 60_000);
	}

	@Test
	void heartbeatRetakesAMissingLease(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		String token = lease(harness, player.getUniqueId());
		harness.redisDelete(SessionLease.key(player.getUniqueId()));
		harness.tick((int) SessionLease.HEARTBEAT_TICKS);
		harness.awaitWithTicks("lease taken again", () -> String.valueOf(token).equals(lease(harness, player.getUniqueId())));
	}

	@Test
	void heartbeatKicksASessionWhoseLeaseIsHeldElsewhere(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.redisSet(SessionLease.key(player.getUniqueId()), ELSEWHERE, 60_000);
		harness.tick((int) SessionLease.HEARTBEAT_TICKS);
		harness.awaitWithTicks("kicked", () -> !player.isPlaced());
		assertEquals(ELSEWHERE, lease(harness, player.getUniqueId()));
	}

	/** An offline edit of a player who holds a lease (is online on some shard) is refused */
	@Test
	void offlineEditIsRefusedWhileTheLeaseIsHeld(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 3);
		harness.tick(2);
		UUID uuid = saved.getUniqueId();
		RedisPlayerData edit = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();
		((JsonObject) edit.getNbtTagCompoundData()).addProperty("XpLevel", 50);
		harness.redisSet(SessionLease.key(uuid), ELSEWHERE, 60_000);
		assertFalse(MonumentaRedisSyncAPI.saveOfflinePlayerData(edit).get());
	}

	@Test
	void shutdownReleasesLeases(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		String key = SessionLease.key(player.getUniqueId());
		assertNotNull(harness.redisGet(key));
		harness.disablePlugin();
		assertEquals(null, harness.redisGet(key));
	}

	private static @Nullable String lease(RedisSyncTestHarness harness, UUID uuid) {
		try {
			return harness.redisGet(SessionLease.key(uuid));
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}

	private static long pttl(RedisSyncTestHarness harness, String key) {
		try {
			return harness.redisPttl(key);
		} catch (Exception ex) {
			throw new IllegalStateException(ex);
		}
	}
}
