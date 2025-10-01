package com.playmonumenta.redissync.player;

import com.google.gson.Gson;
import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.RedisAPI;
import it.unimi.dsi.fastutil.objects.Object2ObjectMaps;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;

public class PlayerDataManager {
	static final Gson GSON = new Gson();

	private final Map<UUID, LocalRedisPlayer> mOnlinePlayerCache = Object2ObjectMaps.synchronize(
		new Object2ObjectOpenHashMap<>()
	);
	private final RedisAPI mRedisApi;

	public PlayerDataManager(MonumentaRedisSync plugin) {
		Bukkit.getPluginManager().registerEvents(
			new PlayerDataListener(plugin.getLogger(), this, plugin),
			plugin
		);
		this.mRedisApi = plugin.getRedisApi();
	}

	/**
	 * @param uuid
	 */
	public void preloadPlayerData(UUID uuid) {
		if (mOnlinePlayerCache.containsKey(uuid)) {
			// TODO: BAD BAD BAD
			return;
		}

		mOnlinePlayerCache.put(uuid, new LocalRedisPlayer(uuid, mRedisApi.asyncStringBytes()));
	}

	public LocalRedisPlayer getLocalPlayerData(UUID uuid) {
		return mOnlinePlayerCache.get(uuid);
	}
}
