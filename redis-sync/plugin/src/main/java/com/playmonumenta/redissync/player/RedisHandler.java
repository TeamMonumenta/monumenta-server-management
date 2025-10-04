package com.playmonumenta.redissync.player;

import com.floweytf.coro.concepts.Awaitable;
import com.google.common.base.Preconditions;
import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.RedisAPI;
import com.playmonumenta.redissync.utils.PluginScheduler;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.UUID;

class RedisHandler {
	private final StatefulRedisConnection<String, byte[]> mConnection;
	private final RedisAsyncCommands<String, byte[]> mCommands;
	private final PluginScheduler mScheduler;
	private final String mDomain;
	private final Thread multiWorker = new Thread();

	RedisHandler(MonumentaRedisSync mrs) {
		this.mConnection = mrs.getRedisApi().openConnection(RedisAPI.STRING_BYTE_CODEC);
		this.mCommands = mConnection.async();
		this.mScheduler = mrs;
		this.mDomain = mrs.getBukkitConfig().getServerDomain();
	}

	public void assertRedisThread() {
		Preconditions.checkState(Thread.currentThread() == multiWorker, "expected task to run on multi-safe thread");
	}

	public Awaitable.Unwrapped<Void> syncRedis() {
		throw new AssertionError("TODO");
	}

	public Awaitable.Unwrapped<Void> syncMain() {
		throw new AssertionError("TODO");
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
}
