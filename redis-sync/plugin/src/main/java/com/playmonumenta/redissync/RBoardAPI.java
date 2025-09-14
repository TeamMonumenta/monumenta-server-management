package com.playmonumenta.redissync;

import com.playmonumenta.redissync.config.CommonConfig;
import io.lettuce.core.api.async.RedisAsyncCommands;
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

		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hset(redisPath, data).toCompletableFuture();
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

		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hincrby(redisPath, key, amount).toCompletableFuture();
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

		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		commands.multi();
		for (String key : keys) {
			commands.hincrby(redisPath, key, 0);
		}
		CompletableFuture<Map<String, String>> retVal = commands.hmget(redisPath, keys).toCompletableFuture().thenApply(list -> {
			Map<String, String> transformed = new LinkedHashMap<>();
			list.forEach(item -> transformed.put(item.getKey(), item.getValue()));
			return transformed;
		});
		commands.exec();
		return retVal;
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

		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		commands.multi();
		CompletableFuture<Map<String, String>> retVal = commands.hmget(redisPath, keys).toCompletableFuture().thenApply(list -> {
			Map<String, String> transformed = new LinkedHashMap<>();
			list.forEach(item -> transformed.put(item.getKey(), item.getValue()));
			return transformed;
		});
		commands.hdel(redisPath, keys).toCompletableFuture();
		commands.exec();
		return retVal;
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

		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hkeys(redisPath).toCompletableFuture();
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

		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hgetall(redisPath).toCompletableFuture();
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

		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.hdel(redisPath, keys).toCompletableFuture();
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

		RedisAsyncCommands<String, String> commands = RedisAPI.getInstance().async();
		return commands.del(redisPath).toCompletableFuture();
	}
}
