package com.playmonumenta.redissync.adapters;

import com.google.gson.JsonObject;
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

	JsonObject getPlayerScoresAsJson(String playerName, Scoreboard scoreboard);

	void resetPlayerScores(String playerName, Scoreboard scoreboard);

	Object retrieveSaveData(byte[] data, JsonObject shardData) throws IOException;

	SaveData extractSaveData(Object nbtObj, @Nullable ReturnParams returnParams) throws IOException;

	void savePlayer(Player player) throws Exception;

	Object upgradePlayerData(Object nbtTagCompound);

	String upgradePlayerAdvancements(String advancementsStr) throws Exception;
}
