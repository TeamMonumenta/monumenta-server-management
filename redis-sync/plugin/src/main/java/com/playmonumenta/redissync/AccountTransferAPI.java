package com.playmonumenta.redissync;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

public class AccountTransferAPI {
	/**
	 * Get a list of all account transfers for any accounts since the given time.
	 * Does not merge intermediate transfers.
	 * @param startTime The time to include for the first transfer
	 * @return A future list of account transfers
	 */
	public static CompletableFuture<List<AccountTransferDetails>> getAllTransfersSince(LocalDateTime startTime) {
		return getAllTransfersInRange(startTime, null);
	}

	/**
	 * Get a list of all account transfers for any accounts in the given time range.
	 * Does not merge intermediate transfers.
	 * @param startTime The time to include for the first transfer
	 * @param endTime   The time to exclude for the last transfer (or null for no limit)
	 * @return A future list of account transfers
	 */
	public static CompletableFuture<List<AccountTransferDetails>> getAllTransfersInRange(
		LocalDateTime startTime,
		@Nullable LocalDateTime endTime
	) {
		return AccountTransferManager.getTransfersInRange(startTime, endTime);
	}

	/**
	 * Get a list of all account transfers for any accounts since the given time.
	 * Merges intermediate transfers to get the effective start and end accounts
	 * @param startTime The time to include for the first transfer
	 * @return A future list of account transfers
	 */
	public static CompletableFuture<List<AccountTransferDetails>> getEffectiveTransfersSince(LocalDateTime startTime) {
		return getEffectiveTransfersInRange(startTime, null);
	}


	/**
	 * Get a list of all account transfers for any accounts since the given time.
	 * Merges intermediate transfers to get the effective start and end accounts
	 * @param startTime The time to include for the first transfer
	 * @param endTime   The time to exclude for the last transfer (or null for no limit)
	 * @return A future list of account transfers
	 */
	public static CompletableFuture<List<AccountTransferDetails>> getEffectiveTransfersInRange(
		LocalDateTime startTime,
		@Nullable LocalDateTime endTime
	) {
		CompletableFuture<List<AccountTransferDetails>> future = new CompletableFuture<>();

		Bukkit.getScheduler().runTaskAsynchronously(MonumentaRedisSync.getInstance(), () -> {
			try {
				List<AccountTransferDetails> allTransfers = getAllTransfersInRange(startTime, endTime).join();
				future.complete(AccountTransferManager.getEffectiveTransfersFromRange(allTransfers));
			} catch (Throwable throwable) {
				future.completeExceptionally(throwable);
			}
		});

		return future;
	}
}
