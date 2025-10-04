package com.playmonumenta.redissync.player;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Task;
import com.google.common.base.Preconditions;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the local, cached info for a specific player that is online or in the process of joining the server.
 * <p>
 * This class handles most of the actual logic for storing/loading player data from redis, as well as logic required
 * to transfer the player to various shards.
 * </p>
 */
public class LocalRedisPlayer {
	public record HistoryEntry(UUID id, HistoryMetaData metaData) {
	}

	private final UUID mPlayerUuid;

	// global player data
	private String mCurrentProfile;
	private final Map<String, UUID> mStashes;
	private final Map<String, UUID> mProfiles;
	private final List<HistoryEntry> mHistory;

	private PlayerData mActivePlayerData;

	private boolean mDisableSaving;

	private final RedisHandler mRedisHandler;

	private boolean mHasQueuedTransfer;
	private boolean mHasDisconnected = false;

	private @Nullable Task<Void> postDisconnectOps;

	private LocalRedisPlayer(UUID uuid, RedisHandler redisHandler, PlayerGlobalData metadata, PlayerData activeData) {
		this.mPlayerUuid = uuid;
		this.mRedisHandler = redisHandler;
		this.mCurrentProfile = metadata.currentProfile();
		this.mStashes = metadata.stashes();
		this.mProfiles = metadata.profiles();
		this.mHistory = metadata.history();
		this.mActivePlayerData = activeData;
	}

	@Coroutine
	static Task<LocalRedisPlayer> fetch(UUID uuid, RedisHandler redisHandler) {
		Co.await(redisHandler.syncRedis());

		final var commands = redisHandler.commands();

		commands.multi();
		final var metadata = commands.get(redisHandler.playerMetaDataKey(uuid));
		final var activeData = commands.hgetall(redisHandler.playerActiveDataKey(uuid));
		Co.await(Awaitable.from(commands.exec()));

		// the join() calls should not block, since we already awaited for the multi/exec
		return Co.ret(new LocalRedisPlayer(
			uuid,
			redisHandler,
			PlayerDataManager.GSON.fromJson(
				new String(metadata.toCompletableFuture().join()),
				PlayerGlobalData.class
			),
			PlayerData.fromRedisData(activeData.toCompletableFuture().join())
		));
	}

	// getters

	public void disableSaving() {
		mDisableSaving = true;
	}

	public boolean isSavingDisabled() {
		return mDisableSaving;
	}

	public PlayerData currentPlayerData() {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		return mActivePlayerData;
	}

	void onDisconnect() {
		mHasDisconnected = true;

		if (postDisconnectOps != null) {
			postDisconnectOps.begin();
		}
	}

	// switching operations

	private void checkAndSetTransfer() {
		Preconditions.checkState(!mHasQueuedTransfer, "currently transfer operation exists");
		mHasQueuedTransfer = true;
	}

	/**
	 * Performs a "pseudo transfer" to reload player data from some specified blob.
	 *
	 * @param blobId
	 * @param extraAction
	 * @return
	 */
	@Coroutine
	private Task<Void> setPlayerData(UUID blobId, @Nullable Runnable extraAction) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "loadPlayerData called off-main");
		final var player = Bukkit.getPlayer(mPlayerUuid);
		Preconditions.checkState(player != null, "attempted to transfer non-online player");
		checkAndSetTransfer();

		// load the player data from the blob
		Co.await(mRedisHandler.syncRedis());
		final var data = PlayerData.fromRedisData(
			Co.await(Awaitable.from(mRedisHandler.commands().hgetall(mRedisHandler.storageKey(blobId))))
		);

		// TODO: actually initiate the disconnect!

		// we need to run these tasks *after* disconnect
		postDisconnectOps = Co.makeTask(() -> {
			Preconditions.checkState(Bukkit.isPrimaryThread(), "loadPlayerData called off-main");

			final var newProfileId = UUID.randomUUID();
			final var oldProfileId = mProfiles.put(mCurrentProfile, newProfileId);

			if (extraAction != null) {
				extraAction.run();
			}

			final var globalData = packGlobalData();

			Co.await(mRedisHandler.syncRedis());

			// run redis commands
			final var commands = mRedisHandler.commands();

			commands.multi();
			commands.del(mRedisHandler.storageKey(oldProfileId));
			commands.hset(
				mRedisHandler.storageKey(oldProfileId),
				data.toRedisData()
			);
			commands.hset(
				mRedisHandler.playerActiveDataKey(mPlayerUuid),
				data.toRedisData()
			);
			commands.set(
				mRedisHandler.playerMetaDataKey(mPlayerUuid),
				globalData
			);

			Co.await(Awaitable.from(commands.exec()));

			return Co.ret();
		});

		return Co.ret();
	}

	public void switchProfile(String newProfile) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "switchProfile called off-main");
		final var blobId = mProfiles.get(newProfile);
		Preconditions.checkArgument(blobId != null, "unknown profile '%s'", newProfile);
		setPlayerData(blobId, () -> mCurrentProfile = newProfile).begin();
	}

	public void loadStash(String stash) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "loadStash called off-main");
		final var blobId = mStashes.get(stash);
		Preconditions.checkArgument(blobId != null, "unknown stash '%s'", stash);
		setPlayerData(blobId, null).begin();
	}

	public void loadHistory(int historyIndex) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "loadHistory called off-main");
		Preconditions.checkArgument(
			historyIndex >= 0 && historyIndex < mHistory.size(),
			"bad history index: '%s'", historyIndex
		);
		final var blobId = mHistory.get(historyIndex).id;
		setPlayerData(blobId, null).begin();
	}

	// player data saving operations

	// remember that it is very important each of these operations are transactional, so we must
	// use multi() for stuff (remember to use redis thread!)

	/**
	 * Saves the current player data to the active player data on redis.
	 *
	 * @return the task
	 */
	@Coroutine
	Task<Void> savePlayer(PlayerData newData) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		Preconditions.checkState(!mHasDisconnected);

		final var currentData = this.mActivePlayerData = newData;
		final var key = mRedisHandler.playerActiveDataKey(mPlayerUuid);

		Co.await(mRedisHandler.syncRedis());
		Co.await(Awaitable.from(mRedisHandler.commands().hset(
			mRedisHandler.playerActiveDataKey(mPlayerUuid),
			currentData.toRedisData())
		));

		return Co.ret();
	}

	private byte[] packGlobalData() {
		return PlayerDataManager.GSON.toJson(
			new PlayerGlobalData(
				mStashes,
				mProfiles,
				mCurrentProfile,
				mHistory
			)
		).getBytes();
	}

	private Task<Void> storeBlob(PlayerData data, UUID blobAdded, @Nullable UUID blobDeleted) {
		final var newGlobalData = packGlobalData();

		Co.await(mRedisHandler.syncRedis());
		final var commands = mRedisHandler.commands();

		// put the new data to redis
		commands.multi();
		commands.set(mRedisHandler.playerMetaDataKey(mPlayerUuid), newGlobalData);
		if (blobDeleted != null) {
			commands.del(mRedisHandler.storageKey(blobDeleted));
		}
		commands.hset(mRedisHandler.playerActiveDataKey(blobAdded), data.toRedisData());
		Co.await(Awaitable.from(commands.exec()));

		return Co.ret();
	}

	/**
	 * Saves a history entry to redis.
	 *
	 * @param data            The history data.
	 * @param historyMetaData The history metadata.
	 * @param maxHistorySize  The maximum number of history entries to retain.
	 * @return the task
	 */
	@Coroutine
	Task<UUID> pushHistoryEntry(PlayerData data, HistoryMetaData historyMetaData, int maxHistorySize) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		Preconditions.checkState(!mHasDisconnected);

		final var blobId = UUID.randomUUID();

		mHistory.add(new HistoryEntry(blobId, historyMetaData));

		final @Nullable HistoryEntry removed;

		if (mHistory.size() > maxHistorySize) {
			removed = mHistory.remove(0);
		} else {
			removed = null;
		}

		Co.await(storeBlob(data, blobId, removed == null ? null : removed.id));
		return Co.ret(blobId);
	}

	/**
	 * Stores a stash to redis.
	 *
	 * @param data      The stash data.
	 * @param stashName The name of the stash.
	 * @return the task.
	 */
	@Coroutine
	Task<UUID> storeStash(PlayerData data, String stashName) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		Preconditions.checkArgument(!mStashes.containsKey(stashName));

		final var blobId = UUID.randomUUID();

		mStashes.put(stashName, blobId);

		Co.await(storeBlob(data, blobId, null));
		return Co.ret(blobId);
	}

	@Coroutine
	Task<UUID> storeProfile(PlayerData data, String profileName) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		Preconditions.checkArgument(!mProfiles.containsKey(profileName));

		final var blobId = UUID.randomUUID();

		Co.await(storeBlob(data, blobId, null));
		return Co.ret(blobId);
	}
}
