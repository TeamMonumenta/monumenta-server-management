package com.playmonumenta.redissync.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.playmonumenta.redissync.utils.MMLog;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

public class StatsDataManager {
	private final Map<UUID, PlayerStats> mPlayerStats = new HashMap<>();

	public @Nullable String load(UUID uuid, @Nullable String storedJson) {
		if (storedJson == null || storedJson.isBlank()) {
			return null;
		}

		JsonObject root;
		try {
			root = JsonParser.parseString(storedJson).getAsJsonObject();
		} catch (Exception e) {
			MMLog.warning("Failed to parse data for UUID " + uuid, e);
			return null;
		}

		PlayerStats ps = PlayerStats.fromJson(uuid, root);

		if (ps == null) {
			return null;
		}


		mPlayerStats.put(uuid, ps);

		return root.toString();
	}

	public String save(UUID uuid, String savedJson) {
		PlayerStats ps = mPlayerStats.get(uuid);
		if (ps == null) {
			return savedJson;
		}

		JsonObject root;
		try {
			root = JsonParser.parseString(savedJson).getAsJsonObject();
		} catch (Exception e) {
			MMLog.warning("Failed to save data for UUID " + uuid, e);
			return savedJson;
		}

		ps.updateSavedValue(root);

		return root.toString();
	}

	public @Nullable PlayerStats getPlayerStats(UUID uuid) {
		return mPlayerStats.get(uuid);
	} 

	public void remove(UUID uuid) {
		mPlayerStats.remove(uuid);
	}
}
