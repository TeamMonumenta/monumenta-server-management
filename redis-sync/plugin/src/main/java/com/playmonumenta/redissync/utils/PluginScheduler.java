package com.playmonumenta.redissync.utils;

import com.floweytf.coro.concepts.Awaitable;
import com.floweytf.coro.concepts.Continuation;
import com.floweytf.coro.concepts.CoroutineExecutor;
import java.util.concurrent.Executor;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

public interface PluginScheduler extends Awaitable.Unwrapped<Void>, Executor, Plugin {
	@Override
	default void execute(@NotNull Runnable command) {
		Bukkit.getScheduler().runTask(this, command);
	}

	@Override
	default void execute(CoroutineExecutor coroutineExecutor, Continuation<Void> continuation) {
		execute(() -> continuation.submit(null));
	}
}
