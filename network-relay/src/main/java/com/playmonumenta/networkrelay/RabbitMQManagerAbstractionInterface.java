package com.playmonumenta.networkrelay;

import com.google.gson.JsonObject;

public interface RabbitMQManagerAbstractionInterface {
	void startHeartbeatRunnable(Runnable runnable, int delaySeconds, int periodSeconds);

	void scheduleProcessPacket(Runnable runnable);

	void stopHeartbeatRunnable();

	void stopServer();

	void sendMessageEvent(String channel, String source, JsonObject data);

	JsonObject gatherHeartbeatData();

	void sendDestOnlineEvent(String dest);

	void sendDestOfflineEvent(String dest);

	default void sendNetworkMessage(String destination, String channel, JsonObject data, com.rabbitmq.client.AMQP.BasicProperties properties) throws Exception {
		RabbitMQManager.getInstance().sendNetworkMessage(destination, channel, data, properties);
	}
}
