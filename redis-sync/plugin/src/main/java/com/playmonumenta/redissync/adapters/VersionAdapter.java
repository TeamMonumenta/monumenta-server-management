package com.playmonumenta.redissync.adapters;

import com.google.common.collect.ImmutableMap;
import com.playmonumenta.redissync.player.WorldData;
import java.io.IOException;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;

public interface VersionAdapter {
	record SaveData(String advancementData, byte[] nbt, WorldData extractedWorldData) {
	}

	ImmutableMap<String, Integer> getPlayerScores(String playerName, Scoreboard scoreboard);

	void resetPlayerScores(String playerName, Scoreboard scoreboard);

	Object retrieveSaveData(byte[] data, WorldData shardData) throws IOException;

	SaveData savePlayer(Player player);

	Object upgradePlayerData(Object nbtTagCompound);

	String upgradePlayerAdvancements(String advancementsStr) throws Exception;
}
