package com.playmonumenta.redissync;

import com.destroystokyo.paper.event.player.PlayerAdvancementDataLoadEvent;
import com.destroystokyo.paper.event.player.PlayerDataLoadEvent;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;
import net.kyori.adventure.text.Component;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A session covers one connection, from its first load event until its connection closes. Only
 * the session's own connection saves, and only between the end of its join and its disconnect.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class SessionLifecycleTest {
	private static final File UNUSED_PATH = new File("unused-in-tests");

	/* No ticks after the disconnect: a session ends exactly when its connection closes, not on a timer */
	@Test
	void sessionStateIsDroppedAsSoonAsTheConnectionCloses(RedisSyncTestHarness harness) throws Exception {
		harness.onEvent(PlayerSaveEvent.class, event -> event.setPluginData("TestPlugin", new JsonObject()));
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 37);
		MonumentaRedisSyncAPI.savePlayer(player);
		assertNotNull(MonumentaRedisSyncAPI.getPlayerPluginData(player.getUniqueId(), "TestPlugin"));

		harness.disconnect(player);

		assertNull(MonumentaRedisSyncAPI.getPlayerPluginData(player.getUniqueId(), "TestPlugin"), "plugin data");
		assertNull(PlayerSessions.getShardData(player.getUniqueId()), "shard data");
		assertFalse(harness.hasScores(player), "scores removed from the local scoreboard");
	}

	/**
	 * Other plugins load their state in their own join handlers, so a save before the join is over
	 * would save it empty. That includes a save from a MONITOR handler that runs after the plugin's.
	 */
	@Test
	void saveBeforeTheJoinIsOverIsSkipped(RedisSyncTestHarness harness) throws Exception {
		AtomicBoolean savedDuringJoin = new AtomicBoolean(false);
		harness.onEvent(PlayerJoinEvent.class, EventPriority.MONITOR, event -> {
			harness.getAdapter().savePlayer(event.getPlayer());
			savedDuringJoin.set(true);
		});
		TestPlayer player = harness.newPlayer("Tester");
		String dataPath = MonumentaRedisSyncAPI.getRedisDataPath(player);

		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.login(player));
		assertTrue(savedDuringJoin.get());
		harness.awaitPluginCommandsExecuted();
		assertEquals(0, harness.redisListLength(dataPath));

		harness.tick();
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		assertEquals(1, harness.redisListLength(dataPath), "saves from the next tick on");
	}

	/** A plugin holding a Player from an earlier connection must not save its stale state over the current one. */
	@Test
	void earlierConnectionCannotSaveOverTheCurrentOne(RedisSyncTestHarness harness) throws Exception {
		TestPlayer stale = harness.join("Tester");
		harness.setProgress(stale, 1);
		harness.disconnect(stale);
		TestPlayer current = harness.join("Tester");
		harness.setProgress(current, 2);
		MonumentaRedisSyncAPI.savePlayer(current);

		harness.getAdapter().savePlayer(stale);
		harness.awaitPluginCommandsExecuted();
		assertEquals(2, harness.savedProgress(current));
	}

	/**
	 * Once the session has ended there is nothing left to save; a late save (from a plugin's
	 * delayed task, say) must not write empty plugin data over the real thing. The session is kept
	 * while its final save commits, so the late save is made then.
	 */
	@Test
	void saveAfterTheSessionEndedIsRefused(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.pauseRedisWrites(1000);
		harness.disconnect(player);
		assertTrue(PlayerSessions.hasPendingSaves(player), "the final save is still committing");

		harness.getAdapter().savePlayer(player);
		harness.awaitSaved(player);
		harness.awaitPluginCommandsExecuted();
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisPluginDataPath(player)), "only the disconnect's save");
	}

	/**
	 * A kick removes the player at once and closes the connection a network tick later. A login in
	 * between finds the session still open, with its player already gone, and waits for its save.
	 */
	@Test
	void kickedPlayerCanComeStraightBack(RedisSyncTestHarness harness) throws Exception {
		TestPlayer first = harness.join("Tester");
		harness.setProgress(first, 9);

		harness.pauseRedisWrites(1000);
		first.kick(Component.text("test kick"));
		assertFalse(first.isPlaced());

		assertEquals(9, harness.progress(harness.join("Tester")), "the save made while being kicked is what loads");
	}

	/**
	 * The session starts at the advancement load, which Paper fires before PlayerLoginEvent. A login
	 * refused there (a ban, the whitelist) must still end that session, or every later login would
	 * wait on it and be refused.
	 */
	@Test
	void loginRefusedAtPlayerLoginEventDoesNotLockThePlayerOut(RedisSyncTestHarness harness) throws Exception {
		AtomicBoolean refuse = new AtomicBoolean(true);
		harness.onEvent(PlayerLoginEvent.class, event -> {
			if (refuse.get()) {
				event.disallow(PlayerLoginEvent.Result.KICK_BANNED, Component.text("banned"));
			}
		});

		assertNotEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.login(harness.newPlayer("Tester")));

		refuse.set(false);
		harness.join("Tester");
	}

	/**
	 * NPC plugins construct fake ServerPlayers, which fire the advancement load but have no
	 * connection to close. A session started for one would never end, and lock the real player out.
	 */
	@Test
	void loadEventsWithoutALoginDoNotStartASession(RedisSyncTestHarness harness) throws Exception {
		TestPlayer npc = harness.newPlayer("Tester");
		harness.callEvent(new PlayerAdvancementDataLoadEvent(npc, UNUSED_PATH));
		harness.callEvent(new PlayerDataLoadEvent(npc, UNUSED_PATH));

		assertNull(PlayerSessions.getShardData(npc.getUniqueId()));
		harness.join("Tester");
	}

	/**
	 * Only logins let through pre-login start a session. One that then drops before it loads fires
	 * no close event, so it is only expected for a while: a fake player with the same UUID turning
	 * up much later must not be taken for it.
	 */
	@Test
	void abandonedLoginIsNotPickedUpByALaterFakePlayer(RedisSyncTestHarness harness) throws Exception {
		PlayerSessions.setExpectedLoginTimeoutMillis(50);
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(harness.newPlayer("Tester")));
		Thread.sleep(100);

		TestPlayer npc = harness.newPlayer("Tester");
		harness.callEvent(new PlayerAdvancementDataLoadEvent(npc, UNUSED_PATH));
		harness.callEvent(new PlayerDataLoadEvent(npc, UNUSED_PATH));
		assertNull(PlayerSessions.getShardData(npc.getUniqueId()), "no session for the fake player");
		harness.join("Tester");
	}

	/** After a plugin reload the players online have no session, so nothing of theirs would be saved: they are sent to rejoin. */
	@Test
	void playersOnlineAcrossAPluginReloadAreSentToRejoin(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 4);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);

		harness.reloadPlugin();
		harness.tick();
		assertFalse(player.isPlaced(), "kicked");
		harness.tick();
		assertEquals(4, harness.progress(harness.join("Tester")));
	}
}
