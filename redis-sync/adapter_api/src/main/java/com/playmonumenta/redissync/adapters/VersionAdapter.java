package com.playmonumenta.redissync.adapters;

import com.google.common.collect.ImmutableMap;
import com.playmonumenta.redissync.player.PlayerPos;
import com.playmonumenta.redissync.player.WorldData;
import it.unimi.dsi.fastutil.Pair;
import java.io.IOException;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.jetbrains.annotations.Nullable;

public interface VersionAdapter {
	class ReturnParams {
		public final @Nullable Location mReturnLoc;
		public final @Nullable Float mReturnYaw;
		public final @Nullable Float mReturnPitch;

		public ReturnParams(@Nullable Location returnLoc, @Nullable Float returnYaw, @Nullable Float returnPitch) {
			mReturnLoc = returnLoc;
			mReturnYaw = returnYaw;
			mReturnPitch = returnPitch;
		}
	}

	class SaveData {
		private final byte[] mData;
		private final @Nullable String mShardData;

		protected SaveData(byte[] data, @Nullable String shardData) {
			mData = data;
			mShardData = shardData;
		}

		public byte[] getData() {
			return mData;
		}

		public @Nullable String getShardData() {
			return mShardData;
		}
	}

	ImmutableMap<String, Integer> getPlayerScores(String playerName, Scoreboard scoreboard);

	void resetPlayerScores(String playerName, Scoreboard scoreboard);

	Object retrieveSaveData(byte[] data, WorldData shardData) throws IOException;

	Pair<byte[], WorldData> extractSaveData(Object nbtObj) throws IOException;

	void savePlayer(Player player);

	Object upgradePlayerData(Object nbtTagCompound);

	String upgradePlayerAdvancements(String advancementsStr) throws Exception;
}
