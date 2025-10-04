package com.playmonumenta.redissync.player;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.annotations.MakeCoro;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Task;
import com.google.common.base.Preconditions;
import com.playmonumenta.redissync.MonumentaRedisSync;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/**
 * Represents the local, cached info for a specific player that is online or in the process of joining the server.
 */
public class LocalRedisPlayer {
	// we represent playerdata as an abstract set of "blobs," indexed by some key K, so we have a
	// blob: K -> PlayerData
	//
	// we can consider the set of "stashes" that the player has to be simply a map
	// stashes: String -> K, indexed by the name
	//
	// a player profile, similarly, is implemented as such:
	// profiles: String -> K
	// current_profile: String
	//
	// now, to handle player history, we will also have an array
	// history: (K, HistoryMeta)[]
	//
	// the source-data is indexed by some K, which is what profile will be loaded/stored to
	// source: K

	public record HistoryEntry(UUID id, HistoryMetaData metaData) {
	}

	private final UUID mPlayerUuid;
	private final Map<String, UUID> mStashes;
	private final Map<String, UUID> mProfiles;
	private String mCurrentProfile;
	private final List<HistoryEntry> mHistory;
	private PlayerData mActivePlayerData;
	private boolean mDisableSaving;

	// TODO: group this somewhere sane
	private @Nullable Task<Void> mCurrentTask;
	private final RedisHandler mRedisHandler;

	@Nullable
	private Runnable queuedOp;

	/**
	 * WARNING: this is blocking
	 */
	LocalRedisPlayer(UUID uuid, RedisHandler redisHandler) {
		this.mPlayerUuid = uuid;
		this.mRedisHandler = redisHandler;
		final var key = redisHandler.globalPlayerDataKey(uuid);
		final var metadata = redisHandler.commands().get(key).toCompletableFuture().join();
		final var globalPlayerData = PlayerDataManager.GSON.fromJson(
			new String(metadata, StandardCharsets.UTF_8),
			PlayerGlobalData.class
		);

		// build the maps
		this.mStashes = globalPlayerData.stashes();
		this.mProfiles = globalPlayerData.profiles();
		this.mCurrentProfile = globalPlayerData.currentProfile();
		this.mHistory = globalPlayerData.history();

		final var profileId = mProfiles.get(mCurrentProfile);
		Preconditions.checkState(profileId != null);

		// fetch the active player data
		this.mActivePlayerData = PlayerData.fromRedisData(
			redisHandler.commands()
				.hgetall(redisHandler.playerDataKey(uuid, profileId))
				.toCompletableFuture()
				.join()
		);
	}

	/**
	 * Runs a redis task, synchronizing execution to the *current* player. This means that no concurrent redis
	 * operations can be run on the same player, effectively making this a per-player redis mutex.
	 *
	 * @param task the task.
	 * @return the awaitable.
	 */
	private Awaitable<Void> runRedisTask(@MakeCoro Supplier<Task<Void>> task) {
		return mCurrentTask = Co.launch(() -> {
			Co.await(mCurrentTask);
			Co.await(task.get());
			return Co.ret();
		});
	}

	private void enqueueTransferOperation(Runnable op) {
		final var player = Bukkit.getPlayer(mPlayerUuid);
		Preconditions.checkState(player != null, "attempted to transfer non-online player");
		Preconditions.checkState(queuedOp == null, "currently queued player data operation exists");
		queuedOp = op;
		player.sendPluginMessage(MonumentaRedisSync.getInstance(), "redissync:transfer",
			"TODO_FIND_TARGET".getBytes());
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

	public void currentPlayerData(PlayerData data) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		mActivePlayerData = data;
	}

	public void currentPlayerData(UnaryOperator<PlayerData> transform) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		mActivePlayerData = transform.apply(mActivePlayerData);
	}

	// switching operations

	public void switchProfile(String newProfile) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "switchProfile called off-main");
		Preconditions.checkArgument(mProfiles.containsKey(newProfile), "unknown profile '%s'", newProfile);

		enqueueTransferOperation(() -> mCurrentProfile = newProfile);
	}

	public void loadStash(String stash) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "loadStash called off-main");
		final var stashId = mStashes.get(stash);
		Preconditions.checkArgument(stashId != null, "unknown stash '%s'", stash);

		enqueueTransferOperation(() -> mProfiles.put(mCurrentProfile, stashId));
	}

	public void loadHistory(int historyIndex) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "loadHistory called off-main");
		Preconditions.checkArgument(
			historyIndex >= 0 && historyIndex < mHistory.size(),
			"bad history index: '%s'", historyIndex
		);
		final var id = mHistory.get(historyIndex).id;

		enqueueTransferOperation(() -> mProfiles.put(mCurrentProfile, id));
	}

	// player data saving operations

	// remember that it is very important each of these operations are transactional, so we must
	// use multi() for stuff (remember to use main thread!)

	private UUID currentProfileId() {
		final var id = mProfiles.get(mCurrentProfile);
		Preconditions.checkState(id != null);
		return id;
	}

	@Coroutine
	Task<Void> savePlayer() {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		final var id = currentProfileId();
		final var key = mRedisHandler.playerDataKey(mPlayerUuid, id);

		final var currentData = this.mActivePlayerData;

		Co.await(runRedisTask(() -> {
			Co.await(Awaitable.from(mRedisHandler.commands().hset(key, currentData.toRedisData())));
			return Co.ret();
		}));

		return Co.ret();
	}

	@Coroutine
	Task<UUID> pushHistoryEntry(PlayerData newData, HistoryMetaData historyMetaData, int maxHistorySize) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		final var id = UUID.randomUUID();

		mHistory.add(new HistoryEntry(id, historyMetaData));

		final @Nullable HistoryEntry removed;

		if (mHistory.size() > maxHistorySize) {
			removed = mHistory.remove(0);
		} else {
			removed = null;
		}

		final var newGlobalData = PlayerDataManager.GSON.toJson(
			new PlayerGlobalData(
				mStashes,
				mProfiles,
				mCurrentProfile,
				mHistory
			)
		);

		// put the new data to redis
		Co.await(runRedisTask(() -> {
			final var cmd = mRedisHandler.commands();
			cmd.multi();
			cmd.set(mRedisHandler.globalPlayerDataKey(mPlayerUuid), newGlobalData.getBytes());
			if (removed != null) {
				cmd.del(mRedisHandler.playerDataKey(mPlayerUuid, removed.id));
			}
			cmd.hset(mRedisHandler.playerDataKey(mPlayerUuid, id), newData.toRedisData());
			Co.await(Awaitable.from(cmd.exec()));
			return Co.ret();
		}));

		return Co.ret(id);
	}

	@Coroutine
	Task<UUID> storeStash(PlayerData newData, String stashName) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		Preconditions.checkState(!mStashes.containsKey(stashName));
		final var id = UUID.randomUUID();
		mStashes.put(stashName, id);

		final var newGlobalData = PlayerDataManager.GSON.toJson(
			new PlayerGlobalData(
				mStashes,
				mProfiles,
				mCurrentProfile,
				mHistory
			)
		);

		Co.await(runRedisTask(() -> {
			final var cmd = mRedisHandler.commands();
			cmd.multi();
			cmd.set(mRedisHandler.globalPlayerDataKey(mPlayerUuid), newGlobalData.getBytes());
			cmd.hset(mRedisHandler.playerDataKey(mPlayerUuid, id), newData.toRedisData());
			Co.await(Awaitable.from(cmd.exec()));
			return Co.ret();
		}));

		return Co.ret(id);
	}
}
