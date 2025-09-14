package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;
import java.util.TimerTask;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import org.jetbrains.annotations.Nullable;

public class RabbitMQManagerAbstractionGeneric implements RabbitMQManagerAbstractionInterface {
	static final ScheduledThreadPoolExecutor mScheduler = new ScheduledThreadPoolExecutor(1);
	private final NetworkRelayGeneric mPlugin;
	private static @Nullable TimerTask mHeartbeatRunnable = null;
	private int mHeartbeatPeriod = 1;
	private @Nullable Runnable mOnHeartbeatRunnable = null;

	protected RabbitMQManagerAbstractionGeneric(NetworkRelayGeneric plugin) {
		mPlugin = plugin;
	}

	@Override
	public void startHeartbeatRunnable(Runnable runnable, int delaySeconds, int periodSeconds) {
		mOnHeartbeatRunnable = runnable;
		mHeartbeatPeriod = periodSeconds;

		mHeartbeatRunnable = getNewHeartbeatRunnable();
		mScheduler.schedule(mHeartbeatRunnable, delaySeconds, TimeUnit.SECONDS);
	}

	@Override
	public void scheduleProcessPacket(Runnable runnable) {
		mScheduler.schedule(runnable, 0, TimeUnit.MILLISECONDS);
	}

	@Override
	public void stopHeartbeatRunnable() {
		if (mHeartbeatRunnable != null) {
			mHeartbeatRunnable.cancel();
		}
	}

	@Override
	public void stopServer() {
		mPlugin.getLogger().info("Lost connection to network relay / rabbitmq");
		System.exit(0);
	}

	@Override
	public void sendMessageEvent(String channel, String source, JsonObject data) {
		NetworkRelayMessageEventGeneric event = new NetworkRelayMessageEventGeneric(channel, source, data);
		mPlugin.callEvent(event);
	}

	@Override
	public JsonObject gatherHeartbeatData() {
		GatherHeartbeatDataEventGeneric event = new GatherHeartbeatDataEventGeneric();
		mPlugin.callEvent(event);
		return event.getPluginData();
	}

	@Override
	public void sendDestOnlineEvent(String dest) {
		DestOnlineEventGeneric event = new DestOnlineEventGeneric(dest);
		mPlugin.callEvent(event);
	}

	@Override
	public void sendDestOfflineEvent(String dest) {
		DestOfflineEventGeneric event = new DestOfflineEventGeneric(dest);
		mPlugin.callEvent(event);
	}

	private TimerTask getNewHeartbeatRunnable() {
		return new TimerTask() {
			@Override
			public void run() {
				runHeartbeatRunnable();
			}
		};
	}

	private void runHeartbeatRunnable() {
		if (mHeartbeatRunnable != null) {
			mHeartbeatRunnable.cancel();
		}
		mHeartbeatRunnable = getNewHeartbeatRunnable();
		mScheduler.schedule(mHeartbeatRunnable, mHeartbeatPeriod, TimeUnit.SECONDS);

		if (mOnHeartbeatRunnable == null) {
			return;
		}
		mOnHeartbeatRunnable.run();
	}
}
