package com.playmonumenta.redissync;

import com.playmonumenta.redissync.config.CommonConfig;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.ScoredValue;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class LeaderboardAPI {

	/**
	 * Retrieve the leaderboard entries between the specified start and stop indices (inclusive)
	 *
	 * @param objective The leaderboard objective name (one leaderboard per objective)
	 * @param start Starting index to retrieve (inclusive)
	 * @param stop Ending index to retrieve (inclusive)
	 * @param ascending If true, leaderboard and results are smallest to largest and vice versa
	 */
	public static CompletableFuture<Map<String, Integer>> get(String objective, long start, long stop, boolean ascending) {
		RedisAPI api = RedisAPI.getInstance();
		final RedisFuture<List<ScoredValue<String>>> values;
		if (ascending) {
			values = api.async().zrangeWithScores(getRedisPath(objective), start, stop);
		} else {
			values = api.async().zrevrangeWithScores(getRedisPath(objective), start, stop);
		}

		return values.thenApply((scores) -> {
			LinkedHashMap<String, Integer> map = new LinkedHashMap<>();
			for (ScoredValue<String> value : scores) {
				map.put(value.getValue(), (int)value.getScore());
			}

			return (Map<String, Integer>)map;
		}).toCompletableFuture();
	}

	/**
	 * Updates the specified leaderboard with name/value.
	 * <p>
	 * Update is dispatched asynchronously, this method does not block or return success/failure
	 *
	 * @param objective The leaderboard objective name (one leaderboard per objective)
	 * @param name The name to associate with the value
	 * @param value Leaderboard value
	 */
	public static void updateAsync(String objective, String name, long value) {
		RedisAPI api = RedisAPI.getInstance();
		api.async().zadd(getRedisPath(objective), (double)value, name);
	}

	public static String getRedisPath(String objective) {
		return String.format("%s:leaderboard:%s", CommonConfig.getServerDomain(), objective);
	}
}
