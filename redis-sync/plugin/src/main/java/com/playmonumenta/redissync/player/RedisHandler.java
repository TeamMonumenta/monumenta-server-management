package com.playmonumenta.redissync.player;

import com.floweytf.coro.Co;
import com.floweytf.coro.annotations.Coroutine;
import com.floweytf.coro.annotations.MakeCoro;
import com.floweytf.coro.concepts.Task;
import com.playmonumenta.redissync.MonumentaRedisSync;
import com.playmonumenta.redissync.RedisAPI;
import com.playmonumenta.redissync.utils.PluginScheduler;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import java.util.UUID;
import java.util.function.Supplier;

class RedisHandler {
	private final StatefulRedisConnection<String, byte[]> mConnection;
	private final RedisAsyncCommands<String, byte[]> mCommands;
	private final PluginScheduler mScheduler;
	private final String mDomain;

	RedisHandler(MonumentaRedisSync mrs) {
		this.mConnection = mrs.getRedisApi().openConnection(RedisAPI.STRING_BYTE_CODEC);
		this.mCommands = mConnection.async();
		this.mScheduler = mrs;
		this.mDomain = mrs.getBukkitConfig().getServerDomain();
	}

	@Coroutine
	<T> Task<T> performCommand(final @MakeCoro Supplier<Task<T>> command) {
		Co.await(mScheduler);
		return Co.ret(Co.await(command.get()));
	}

	String playerDataKey(UUID playerId, UUID blobId) {
		return "%s:playerdata:%s:%s".formatted(mDomain, playerId, blobId);
	}

	String globalPlayerDataKey(UUID playerId) {
		return "%s:playerdata:%s".formatted(mDomain, playerId);
	}

	RedisAsyncCommands<String, byte[]> commands() {
		return mCommands;
	}
}
