package com.playmonumenta.redissync;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI.PlayerWorldData;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A saved spawn point is only applied in the world it is actually located in. */
public class PlayerWorldDataSpawnTest {
	@Test
	void spawnInOverworldMatchesMainWorld() {
		assertTrue(PlayerWorldData.spawnDimensionMatches("minecraft:overworld", "minecraft:overworld", "Project_Epic-valley"));
	}

	@Test
	void missingDimensionIsOverworld() {
		assertTrue(PlayerWorldData.spawnDimensionMatches(null, "minecraft:overworld", "Project_Epic-valley"));
		assertFalse(PlayerWorldData.spawnDimensionMatches(null, "minecraft:quests", "quests"));
	}

	@Test
	void overworldSpawnDoesNotMatchContentWorld() {
		assertFalse(PlayerWorldData.spawnDimensionMatches("minecraft:overworld", "minecraft:quests", "quests"));
	}

	@Test
	void dimensionMatchesByKeyOrWorldName() {
		assertTrue(PlayerWorldData.spawnDimensionMatches("minecraft:quests", "minecraft:quests", "quests"));
		assertTrue(PlayerWorldData.spawnDimensionMatches("quests", "minecraft:other", "quests"));
		assertFalse(PlayerWorldData.spawnDimensionMatches("mist1", "minecraft:mist2", "mist2"));
	}

	@Test
	void realSavedBlobs() {
		/* Values taken from a real player's sharddata hash */
		assertTrue(PlayerWorldData.spawnDimensionMatches("minecraft:gray13317", "minecraft:gray13317", "gray13317"));
		assertTrue(PlayerWorldData.spawnDimensionMatches("minecraft:plot1", "minecraft:plot1", "plot1"));
		assertTrue(PlayerWorldData.spawnDimensionMatches("minecraft:overworld", "minecraft:overworld", "Project_Epic-plots"));
		assertFalse(PlayerWorldData.spawnDimensionMatches("minecraft:gray13317", "minecraft:overworld", "Project_Epic-isles"));
		assertFalse(PlayerWorldData.spawnDimensionMatches("minecraft:gray13317", "minecraft:purple10438", "purple10438"));
		assertFalse(PlayerWorldData.spawnDimensionMatches("minecraft:overworld", "minecraft:plot1", "plot1"));
	}
}
