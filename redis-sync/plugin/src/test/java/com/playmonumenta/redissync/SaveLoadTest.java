package com.playmonumenta.redissync;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** What is saved comes back, and every save is one aligned entry in the player's history. */
@ExtendWith(RedisSyncTestHarness.PerTest.class)
public class SaveLoadTest {
	@Test
	void everythingSavedComesBackOnTheNextLogin(RedisSyncTestHarness harness) throws Exception {
		/* Another plugin contributing data through the public save event */
		harness.onEvent(PlayerSaveEvent.class, event -> {
			JsonObject data = new JsonObject();
			data.addProperty("questProgress", 12);
			event.setPluginData("TestPlugin", data);
		});
		TestPlayer first = harness.join("Tester");
		harness.setProgress(first, 42);
		JsonObject nbt = harness.getAdapter().getLiveData(first);
		nbt.addProperty("Health", 17);
		JsonArray pos = position(5.5, 64, -3);
		nbt.add("Pos", pos);

		harness.disconnect(first);

		/* Disconnecting cleared the scores and cached data, so anything asserted below came back from redis */
		TestPlayer second = harness.join("Tester");
		assertEquals(42, harness.progress(second), "playerdata, scores and advancements");
		JsonObject loaded = harness.getAdapter().getLiveData(second);
		assertEquals(17, loaded.get("Health").getAsInt());
		assertEquals(pos, loaded.get("Pos"), "position, which is saved per shard rather than in the playerdata");
		JsonObject pluginData = MonumentaRedisSyncAPI.getPlayerPluginData(second.getUniqueId(), "TestPlugin");
		assertNotNull(pluginData, "plugin data");
		assertEquals(12, pluginData.get("questProgress").getAsInt());
	}

	/**
	 * Rollback, stash and loadFromPlayer read every history list at the same index. Nothing makes
	 * the lists line up but each save pushing exactly one entry onto each.
	 */
	@Test
	void everySavePushesOneAlignedEntryOntoEveryHistoryList(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		for (int i = 0; i < 3; i++) {
			harness.setProgress(player, i);
			MonumentaRedisSyncAPI.savePlayer(player);
		}
		harness.awaitSaved(player);

		assertEquals(List.of(2, 1, 0), harness.savedProgressHistory(player), "newest first");
		assertEquals(3, harness.redisListLength(MonumentaRedisSyncAPI.getRedisPluginDataPath(player)));
		assertEquals(3, harness.redisListLength(MonumentaRedisSyncAPI.getRedisHistoryPath(player)));
	}

	/**
	 * A datapack reload saves advancements on their own, outside a full save, then reloads them.
	 * That must neither lose the progress made since the last save nor push an advancements entry
	 * with nothing to pair with on the other lists.
	 */
	@Test
	void datapackReloadKeepsProgressAndHistoryAligned(RedisSyncTestHarness harness) throws Exception {
		TestPlayer player = harness.join("Tester");
		harness.setProgress(player, 1);
		MonumentaRedisSyncAPI.savePlayer(player);

		harness.setProgress(player, 2);
		harness.reloadDatapacks();
		assertEquals(2, harness.progress(player), "the reload loaded back the unsaved advancements");

		harness.setProgress(player, 3);
		MonumentaRedisSyncAPI.savePlayer(player);
		harness.reloadDatapacks();
		harness.awaitSaved(player);

		/* Each reload ends with a full save */
		assertEquals(List.of(3, 3, 2, 1), harness.savedProgressHistory(player));
	}

	private static JsonArray position(double x, double y, double z) {
		JsonArray pos = new JsonArray();
		pos.add(x);
		pos.add(y);
		pos.add(z);
		return pos;
	}
}
