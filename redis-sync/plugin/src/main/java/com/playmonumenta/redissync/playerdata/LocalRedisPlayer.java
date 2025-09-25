package com.playmonumenta.redissync.playerdata;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.annotations.MakeCoro;
import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Task;
import com.google.common.base.Preconditions;
import com.google.common.collect.Sets;
import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.RedisAPI;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.function.UnaryOperator;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

import static com.floweytf.coro.support.Awaitables.awaitable;

/**
 * Represents the info for a specific player.
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

	private record HistoryEntry(UUID id) {
	}

	private final UUID mUuid;
	private final Set<UUID> mRemotePlayerDataBlobs = new HashSet<>();
	private final Map<String, UUID> mStashes = new HashMap<>();
	private final Map<String, UUID> mProfiles = new HashMap<>();
	private String mCurrentProfile;
	private final List<HistoryEntry> mHistory = new ArrayList<>();
	private CachedPlayerData mActivePlayerData;

	private @Nullable Task<Void> lastestTask;

	@Nullable
	private Runnable queuedOp;

	public LocalRedisPlayer(UUID mUuid, CachedPlayerData activePlayerData) {
		this.mUuid = mUuid;
		this.mActivePlayerData = activePlayerData;
	}

	private Awaitable<Void> runRedisTask(@MakeCoro Supplier<Task<Void>> task) {
		final var newTask = task.get();

		return lastestTask = Co.launch(() -> {
			Co.await(lastestTask);
			Co.await(newTask);
			return Co.ret();
		});
	}

	private void enqueuePlayerDataOperation(Runnable op) {
		final var player = Bukkit.getPlayer(mUuid);
		Preconditions.checkState(player != null, "attempted to transfer non-online player");
		Preconditions.checkState(queuedOp == null, "currently queued player data operation exists");
		queuedOp = op;

		// TODO: notify the proxy to send the player to the correct target
	}

	public void switchProfile(String newProfile) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "switchProfile called off-main");
		Preconditions.checkArgument(mProfiles.containsKey(newProfile), "unknown profile '%s'", newProfile);

		enqueuePlayerDataOperation(() -> mCurrentProfile = newProfile);
	}

	public void loadStash(String stash) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "loadStash called off-main");
		final var stashId = mStashes.get(stash);
		Preconditions.checkArgument(stashId != null, "unknown stash '%s'", stash);

		enqueuePlayerDataOperation(() -> mProfiles.put(mCurrentProfile, stashId));
	}

	public void loadHistory(int historyIndex) {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "loadHistory called off-main");
		Preconditions.checkArgument(
			historyIndex >= 0 && historyIndex < mHistory.size(),
			"bad history index: '%s'", historyIndex
		);
		final var id = mHistory.get(historyIndex).id;

		enqueuePlayerDataOperation(() -> mProfiles.put(mCurrentProfile, id));
	}

	public CachedPlayerData currentPlayerData() {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		return mActivePlayerData;
	}

	public void currentPlayerData(CachedPlayerData data) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		mActivePlayerData = data;
	}

	public void currentPlayerData(UnaryOperator<CachedPlayerData> transform) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		mActivePlayerData = transform.apply(mActivePlayerData);
	}

	@Coroutine
	public Task<Void> savePlayerData() {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		final var data = mActivePlayerData;
		final var storeId = mProfiles.get(mCurrentProfile);
		Preconditions.checkState(storeId != null);

		Co.await(runRedisTask(() -> {
			Co.await(data.store("TODO", mUuid, storeId));
			return Co.ret();
		}));

		return Co.ret();
	}

	@Coroutine
	public Task<Void> storeStash(String stash) {
		Preconditions.checkState(Bukkit.isPrimaryThread());
		final var player = Bukkit.getPlayer(mUuid);
		Preconditions.checkState(player != null, "attempted to create stash for non-online player?");

		final var stashStoreId = UUID.randomUUID();

		// save the current player data, and put the data in the map
		MonumentaRedisSync.getInstance().getVersionAdapter().savePlayer(player);
		final var stashData = mActivePlayerData;

		// actually save the stash to redis
		Co.await(runRedisTask(() -> {
			// store data to redis
			Co.await(stashData.store("TODO", mUuid, stashStoreId));

			// important: run on main
			Co.await(); /* TODO switch to main thread*/
			mRemotePlayerDataBlobs.add(stashStoreId);
			mStashes.put(stash, stashStoreId);
			return Co.ret();
		}));

		return Co.ret();
	}

	@Coroutine
	public Task<Void> collectGarbage() {
		Preconditions.checkState(Bukkit.isPrimaryThread(), "collectGarbage() called off-main");

		final var gcRoots = new HashSet<UUID>();
		gcRoots.addAll(mStashes.values());
		gcRoots.addAll(mProfiles.values());
		mHistory.stream().map(HistoryEntry::id).forEach(gcRoots::add);
		final var toDelete = Sets.difference(mRemotePlayerDataBlobs, gcRoots)
			.stream()
			.map(UUID::toString) // TODO: fix this key
			.toArray(String[]::new);
		mRemotePlayerDataBlobs.clear();
		mRemotePlayerDataBlobs.addAll(gcRoots);

		Co.await(runRedisTask(() -> {
			Co.await(awaitable(RedisAPI.getInstance().asyncStringBytes().del(toDelete)));
			return Co.ret();
		}));

		return Co.ret();
	}
}
