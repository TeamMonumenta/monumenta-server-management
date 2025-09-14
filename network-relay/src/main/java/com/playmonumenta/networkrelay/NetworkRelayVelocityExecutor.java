package com.playmonumenta.networkrelay;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.playmonumenta.networkrelay.util.MMLog;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.checkerframework.checker.nullness.qual.MonotonicNonNull;

// Entire purpose of this class is to mimic a Bukkit/Bungee main "thread"
public class NetworkRelayVelocityExecutor {
	private static @MonotonicNonNull NetworkRelayVelocityExecutor INSTANCE;
	private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(new ThreadFactoryBuilder().setNameFormat("MonumentaNetworkRelay RabbitMQ Thread").build());

	public NetworkRelayVelocityExecutor() {
		INSTANCE = this;
	}

	public static NetworkRelayVelocityExecutor getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new NetworkRelayVelocityExecutor();
		}
		return INSTANCE;
	}

	public void stop() {
		executor.shutdown();
		try {
			if (executor.awaitTermination(1, TimeUnit.SECONDS)) {
				executor.shutdownNow();
			}
		} catch (InterruptedException ex) {
			if (!executor.isShutdown()) {
				executor.shutdownNow();
			}
		}
	}

	public void schedule(Runnable runnable) {
		if (executor.isShutdown()) {
			return;
		}
		schedule(new WrappedRunnable(runnable));
	}

	public void schedule(WrappedRunnable runnable) {
		if (executor.isShutdown()) {
			return;
		}
		executor.schedule(runnable, 0, TimeUnit.MILLISECONDS);
	}

	public void scheduleRepeatingTask(Runnable runnable, long delay, long period, TimeUnit unit) {
		if (executor.isShutdown()) {
			return;
		}
		executor.scheduleAtFixedRate(new WrappedRunnable(runnable), delay, period, unit);
	}

	public void scheduleRepeatingTask(WrappedRunnable runnable, long delay, long period, TimeUnit unit) {
		if (executor.isShutdown()) {
			return;
		}
		executor.scheduleAtFixedRate(runnable, delay, period, unit);
	}

	public static class WrappedRunnable implements Runnable {
		private final Runnable mTask;

		public WrappedRunnable(Runnable task) {
			this.mTask = task;
		}

		@Override
		public void run() {
			try {
				mTask.run();
			} catch (Exception ex) {
				MMLog.severe("Error executing task in RabbitMQ");
				ex.printStackTrace();
			}
		}
	}


}
