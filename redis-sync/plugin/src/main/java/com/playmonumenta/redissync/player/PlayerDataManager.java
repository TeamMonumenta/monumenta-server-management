package com.playmonumenta.redissync.player;

import com.google.gson.Gson;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {
	static final Gson GSON = new Gson();


	private final Map<UUID, LocalRedisPlayer> mOnlinePlayerCache = Object2ObjectMaps.synchronize(
		new Object2ObjectOpenHashMap<>()
	);

	/**
	 *
	 * @param uuid
	 */
	public void preloadPlayerData(UUID uuid) {
		if (mOnlinePlayerCache.containsKey(uuid)) {
			// TODO: BAD BAD BAD
		}

		mOnlinePlayerCache.put(uuid, new LocalRedisPlayer(uuid));
	}

	public LocalRedisPlayer getLocalPlayerData(UUID uuid) {
		return mOnlinePlayerCache.get(uuid);
	}
}
