package com.playmonumenta.redissync;

import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisFuture;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RBoardAPI {

	public static String getRedisPath(String name) throws IllegalArgumentException {
		if (!name.matches("^[-_0-9A-Za-z$]+$")) {
			throw new IllegalArgumentException("Name '" + name + "' contains illegal characters, must match '^[-_$0-9A-Za-z$]+'");
		}
		return String.format("%s:rboard:%s", CommonConfig.getServerDomain(), name);
	}

	/* ******************* Set ******************* */
	public static CompletableFuture<Long> set(String name, Map<String, String> data) {
		final String redisPath;
		try {
			redisPath = getRedisPath(name);
		} catch (IllegalArgumentException ex) {
			CompletableFuture<Long> future = new CompletableFuture<>();
			future.completeExceptionally(ex);
			return future;
		}

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			return conn.hset(redisPath, data).toCompletableFuture();
		}
	}

	public static CompletableFuture<Long> set(String name, String key, long amount) {
		Map<String, String> data = new HashMap<>();
		data.put(key, Long.toString(amount));
		return set(name, data);
	}

	/* ******************* Add ******************* */
	public static CompletableFuture<Long> add(String name, String key, long amount) {
		final String redisPath;
		try {
			redisPath = getRedisPath(name);
		} catch (IllegalArgumentException ex) {
			CompletableFuture<Long> future = new CompletableFuture<>();
			future.completeExceptionally(ex);
			return future;
		}

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			return conn.hincrby(redisPath, key, amount).toCompletableFuture();
		}
	}

	/* ******************* Get ******************* */
	public static CompletableFuture<Map<String, String>> get(String name, String... keys) {
		final String redisPath;
		try {
			redisPath = getRedisPath(name);
		} catch (IllegalArgumentException ex) {
			CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
			future.completeExceptionally(ex);
			return future;
		}

		// Use single-element fixed-memory-location array to store values from lambda
		@SuppressWarnings("unchecked")
		final CompletableFuture<List<KeyValue<String, String>>>[] hmgetRef = new CompletableFuture[1];
		RedisAPI.multi(conn -> {
			for (String key : keys) {
				conn.hincrby(redisPath, key, 0);
			}
			hmgetRef[0] = conn.hmget(redisPath, keys).toCompletableFuture();
		});
		return hmgetRef[0].thenApply(list -> {
			Map<String, String> transformed = new LinkedHashMap<>();
			list.forEach(item -> transformed.put(item.getKey(), item.getValue()));
			return transformed;
		});
	}

	public static CompletableFuture<Long> getAsLong(String name, String key, long def) {
		return get(name, key).thenApply(data -> {
			String value = data.get(key);
			if (value != null) {
				/* Note this may throw a NumberFormatException, but caller already should catch exceptional completion */
				return Long.parseLong(value);
			}
			return def;
		});
	}

	/* ******************* GetAndReset ******************* */
	public static CompletableFuture<Map<String, String>> getAndReset(String name, String... keys) {
		final String redisPath;
		try {
			redisPath = getRedisPath(name);
		} catch (IllegalArgumentException ex) {
			CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
			future.completeExceptionally(ex);
			return future;
		}

		// Use single-element fixed-memory-location array to store values from lambda
		@SuppressWarnings("unchecked")
		final RedisFuture<List<KeyValue<String, String>>>[] hmgetRef = new RedisFuture[1];
		RedisAPI.multi(conn -> {
			hmgetRef[0] = conn.hmget(redisPath, keys);
			conn.hdel(redisPath, keys);
		});
		return hmgetRef[0].toCompletableFuture().thenApply(list -> {
			Map<String, String> transformed = new LinkedHashMap<>();
			list.forEach(item -> transformed.put(item.getKey(), item.getValue()));
			return transformed;
		});
	}

	/* ******************* GetKeys ******************* */
	public static CompletableFuture<List<String>> getKeys(String name) {
		final String redisPath;
		try {
			redisPath = getRedisPath(name);
		} catch (IllegalArgumentException ex) {
			CompletableFuture<List<String>> future = new CompletableFuture<>();
			future.completeExceptionally(ex);
			return future;
		}

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			return conn.hkeys(redisPath).toCompletableFuture();
		}
	}

	/* ******************* GetAll ******************* */
	public static CompletableFuture<Map<String, String>> getAll(String name) {
		final String redisPath;
		try {
			redisPath = getRedisPath(name);
		} catch (IllegalArgumentException ex) {
			CompletableFuture<Map<String, String>> future = new CompletableFuture<>();
			future.completeExceptionally(ex);
			return future;
		}

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			return conn.hgetall(redisPath).toCompletableFuture();
		}
	}

	/* ******************* Reset ******************* */
	public static CompletableFuture<Long> reset(String name, String... keys) {
		final String redisPath;
		try {
			redisPath = getRedisPath(name);
		} catch (IllegalArgumentException ex) {
			CompletableFuture<Long> future = new CompletableFuture<>();
			future.completeExceptionally(ex);
			return future;
		}

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			return conn.hdel(redisPath, keys).toCompletableFuture();
		}
	}

	/* ******************* ResetAll ******************* */
	public static CompletableFuture<Long> resetAll(String name) {
		final String redisPath;
		try {
			redisPath = getRedisPath(name);
		} catch (IllegalArgumentException ex) {
			CompletableFuture<Long> future = new CompletableFuture<>();
			future.completeExceptionally(ex);
			return future;
		}

		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			return conn.del(redisPath).toCompletableFuture();
		}
	}
}
