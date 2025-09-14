package com.playmonumenta.redissync;

import com.playmonumenta.redissync.config.CommonConfig;
import io.lettuce.core.KeyValue;
import io.lettuce.core.Value;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

public class RemoteDataAPI {
	/**
	 * Gets a specific remote data entry for a player.
	 * <p>
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return null if no entry was present, String otherwise
	 */
	public static CompletableFuture<String> get(UUID uuid, String key) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<String> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hget(getRedisPath(uuid), key).toCompletableFuture();
	}

	/**
	 * Gets multiple remote data entries for a player.
	 * <p>
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return null if no entry was present, String otherwise
	 */
	public static CompletableFuture<Map<String, String>> getMulti(UUID uuid, String... keys) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hmget(getRedisPath(uuid), keys).toCompletableFuture().thenApply((listResult) -> listResult.stream().filter(Value::hasValue).collect(Collectors.toMap(KeyValue::getKey, Value::getValue)));
	}

	/**
	 * Sets a specific remote data entry for a player.
	 * <p>
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return True if an entry was set, False otherwise
	 */
	public static CompletableFuture<Boolean> set(UUID uuid, String key, String value) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hset(getRedisPath(uuid), key, value).toCompletableFuture();
	}

	/**
	 * Atomically increments a specific remote data entry for a player.
	 * <p>
	 * Note that this will interpret the hash value as an integer (default 0 if not existing)
	 * <p>
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return Resulting value
	 */
	public static CompletableFuture<Long> increment(UUID uuid, String key, int incBy) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Long> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hincrby(getRedisPath(uuid), key, incBy).toCompletableFuture();
	}

	/**
	 * Deletes a specific key in the player's remote data.
	 * <p>
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return True if an entry was present and was deleted, False if no entry was present to begin with
	 */
	public static CompletableFuture<Boolean> del(UUID uuid, String key) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Boolean> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hdel(getRedisPath(uuid), key).thenApply((val) -> val == 1).toCompletableFuture();
	}

	/**
	 * Gets a map of all remote data for a player.
	 * <p>
	 * Will dispatch the task immediately async, making this suitable for use on main or async thread.
	 * WARNING: These complete async, if you need to run a sync task on completion you need to schedule it yourself (or wrap with runOnMainThreadWhenComplete)
	 *
	 * @return Non-null map of keys:values. If no data, will return an empty map
	 */
	public static CompletableFuture<Map<String, String>> getAll(UUID uuid) {
		RedisAPI api = RedisAPI.getInstance();
		if (api == null) {
			CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
			future.completeExceptionally(new Exception("MonumentaRedisSync is not loaded!"));
			return future;
		}

		return api.async().hgetall(getRedisPath(uuid)).toCompletableFuture();
	}

	public static String getRedisPath(UUID uuid) {
		return String.format("%s:playerdata:%s:remotedata", CommonConfig.getServerDomain(), uuid.toString());
	}
}
