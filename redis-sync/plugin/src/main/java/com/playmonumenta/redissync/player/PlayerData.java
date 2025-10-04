package com.playmonumenta.redissync.player;

import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import com.playmonumenta.redissync.utils.Util;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Objects;

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

	// internal methods for loading/storing data to redis

	static PlayerData fromRedisData(Map<String, byte[]> data) {
		final var playerData = Objects.requireNonNull(data.get("player"));
		final var pluginData = Objects.requireNonNull(data.get("plugin"));
		final var scores = Objects.requireNonNull(data.get("scores"));
		final var shard = Objects.requireNonNull(data.get("shard"));
		final var advancements = Objects.requireNonNull(data.get("advancements"));

		// parse data
		return new PlayerData(
			playerData,
			ImmutableMap.copyOf(bytesToJson(pluginData).asMap()),
			ImmutableMap.copyOf(bytesToJson(scores, new TypeToken<Map<String, Integer>>() {
			})),
			ImmutableMap.copyOf(bytesToJson(shard, new TypeToken<Map<String, ShardData>>() {
			})),
			new String(advancements, StandardCharsets.UTF_8)
		);
	}

	Map<String, byte[]> toRedisData() {
		return Map.of(
			"player", playerData,
			"plugin", jsonToBytes(playerData),
			"scores", jsonToBytes(scoreData),
			"shard", jsonToBytes(shardData),
			"advancements", advancements.getBytes(StandardCharsets.UTF_8)
		);
	}
}
