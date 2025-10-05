package com.playmonumenta.redissync.player;

import com.floweytf.coro.concepts.Awaitable;
import com.google.common.base.Preconditions;
import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.RedisAPI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.Nullable;

class RedisHandler implements AutoCloseable {
	private final StatefulRedisConnection<String, byte[]> mConnection;
	private final RedisAsyncCommands<String, byte[]> mCommands;
	private final MonumentaRedisSync mPlugin;
	private final String mDomain;
	private @Nullable Thread mRedisWorkerThread;
	private final ExecutorService mRedisWorker = Executors.newSingleThreadExecutor(r -> {
		Preconditions.checkState(mRedisWorkerThread == null, "multiple thread created from executor?");
		return mRedisWorkerThread = new Thread(r, "RedisWorker");
	});

	RedisHandler(MonumentaRedisSync mrs) {
		this.mConnection = mrs.getRedisApi().openConnection(RedisAPI.STRING_BYTE_CODEC);
		this.mCommands = mConnection.async();
		this.mPlugin = mrs;
		this.mDomain = mrs.getBukkitConfig().getServerDomain();
	}

	public void assertRedisThread() {
		Preconditions.checkState(Thread.currentThread() == mRedisWorkerThread, "expected task to run on redis thread");
	}

	public Awaitable.Unwrapped<Void> syncRedis() {
		return Awaitable.runOn(mRedisWorker);
	}

	public Awaitable.Unwrapped<Void> syncMain() {
		return mPlugin;
	}

	String storageKey(UUID blobId) {
		return "%s:playerdata_storage:%s".formatted(mDomain, blobId);
	}

	String playerActiveDataKey(UUID playerId) {
		return "%s:playerdata:%s:active".formatted(mDomain, playerId);
	}

	String playerMetaDataKey(UUID playerId) {
		return "%s:playerdata:%s:metadata".formatted(mDomain, playerId);
	}

	RedisAsyncCommands<String, byte[]> commands() {
		assertRedisThread();
		return mCommands;
	}

	@Override
	public void close() {
		mRedisWorker.close();
		mConnection.close();
	}
}
