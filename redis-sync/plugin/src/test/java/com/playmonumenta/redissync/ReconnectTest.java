package com.playmonumenta.redissync;

import com.playmonumenta.common.event.PlayerTransferFailEvent;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Connections for one account that follow each other closely, or overlap: a reconnect before the
 * last connection's final save has committed, or before this server has noticed it died, and
 * connections that drop part way through logging in.
 *
 * <p>The invariant throughout: whichever connection ends up in the game loads the newest state any
 * earlier connection saved, and no connection that did not finish loading ever saves.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class ReconnectTest {
	/**
	 * The final save on disconnect is only handed to redis, and can take 100ms or more to commit.
	 * A reconnect inside that window waits for it, rather than loading the save before, or being
	 * refused. Paper does not promise the close event on the main thread, so both are covered.
	 */
	@ParameterizedTest(name = "close event off the main thread: {0}")
	@ValueSource(booleans = {false, true})
	void reconnectWhileTheFinalSaveIsCommittingLoadsIt(boolean closeOffMainThread, RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("Tester");
		harness.setProgress(first, 5);
		MonumentaRedisSyncAPI.savePlayer(first);
		harness.awaitSaved(first);
		harness.setProgress(first, 7);

		/* What redis has committed when pre-login lets the reconnect through */
		AtomicReference<Integer> committedAtPreLogin = new AtomicReference<>();
		harness.onEvent(AsyncPlayerPreLoginEvent.class, EventPriority.MONITOR, event -> {
			try {
				committedAtPreLogin.set(harness.savedProgress(first));
			} catch (Exception ex) {
				throw new IllegalStateException(ex);
			}
		});

		harness.pauseRedisWrites(1000);
		if (closeOffMainThread) {
			harness.disconnectWithCloseOffMainThread(first);
		} else {
			harness.disconnect(first);
		}
		assertTrue(PlayerSessions.hasPendingSaves(first), "the final save is still in flight");

		assertEquals(7, harness.progress(harness.join("Tester")));
		assertEquals(7, committedAtPreLogin.get(), "the final save had committed before the login was let through");
	}

	/**
	 * The client's connection drops and it reconnects before this server has timed the old one out.
	 * The old connection is still in the game, with progress since its last save. As in vanilla the
	 * newest login wins, but only once the old connection has been closed and its final save has
	 * committed, so the new one loads that progress.
	 */
	@Test
	void reconnectWhileTheOldConnectionIsStillInTheGameTakesOverWithItsLatestState(RedisSyncTestHarness harness) throws Exception {
		TestPlayer old = harness.join("Tester");
		harness.setProgress(old, 5);
		MonumentaRedisSyncAPI.savePlayer(old);
		harness.awaitSaved(old);
		harness.setProgress(old, 7);

		harness.pauseRedisWrites(1000);
		TestPlayer reconnect = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.login(reconnect));
		assertFalse(old.isPlaced(), "the old connection was closed");
		assertEquals(7, harness.progress(reconnect), "loaded the old connection's final save");

		/* And the new connection saves normally */
		harness.tick();
		harness.setProgress(reconnect, 8);
		harness.disconnect(reconnect);
		assertEquals(8, harness.progress(harness.join("Tester")));
	}

	/**
	 * The old connection had not finished logging in: it is in the configuration phase, where it
	 * cannot be kicked. It must never save, and the new login waits for it to go away.
	 */
	@Test
	void reconnectWhileTheOldConnectionIsStillConfiguringWaitsForItAndItNeverSaves(RedisSyncTestHarness harness) throws Exception {
		harness.savedAndLeft("Tester", 3);
		TestPlayer old = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(old));
		assertTrue(harness.verifyLogin(old));

		/* The old connection finishes configuring while the new login waits */
		harness.runLater(10, () -> harness.placePlayer(old));
		TestPlayer reconnect = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.login(reconnect));

		assertFalse(old.isPlaced(), "the old connection was kicked as soon as it could be");
		assertEquals(3, harness.progress(reconnect));
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisDataPath(reconnect)), "the old connection saved nothing");
	}

	/**
	 * A client that goes away during the configuration phase was never placed, so Paper neither
	 * fires a quit nor saves. Its session must still end at once, or the next login would wait out
	 * the full timeout and be refused.
	 */
	@Test
	void disconnectDuringConfigurationDoesNotHoldUpTheNextLogin(RedisSyncTestHarness harness) throws Exception {
		harness.savedAndLeft("Tester", 3);
		TestPlayer dropped = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(dropped));
		assertTrue(harness.verifyLogin(dropped));
		harness.tick();
		harness.closeUnplacedConnection(dropped);

		long start = System.currentTimeMillis();
		TestPlayer next = harness.join("Tester");
		assertTrue(System.currentTimeMillis() - start < 5000, "login did not wait on the dropped connection");
		assertEquals(3, harness.progress(next));
	}

	/** A plugin kicks the player inside their join event, before they could have done anything worth saving. */
	@Test
	void playerKickedDuringTheirJoinSavesNothingAndCanComeStraightBack(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 3);
		AtomicBoolean kickOnJoin = new AtomicBoolean(true);
		harness.onEvent(PlayerJoinEvent.class, event -> {
			if (kickOnJoin.getAndSet(false)) {
				event.getPlayer().kick(Component.text("not now"));
			}
		});

		TestPlayer kicked = harness.newPlayer("Tester");
		harness.login(kicked);
		assertFalse(kicked.isPlaced());

		assertEquals(3, harness.progress(harness.join("Tester")));
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisDataPath(saved)));
	}

	/*
	 * Two connections for one account that both get through pre-login before either loads. Velocity
	 * never does this to one backend, but a proxy bug could. Pre-login cannot tell which later load
	 * belongs to which, and the close event carries only a UUID.
	 */

	/**
	 * The second to load is refused, and that must leave the first alone: its session, and a
	 * transfer it has started, since unlocking that would let it save over what the target shard loads.
	 */
	@Test
	void secondOverlappingLoginIsRefusedAndLeavesTheFirstAlone(RedisSyncTestHarness harness) throws Exception {
		AtomicInteger transferFailures = harness.countEvents(PlayerTransferFailEvent.class);
		TestPlayer first = harness.newPlayer("Tester");
		TestPlayer second = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(first));
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(second));
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.completeLogin(first));
		harness.tick();
		harness.setProgress(first, 6);
		MonumentaRedisSyncAPI.sendPlayer(first, "other_shard");

		assertNotEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.completeLogin(second));
		harness.tick(2);
		assertTrue(first.isPlaced(), "the first connection stays");
		assertTrue(MonumentaRedisSyncAPI.isPlayerTransferring(first), "and stays locked");
		assertEquals(0, transferFailures.get(), "no transfer failure reported");
		harness.awaitSaved(first);
		assertEquals(6, harness.savedProgress(first), "the first connection's transfer save landed");
	}

	/**
	 * The second is refused while the first is still configuring. It is not in the game yet, so the
	 * refused connection's close, by UUID alone, looks like the first one's own.
	 */
	@Test
	void secondOverlappingLoginRefusedWhileTheFirstConfiguresLeavesTheFirstAlone(RedisSyncTestHarness harness) throws Exception {
		harness.savedAndLeft("Tester", 3);
		TestPlayer first = harness.newPlayer("Tester");
		TestPlayer second = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(first));
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(second));
		assertTrue(harness.verifyLogin(first));
		assertFalse(harness.verifyLogin(second), "the second is refused");
		harness.tick();

		harness.placePlayer(first);
		harness.tick();
		assertTrue(first.isPlaced(), "the first joins");
		assertEquals(3, harness.progress(first));
		harness.setProgress(first, 4);
		harness.disconnect(first);
		assertEquals(4, harness.progress(harness.join("Tester")), "and saves");
	}

	/** The second drops before it loads anything. Its close must not end the first one's session, or nothing it did would be saved. */
	@Test
	void secondOverlappingLoginDroppingLeavesTheFirstSaving(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.newPlayer("Tester");
		TestPlayer second = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(first));
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(second));
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.completeLogin(first));
		harness.tick();
		harness.setProgress(first, 6);

		harness.closeUnplacedConnection(second);
		harness.tick();
		assertTrue(harness.hasScores(first), "the first connection's session did not end");

		harness.disconnect(first);
		assertEquals(6, harness.progress(harness.join("Tester")), "the first connection's final save landed");
	}

	/**
	 * As above, but the second drops while the first is still configuring (not in the game yet), so
	 * the close cannot be told apart from the first's own and ends the session. The first must then
	 * not be let in to play with nothing saved: it is refused, and the data is as it was.
	 */
	@Test
	void secondOverlappingLoginDroppingWhileTheFirstConfiguresRefusesTheFirst(RedisSyncTestHarness harness) throws Exception {
		harness.savedAndLeft("Tester", 3);
		TestPlayer first = harness.newPlayer("Tester");
		TestPlayer second = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(first));
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(second));
		assertTrue(harness.verifyLogin(first));
		harness.tick();

		harness.closeUnplacedConnection(second);
		harness.tick();
		harness.placePlayer(first);
		harness.tick(2);

		assertFalse(first.isPlaced(), "refused");
		assertEquals(3, harness.progress(harness.join("Tester")));
	}
}
