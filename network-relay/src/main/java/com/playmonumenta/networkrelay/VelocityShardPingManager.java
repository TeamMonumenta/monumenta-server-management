package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import com.velocitypowered.api.proxy.server.PingOptions;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import org.jetbrains.annotations.Nullable;

public class VelocityShardPingManager {
	public static final Duration UPDATE_INTERVAL = Duration.of(1, ChronoUnit.MINUTES);
	public static final Duration PING_TIMEOUT = Duration.of(1, ChronoUnit.SECONDS);

	private static Map<String, @Nullable Duration> mShardPings = new HashMap<>();

	public static @Nullable Duration getShardPing(String shard) {
		return mShardPings.get(shard);
	}

	public static JsonObject getShardPingsJson() {
		JsonObject result = new JsonObject();
		for (Map.Entry<String, @Nullable Duration> shardPingEntry : mShardPings.entrySet()) {
			String shardName = shardPingEntry.getKey();
			Duration ping = shardPingEntry.getValue();
			if (ping == null) {
				result.add(shardName, null);
			} else {
				result.addProperty(shardName, ping.toMillis());
			}
		}
		return result;
	}

	protected static void schedulePingUpdates(NetworkRelayVelocity plugin) {
		PingOptions pingOptions = PingOptions.builder()
			.timeout(PING_TIMEOUT)
			.build();

		plugin.mServer.getScheduler().buildTask(plugin, () -> {
				Collection<RegisteredServer> allShards = plugin.mServer.getAllServers();
				int numShards = allShards.size();

				Duration loadBalancePingDelayIncrement = UPDATE_INTERVAL.minus(PING_TIMEOUT).dividedBy(numShards);
				List<CompletableFuture<NamedPingData>> pingFutures = new ArrayList<>();
				int shardNum = 0;
				for (RegisteredServer shard : allShards) {
					CompletableFuture<NamedPingData> shardPingFuture = new CompletableFuture<>();

					plugin.mServer.getScheduler().buildTask(plugin, () -> {
							String shardName = shard.getServerInfo().getName();
							Instant startTime = Instant.now();
							try {
								shard.ping(pingOptions).join();
								Instant stopTime = Instant.now();
								shardPingFuture.complete(new NamedPingData(shardName, Duration.between(startTime, stopTime)));
							} catch (CompletionException | CancellationException ignored) {
								shardPingFuture.complete(new NamedPingData(shardName, null));
							} catch (Throwable throwable) {
								String message = "Unexpected exception getting ping for "
									+ shard.getServerInfo().getName() + " shard";
								plugin.mLogger.error(message, throwable);
								shardPingFuture.complete(new NamedPingData(shardName, null));
							}
						})
						.delay(loadBalancePingDelayIncrement.multipliedBy(shardNum))
						.schedule();

					pingFutures.add(shardPingFuture);
					shardNum++;
				}

				Map<String, @Nullable Duration> result = new HashMap<>();
				for (CompletableFuture<NamedPingData> shardPingFuture : pingFutures) {
					NamedPingData pingData = shardPingFuture.join();
					result.put(pingData.shard(), pingData.pingDuration());
				}

				mShardPings = result;
			})
			.repeat(UPDATE_INTERVAL)
			.schedule();
	}

	private record NamedPingData(String shard, @Nullable Duration pingDuration) {
	}
}
