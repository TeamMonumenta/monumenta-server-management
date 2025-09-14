package com.playmonumenta.redissync;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.playmonumenta.redissync.event.PlayerAccountTransferEvent;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import io.lettuce.core.Range;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.NavigableSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class AccountTransferManager implements Listener {
	protected static final String PLUGIN_KEY = "MonumentaRedisSync";
	protected static final String REDIS_KEY = "account_transfer_log";
	protected static final LocalDateTime EPOCH = LocalDateTime.ofEpochSecond(0, 0, ZoneOffset.UTC);
	private static final int CACHE_EXPIRY_TICKS = 5 * 60 * 20;

	private static @Nullable AccountTransferManager INSTANCE = null;
	private static final NavigableSet<AccountTransferDetails> mTransferCache = new ConcurrentSkipListSet<>();
	private static final NavigableSet<TransferCacheRequest> mTransferCacheRequestExpiry = new ConcurrentSkipListSet<>();
	private static final NavigableSet<TransferCacheRequest> mTransferCacheLoadedExpiry = new ConcurrentSkipListSet<>();
	private static final NavigableMap<LocalDateTime, CompletableFuture<List<AccountTransferDetails>>> mPendingTransfers
		= new ConcurrentSkipListMap<>();
	private static @Nullable BukkitRunnable mTransferCacheExpirationRunnable = null;

	private AccountTransferManager() {
	}

	public static AccountTransferManager getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new AccountTransferManager();
		}
		return INSTANCE;
	}

	public static void onDisable() {
		if (mTransferCacheExpirationRunnable != null) {
			mTransferCacheExpirationRunnable.cancel();
			mTransferCacheExpirationRunnable = null;

			mTransferCache.clear();
			mTransferCacheRequestExpiry.clear();
			mTransferCacheLoadedExpiry.clear();
		}
		INSTANCE = null;
	}

	@EventHandler
	public void playerSaveEvent(PlayerSaveEvent event) {
		Player player = event.getPlayer();

		JsonObject redisSyncData = event.getPluginData().computeIfAbsent(PLUGIN_KEY, k -> new JsonObject());
		redisSyncData.addProperty("last_account_uuid", player.getUniqueId().toString());
		redisSyncData.addProperty("last_account_name", player.getName());

		event.setPluginData(PLUGIN_KEY, redisSyncData);
	}

	@EventHandler
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		UUID currentPlayerId = player.getUniqueId();
		JsonObject data = MonumentaRedisSyncAPI.getPlayerPluginData(currentPlayerId, PLUGIN_KEY);

		if (
			data == null
				|| !(data.get("last_account_uuid") instanceof JsonPrimitive lastAccountUuidPrimitive)
				|| !lastAccountUuidPrimitive.isString()
		) {
			return;
		}

		UUID lastAccountId;
		try {
			lastAccountId = UUID.fromString(lastAccountUuidPrimitive.getAsString());
		} catch (Throwable throwable) {
			MonumentaRedisSync.getInstance().getLogger().log(Level.WARNING, "[AccountTransferManager] Unable to get previous player account ID for " + player.getName() + "!", throwable);
			return;
		}

		if (currentPlayerId.equals(lastAccountId)) {
			return;
		}

		// Account transfer detected! Log and tell other plugins to fix up their data!

		String currentPlayerName = player.getName();
		String lastAccountName;

		if (
			data.get("last_account_name") instanceof JsonPrimitive lastAccountNamePrimitive
				&& lastAccountNamePrimitive.isString()
		) {
			lastAccountName = lastAccountNamePrimitive.getAsString();
		} else {
			lastAccountName = MonumentaRedisSyncAPI.cachedUuidToName(lastAccountId);
			if (lastAccountName == null) {
				lastAccountName = lastAccountId.toString();
			}
		}

		MonumentaRedisSync plugin = MonumentaRedisSync.getInstance();
		plugin.getLogger().info("[AccountTransferManager] Detected account transfer for " + lastAccountName + " (" + lastAccountId +") -> " + currentPlayerName + " (" + currentPlayerId + ")");

		LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
		long timestampMillis = EPOCH.until(now, ChronoUnit.MILLIS);

		// Alert local plugins

		PlayerAccountTransferEvent newEvent = new PlayerAccountTransferEvent(now, player, lastAccountId, lastAccountName);
		Bukkit.getPluginManager().callEvent(newEvent);

		// Alert remote plugins if possible

		NetworkRelayIntegration.broadcastPlayerAccountTransferEvent(timestampMillis, lastAccountId, lastAccountName, currentPlayerId, currentPlayerName);

		// Log to Redis

		JsonObject transferDetails = new JsonObject();
		transferDetails.addProperty("timestamp_millis", timestampMillis);
		transferDetails.addProperty("old_id", lastAccountId.toString());
		transferDetails.addProperty("old_name", lastAccountName);
		transferDetails.addProperty("new_id", currentPlayerId.toString());
		transferDetails.addProperty("new_name", currentPlayerName);

		RedisAPI.getInstance().async().zadd(REDIS_KEY, (double) timestampMillis, transferDetails.toString());
	}

	public static CompletableFuture<List<AccountTransferDetails>> getTransfersInRange(
		LocalDateTime startTime,
		@Nullable LocalDateTime endTime
	) {
		long startTimestampMillis = EPOCH.until(startTime, ChronoUnit.MILLIS);
		Range.Boundary<Long> startBound = Range.Boundary.including(startTimestampMillis);

		int currentTick = Bukkit.getCurrentTick();
		int expiryTick = currentTick + CACHE_EXPIRY_TICKS;

		CompletableFuture<List<AccountTransferDetails>> future = new CompletableFuture<>();
		MonumentaRedisSync plugin = MonumentaRedisSync.getInstance();

		LocalDateTime previousEarliestRequest = getEarliestRequestedTime(mTransferCacheRequestExpiry);

		registerCacheRequest(mTransferCacheRequestExpiry, expiryTick, startTime);
		if (previousEarliestRequest == null || startTime.isBefore(previousEarliestRequest)) {
			mPendingTransfers.put(startTime, future);
		}
		logCacheStats("Attempted to register entry in mTransferCacheRequestExpiry");

		startTransferCacheRunnable();

		Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
			try {
				// Wait for later requests to wrap up first
				if (previousEarliestRequest != null) {
					while (true) {
						Map.Entry<LocalDateTime, CompletableFuture<List<AccountTransferDetails>>> previousRequestEntry = mPendingTransfers.higherEntry(startTime);
						if (previousRequestEntry == null) {
							break;
						}

						try {
							previousRequestEntry.getValue().join();
						} catch (Throwable ignored) {
							// Not our problem; try the request again
						}
					}
				}

				LocalDateTime earliestLoadedRequest = getEarliestRequestedTime(mTransferCacheLoadedExpiry);

				Range.Boundary<Long> endBound;
				if (earliestLoadedRequest == null) {
					endBound = Range.Boundary.unbounded();
				} else {
					long timestampMillis = EPOCH.until(earliestLoadedRequest, ChronoUnit.MILLIS);
					endBound = Range.Boundary.excluding(timestampMillis);
				}

				// Fetch the list of transfers from Redis
				List<String> transferJsonStrList = RedisAPI.getInstance().async().zrangebyscore(REDIS_KEY, Range.from(
					startBound,
					endBound
				)).toCompletableFuture().join();

				// Parse them as-is
				Gson gson = new Gson();
				for (String transferJsonStr : transferJsonStrList) {
					JsonObject transferJson = gson.fromJson(transferJsonStr, JsonObject.class);
					mTransferCache.add(new AccountTransferDetails(transferJson));
				}

				// Return the results
				List<AccountTransferDetails> transfers = new ArrayList<>();
				for (AccountTransferDetails transfer : mTransferCache) {
					LocalDateTime transferTime = transfer.transferTime();
					if (transferTime.isBefore(startTime)) {
						continue;
					}

					if (endTime != null && transferTime.isBefore(endTime)) {
						break;
					}

					transfers.add(transfer);
				}
				future.complete(transfers);
				registerCacheRequest(mTransferCacheLoadedExpiry, expiryTick, startTime);
				mPendingTransfers.remove(startTime, future);
				logCacheStats("Attempted to register entry in mTransferCacheLoadedExpiry");
			} catch (Throwable throwable) {
				future.completeExceptionally(throwable);
				mPendingTransfers.remove(startTime, future);
			}
		});

		return future;
	}

	protected static void registerRemoteTransfer(AccountTransferDetails transferDetails) {
		// Add the remote transfer details to the local cache, and create a false request so it is properly cleaned up if it goes unused

		LocalDateTime startTime = transferDetails.transferTime();

		int currentTick = Bukkit.getCurrentTick();
		int expiryTick = currentTick + CACHE_EXPIRY_TICKS;

		registerCacheRequest(mTransferCacheRequestExpiry, expiryTick, startTime);
		startTransferCacheRunnable();

		mTransferCache.add(transferDetails);

		registerCacheRequest(mTransferCacheLoadedExpiry, expiryTick, startTime);
	}

	private static void startTransferCacheRunnable() {
		MonumentaRedisSync plugin = MonumentaRedisSync.getInstance();

		Bukkit.getScheduler().runTask(plugin, () -> {
			if (mTransferCacheExpirationRunnable != null) {
				return;
			}

			mTransferCacheExpirationRunnable = new BukkitRunnable() {
				@Override
				public void run() {
					if (mTransferCacheRequestExpiry.isEmpty() && mTransferCacheLoadedExpiry.isEmpty()) {
						plugin.getLogger().fine("[AccountTransferManager] Shutting down cache runnable due to lack of entries");
						mTransferCacheExpirationRunnable = null;
						cancel();
						return;
					}

					logCacheStats("pre-expiry check");

					int currentTick = Bukkit.getCurrentTick();
					removeExpiredCacheRequests(mTransferCacheLoadedExpiry, currentTick);
					removeExpiredCacheRequests(mTransferCacheRequestExpiry, currentTick);
					logCacheStats("pre-cleanup check");

					LocalDateTime earliestRequest = getEarliestRequestedTime(mTransferCacheRequestExpiry);

					Iterator<AccountTransferDetails> transferIt = mTransferCache.iterator();
					while (transferIt.hasNext()) {
						if (!mPendingTransfers.isEmpty()) {
							// Entries are loading, pause removal for now!
							plugin.getLogger().fine("[AccountTransferManager] Cache runnable pausing cleaning while transfers are in progress");
							return;
						}

						AccountTransferDetails oldTransfer = transferIt.next();
						if (earliestRequest == null || oldTransfer.transferTime().isBefore(earliestRequest)) {
							transferIt.remove();
						} else {
							break;
						}
					}
					logCacheStats("post-cleanup check");
				}
			};
			mTransferCacheExpirationRunnable.runTaskTimer(plugin, CACHE_EXPIRY_TICKS, 60 * 20L);
			plugin.getLogger().fine("[AccountTransferManager] Started cache runnable");
		});
	}

	public static List<AccountTransferDetails> getEffectiveTransfersFromRange(List<AccountTransferDetails> allTransfersInRange) {
		Map<UUID, AccountTransferDetails> mTransfersByNewId = new HashMap<>();

		for (AccountTransferDetails transferDetails : allTransfersInRange) {
			AccountTransferDetails oldTransfer = mTransfersByNewId.remove(transferDetails.oldId());
			if (oldTransfer == null) {
				mTransfersByNewId.put(transferDetails.newId(), transferDetails);
			} else {
				AccountTransferDetails mergedTransfer = new AccountTransferDetails(oldTransfer, transferDetails);
				mTransfersByNewId.put(mergedTransfer.newId(), mergedTransfer);
			}
		}

		return new ArrayList<>(mTransfersByNewId.values().stream()
			.filter(transfer -> !transfer.oldId().equals(transfer.newId()))
			.sorted().toList());
	}

	private record TransferCacheRequest(int mExpiryTick, LocalDateTime mRequestTime) implements Comparable<TransferCacheRequest> {
		@Override
		public int compareTo(@NotNull TransferCacheRequest o) {
			int result;

			result = Integer.compare(mExpiryTick, o.mExpiryTick);
			if (result != 0) {
				return result;
			}

			return mRequestTime.compareTo(o.mRequestTime);
		}
	}

	private static @Nullable LocalDateTime getEarliestRequestedTime(NavigableSet<TransferCacheRequest> cacheRequests) {
		LocalDateTime earliestRequest = null;
		for (TransferCacheRequest request : cacheRequests) {
			if (earliestRequest == null) {
				earliestRequest = request.mRequestTime;
				continue;
			}

			if (request.mRequestTime.isBefore(earliestRequest)) {
				earliestRequest = request.mRequestTime;
			}
		}
		return earliestRequest;
	}

	private static void registerCacheRequest(
		NavigableSet<TransferCacheRequest> cacheRequests,
		int expiryTick,
		LocalDateTime requestedTime
	) {
		TransferCacheRequest request = new TransferCacheRequest(expiryTick, requestedTime);
		if (!cacheRequests.add(request)) {
			return;
		}

		cacheRequests.removeIf(oldRequest
			-> oldRequest.mExpiryTick <= expiryTick && requestedTime.isBefore(oldRequest.mRequestTime));
	}

	private static void removeExpiredCacheRequests(NavigableSet<TransferCacheRequest> cacheRequests, int currentTick) {
		Iterator<TransferCacheRequest> it = cacheRequests.iterator();
		while (it.hasNext()) {
			TransferCacheRequest request = it.next();
			if (request.mExpiryTick > currentTick) {
				return;
			}
			it.remove();
		}
	}

	private static void logCacheStats(String label) {
		MonumentaRedisSync.getInstance().getLogger()
			.fine("[AccountTransferManager] Cache runnable stats (" + label + "): Cached=" + mTransferCache.size() + " Requested=" + mTransferCacheRequestExpiry.size() + ", Loaded=" + mTransferCacheLoadedExpiry.size() + ", PendingTransfers=" + mPendingTransfers.size());
	}
}
