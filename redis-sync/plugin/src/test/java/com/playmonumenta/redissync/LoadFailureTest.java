package com.playmonumenta.redissync;

import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A failed load fails closed: the player is not let play with whatever partial state they ended
 * up with, because saving that would overwrite their real data. Nor are they locked out: once
 * whatever broke is fixed, they can log in.
 */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class LoadFailureTest {
	@Test
	void playerdataThatFailsToLoadIsNeitherPlayedNorSavedOver(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 9);
		String dataPath = MonumentaRedisSyncAPI.getRedisDataPath(saved);
		String good = harness.redisListEntry(dataPath, 0);
		harness.redisSetListEntry(dataPath, 0, "not valid data");

		TestPlayer failed = harness.newPlayer("Tester");
		assertEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.login(failed));
		assertFalse(harness.getAdapter().getLiveData(failed).has("XpLevel"), "nothing loaded");
		harness.awaitWithTicks("player kicked", () -> !failed.isPlaced());
		harness.awaitPluginCommandsExecuted();
		assertEquals(1, harness.redisListLength(dataPath), "the kick's save did not push over the stored data");

		harness.redisSetListEntry(dataPath, 0, String.valueOf(good));
		assertEquals(9, harness.progress(harness.join("Tester")), "logs in once the data is repaired");
	}

	/**
	 * The advancement load runs before the player has a game connection, when a kick does nothing
	 * (CraftPlayer.kick returns early). So the login has to be refused outright.
	 */
	@Test
	void advancementsThatFailToLoadRefuseTheLogin(RedisSyncTestHarness harness) throws Exception {
		TestPlayer saved = harness.savedAndLeft("Tester", 9);
		String advancementsPath = MonumentaRedisSyncAPI.getRedisAdvancementsPath(saved);
		harness.redisRename(advancementsPath, advancementsPath + ".good");
		harness.redisBreakKey(advancementsPath);

		assertNotEquals(AsyncPlayerPreLoginEvent.Result.ALLOWED, harness.login(harness.newPlayer("Tester")));

		harness.redisRename(advancementsPath + ".good", advancementsPath);
		assertEquals(9, harness.progress(harness.join("Tester")), "logs in once the data is repaired");
	}

	/**
	 * A datapack reload reloads the advancements of every player online. If that fails, the player
	 * is left with none in memory, which must not be saved over the ones in redis.
	 */
	@Test
	void advancementsThatFailToReloadAreNotSavedOver(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 9);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.awaitSaved(player);
		String advancementsPath = MonumentaRedisSyncAPI.getRedisAdvancementsPath(player);
		harness.redisRename(advancementsPath, advancementsPath + ".good");
		harness.redisBreakKey(advancementsPath);

		harness.reloadDatapacks();
		harness.awaitWithTicks("player kicked", () -> !player.isPlaced());
		harness.awaitPluginCommandsExecuted();
		assertEquals(1, harness.redisListLength(MonumentaRedisSyncAPI.getRedisDataPath(player)), "nothing saved after the failed reload");

		harness.redisRename(advancementsPath + ".good", advancementsPath);
		assertEquals(9, harness.progress(harness.join("Tester")));
	}
}
