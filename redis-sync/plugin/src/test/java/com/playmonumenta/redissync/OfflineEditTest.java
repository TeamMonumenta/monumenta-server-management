package com.playmonumenta.redissync;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.playmonumenta.redissync.MonumentaRedisSyncAPI.RedisPlayerData;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import java.util.List;
import java.util.UUID;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Offline edits read a player's data ({@code getOfflinePlayerData}), change it, and write it back
 * ({@code saveOfflinePlayerData}). The write is refused if it would bury progress the player has
 * saved since the read, or if the player is here to save over it.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class OfflineEditTest {
	@Test
	void offlineEditOfDataThePlayerHasSinceSavedOverIsRefused(RedisSyncTestHarness harness) throws Exception {
		UUID uuid = savedPlayerWithProgress(harness, 3);
		RedisPlayerData edit = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();

		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 8);
		harness.disconnect(player);
		harness.awaitSaved(player);

		setProgress(edit, 50);
		assertFalse(MonumentaRedisSyncAPI.saveOfflinePlayerData(edit).get());
		assertEquals(8, harness.progress(harness.join("Tester")));
	}

	/** Two edits from the same read: the first lands, and the second would bury it. */
	@Test
	void onlyTheFirstOfTwoOfflineEditsFromOneReadLands(RedisSyncTestHarness harness) throws Exception {
		UUID uuid = savedPlayerWithProgress(harness, 3);
		RedisPlayerData editA = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();
		RedisPlayerData editB = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();
		setProgress(editA, 50);
		setProgress(editB, 60);

		assertTrue(MonumentaRedisSyncAPI.saveOfflinePlayerData(editA).get());
		assertFalse(MonumentaRedisSyncAPI.saveOfflinePlayerData(editB).get());
		assertEquals(50, harness.progress(harness.join("Tester")));
	}

	@Test
	void offlineEditOfAPlayerWhoIsHereIsRefused(RedisSyncTestHarness harness) throws Exception {
		UUID uuid = savedPlayerWithProgress(harness, 3);
		RedisPlayerData edit = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();
		TestPlayer player = harness.join("Tester");

		setProgress(edit, 50);
		assertFalse(MonumentaRedisSyncAPI.saveOfflinePlayerData(edit).get());
		harness.disconnect(player);
		harness.awaitSaved(player);
		assertEquals(List.of(3, 3), harness.savedProgressHistory(player), "only the player's own saves");
	}

	/** As /monumenta redissync upgradeallplayers does: an edit that records its own history entry. */
	@Test
	void offlineEditWithItsOwnHistoryEntryLands(RedisSyncTestHarness harness) throws Exception {
		UUID uuid = savedPlayerWithProgress(harness, 3);
		RedisPlayerData edit = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();
		setProgress(edit, 78);
		String history = "VERSION_UPGRADE|" + System.currentTimeMillis() + "|" + uuid;
		edit.setHistory(history);

		assertTrue(MonumentaRedisSyncAPI.saveOfflinePlayerData(edit).get());
		TestPlayer player = harness.join("Tester");
		assertEquals(78, harness.progress(player));
		assertEquals(history, harness.redisListEntry(MonumentaRedisSyncAPI.getRedisHistoryPath(player), 0));
	}

	/** A login that arrives while an offline edit is committing waits for it, and loads it. */
	@Test
	void loginDuringAnOfflineEditLoadsTheEdit(RedisSyncTestHarness harness) throws Exception {
		UUID uuid = savedPlayerWithProgress(harness, 3);
		RedisPlayerData edit = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();
		setProgress(edit, 50);

		harness.pauseRedisWrites(1000);
		MonumentaRedisSyncAPI.saveOfflinePlayerData(edit);
		assertEquals(50, harness.progress(harness.join("Tester")));
	}

	/**
	 * A login let through pre-login may still load, so offline edits are refused until it does, or
	 * until it would have expired, as one that drops before loading leaves nothing else behind.
	 */
	@Test
	void abandonedLoginHoldsUpOfflineEditsOnlyUntilItExpires(RedisSyncTestHarness harness) throws Exception {
		UUID uuid = savedPlayerWithProgress(harness, 3);
		PlayerSessions.setExpectedLoginTimeoutMillis(2000);
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.preLogin(harness.newPlayer("Tester")));
		RedisPlayerData edit = MonumentaRedisSyncAPI.getOfflinePlayerData(uuid).get();
		setProgress(edit, 66);
		assertFalse(MonumentaRedisSyncAPI.saveOfflinePlayerData(edit).get(), "refused while the login may still load");

		Thread.sleep(2500);
		assertTrue(MonumentaRedisSyncAPI.saveOfflinePlayerData(edit).get(), "allowed once it has expired");
		assertEquals(66, harness.progress(harness.join("Tester")));
	}

	/* A player who logged in, made the given progress, and left; returns their UUID */
	private static UUID savedPlayerWithProgress(RedisSyncTestHarness harness, int progress) throws Exception {
		TestPlayer player = harness.savedAndLeft("Tester", progress);
		/* Past the ended session's removal, which follows its last save committing */
		harness.tick(2);
		return player.getUniqueId();
	}

	/* Sets the edit's progress everywhere the harness looks for it, as RedisSyncTestHarness.setProgress does for a live player */
	private static void setProgress(RedisPlayerData edit, int value) {
		((JsonObject) edit.getNbtTagCompoundData()).addProperty("XpLevel", value);
		JsonObject scores = JsonParser.parseString(edit.getScores()).getAsJsonObject();
		scores.addProperty("Progress", value);
		edit.setScores(scores.toString());
		edit.setAdvancements("{\"progress\":" + value + "}");
	}
}
