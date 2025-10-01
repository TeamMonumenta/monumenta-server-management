package com.playmonumenta.redissync.player;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Task;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.playmonumenta.redissync.utils.Util;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * @param playerData   Read-only view of player data
 * @param pluginData
 * @param scoreData
 * @param shardData
 * @param advancements
 */
public record PlayerData(
	byte[] playerData,
	ImmutableMap<String, JsonElement> pluginData,
	ImmutableMap<String, Integer> scoreData,
	ImmutableMap<String, ShardData> shardData,
	String advancements
) {
	private static JsonObject bytesToJson(byte[] data) {
		return PlayerDataManager.GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data)),
			JsonObject.class);
	}

	private static <T> T bytesToJson(byte[] data, TypeToken<T> token) {
		return PlayerDataManager.GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data)), token);
	}

	private static <T> byte[] jsonToBytes(T map) {
		return PlayerDataManager.GSON.toJson(map).getBytes(StandardCharsets.UTF_8);
	}

	public PlayerData withPlayerData(byte[] playerData) {
		return new PlayerData(playerData, pluginData, scoreData, shardData, advancements);
	}

	public PlayerData withAdvancements(String advancements) {
		return new PlayerData(playerData, pluginData, scoreData, shardData, advancements);
	}

	public PlayerData withScores(ImmutableMap<String, Integer> scoreData) {
		return new PlayerData(playerData, pluginData, scoreData, shardData, advancements);
	}

	public PlayerData withShardData(String shardName, ShardData shardData) {
		return new PlayerData(
			playerData, pluginData, scoreData,
			Util.extend(this.shardData, shardName, shardData),
			advancements
		);
	}

	@Coroutine
	static Task<PlayerData> load(RedisAsyncCommands<String, byte[]> redis, String ns, UUID uuid, UUID blobId) {
		final var key = "%s:playerdata:%s:%s".formatted(ns, uuid, blobId);
		final var result = Co.await(Awaitable.from(redis.hgetall(key)));

		final var playerData = Objects.requireNonNull(result.get("player"));
		final var pluginData = Objects.requireNonNull(result.get("plugin"));
		final var scores = Objects.requireNonNull(result.get("scores"));
		final var shard = Objects.requireNonNull(result.get("shard"));
		final var advancements = Objects.requireNonNull(result.get("advancements"));

		// parse data
		return Co.ret(new PlayerData(
			playerData,
			ImmutableMap.copyOf(bytesToJson(pluginData).asMap()),
			ImmutableMap.copyOf(bytesToJson(scores, new TypeToken<Map<String, Integer>>() {
			})),
			ImmutableMap.copyOf(bytesToJson(shard, new TypeToken<Map<String, ShardData>>() {
			})),
			new String(advancements, StandardCharsets.UTF_8)
		));
	}

	@Coroutine
	Task<Void> store(RedisAsyncCommands<String, byte[]> redis, String ns, UUID uuid, UUID blobId) {
		final var key = "%s:playerdata:%s:%s".formatted(ns, uuid, blobId);

		Co.await(
			Awaitable.from(redis.hset(key, Map.of(
				"player", playerData,
				"plugin", jsonToBytes(playerData),
				"scores", jsonToBytes(scoreData),
				"shard", jsonToBytes(shardData),
				"advancements", advancements.getBytes(StandardCharsets.UTF_8)
			)))
		);

		return Co.ret();
	}
}
