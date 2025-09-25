package com.playmonumenta.redissync.playerdata;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Task;
import com.google.common.collect.ImmutableMap;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.playmonumenta.redissync.RedisAPI;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.floweytf.coro.support.Awaitables.awaitable;

public record CachedPlayerData(
	byte[] playerData,
	ImmutableMap<String, JsonElement> pluginData,
	JsonObject scoreData,
	ImmutableMap<String, ShardData> shardData,
	String advancements
) {
	private static final Gson GSON = new Gson();

	private static JsonObject bytesToJson(byte[] data) {
		return GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data)), JsonObject.class);
	}

	private static <T> T bytesToJson(byte[] data, TypeToken<T> token) {
		return GSON.fromJson(new InputStreamReader(new ByteArrayInputStream(data)), token);
	}


	private static <T> byte[] jsonToBytes(T map) {
		return GSON.toJson(map).getBytes(StandardCharsets.UTF_8);
	}

	public CachedPlayerData withAdvancements(String advancements) {
		return new CachedPlayerData(playerData, pluginData, scoreData, shardData, advancements);
	}

	@Coroutine
	Task<CachedPlayerData> load(String namespace, UUID uuid, UUID blobId) {
		final var key = "%s:playerdata:%s:%s".formatted(namespace, uuid, blobId);
		final var result = Co.await(awaitable(RedisAPI.getInstance().asyncStringBytes().hgetall(key)));

		final var playerData = Objects.requireNonNull(result.get("player"));
		final var pluginData = Objects.requireNonNull(result.get("plugin"));
		final var scores = Objects.requireNonNull(result.get("scores"));
		final var shard = Objects.requireNonNull(result.get("shard"));
		final var advancements = Objects.requireNonNull(result.get("advancements"));

		// parse data
		return Co.ret(new CachedPlayerData(
			playerData,
			ImmutableMap.copyOf(bytesToJson(pluginData).asMap()),
			bytesToJson(scores),
			ImmutableMap.copyOf(bytesToJson(shard).asMap().entrySet().stream().collect(Collectors.toMap(
				Map.Entry::getKey,
				k -> (JsonObject) k.getValue()
			))),
			new String(advancements, StandardCharsets.UTF_8)
		));
	}

	@Coroutine
	Task<Void> store(String namespace, UUID uuid, UUID blobId) {
		final var key = "%s:playerdata:%s:%s".formatted(namespace, uuid, blobId);

		Co.await(
			awaitable(RedisAPI.getInstance().asyncStringBytes().hset(key, Map.of(
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
