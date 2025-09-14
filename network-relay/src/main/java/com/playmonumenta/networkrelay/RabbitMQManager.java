package com.playmonumenta.networkrelay;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.rabbitmq.client.ShutdownListener;
import com.rabbitmq.client.ShutdownSignalException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import org.jetbrains.annotations.Nullable;

public class RabbitMQManager {
	private static final String CONSUMER_TAG = "consumerTag";
	private static final String BROADCAST_EXCHANGE_NAME = "broadcast";

	private static @Nullable RabbitMQManager INSTANCE = null;

	private final long mPrimaryThreadId;
	private final Gson mGson = new Gson();
	private final Logger mLogger;
	private final Channel mChannel;
	private final Connection mConnection;
	private final RabbitMQManagerAbstractionInterface mAbstraction;
	private final String mShardName;
	private final int mHeartbeatInterval;
	private final int mDestinationTimeout;
	private final long mDefaultTTL;

	/*
	 * All messages will be queued until the server finishes starting.
	 * This avoids calling DestOnlineEvents before plugins have finished loading to handle them.
	 */
	private boolean mServerFinishedStarting = false;

	/*
	 * If mShutdown = false, this is expected to run normally
	 * If mShutdown = true, the server is already shutting down
	 */
	private boolean mShutdown = false;

	/*
	 * If mConsumerAlive = true, the consumer is running
	 * If mConsumerAlive = false, the consumer has terminated
	 */
	private boolean mConsumerAlive;

	/*
	 * Last time a message was sent.
	 * If this was more than mHeartbeatInterval seconds ago,
	 * send another heartbeat message.
	 */
	private Instant mLastHeartbeat = Instant.MIN;

	/*
	 * Last time a message was received from a destination.
	 * If this was more than mDestinationTimeout seconds ago,
	 * consider that destination offline.
	 * Offline destinations are removed from the map.
	 */
	private final Map<String, Instant> mDestinationLastHeartbeat = new ConcurrentSkipListMap<>();

	/*
	 * Most recently received plugin data from a shard
	 * Updated each heartbeat, removed when shard is considered offline
	 * based on mDestinationLastHeartbeat
	 */
	private final Map<String, JsonObject> mDestinationHeartbeatData = new ConcurrentSkipListMap<>();

	/*
	 * The type of each server; examples include "minecraft" and "proxy", though others may appear.
	 */
	private final Map<String, String> mDestinationTypes = new ConcurrentSkipListMap<>();

	private static class QueuedMessage {
		final String mChannel;
		final JsonObject mData;

		private QueuedMessage(String channel, JsonObject data) {
			mChannel = channel;
			mData = data;
		}

		private String getChannel() {
			return mChannel;
		}

		private JsonObject getData() {
			return mData;
		}
	}

	private final Map<String, Deque<QueuedMessage>> mDestinationQueuedMessages = new ConcurrentSkipListMap<>();

	private class RelayShutdownHandler implements ShutdownListener {
		@Override
		public void shutdownCompleted(ShutdownSignalException cause) {
			String msg = "RabbitMQ connection shut down; cause='" + cause.getCause() + "' message='" + cause.getMessage() + "'";
			if (mShutdown) {
				mLogger.info(msg);
			} else {
				mLogger.warning(msg);
			}
		}
	}

	// Must be called on primary thread
	protected RabbitMQManager(RabbitMQManagerAbstractionInterface abstraction, Logger logger, String shardName, String rabbitURI, int heartbeatInterval, int destinationTimeout, long defaultTTL) throws Exception {
		// Once this project is running on Java 19 or higher, switch to Thread.threadId() instead
		// (does not exist in this version)
		//noinspection deprecation
		mPrimaryThreadId = Thread.currentThread().getId();
		mAbstraction = abstraction;
		mLogger = logger;
		mShardName = shardName;
		mHeartbeatInterval = heartbeatInterval;
		mDestinationTimeout = destinationTimeout;
		mDefaultTTL = defaultTTL;

		mAbstraction.startHeartbeatRunnable(() -> {
			Instant now = Instant.now();
			if (now.minusSeconds(mHeartbeatInterval).compareTo(mLastHeartbeat) >= 0) {
				mLastHeartbeat = now;
				sendHeartbeat();
			}

			Instant timeoutThreshold = now.minusSeconds(mDestinationTimeout);
			Iterator<Map.Entry<String, Instant>> iter = mDestinationLastHeartbeat.entrySet().iterator();
			while (iter.hasNext()) {
				Map.Entry<String, Instant> entry = iter.next();
				String dest = entry.getKey();
				Instant lastHeartbeat = entry.getValue();

				if (timeoutThreshold.compareTo(lastHeartbeat) >= 0) {
					sendDestOfflineEvent(dest);
					mDestinationHeartbeatData.remove(dest);
					mDestinationTypes.remove(dest);
					iter.remove();
				}
			}
		}, 2, 1);

		ConnectionFactory factory = new ConnectionFactory();
		factory.setAutomaticRecoveryEnabled(true);
		factory.setUri(rabbitURI);

		mConnection = factory.newConnection();
		mChannel = mConnection.createChannel();
		// Print out the reason for disconnection if it happens
		mConnection.addShutdownListener(new RelayShutdownHandler());

		/* Declare a broadcast exchange which routes messages to all attached queues */
		mChannel.exchangeDeclare(BROADCAST_EXCHANGE_NAME, "fanout");

		/* Declare queue arguments */
		Map<String, Object> queueArgs = new HashMap<String, Object>();
		// To prevent messages from piling up while a shard is offline - delete a queue after 5 minutes
		queueArgs.put("x-expires", 300000); // 5 minutes of inactivity (shard not responding/down) until the queue deletes itself
		/* Declare the queue for this shard */
		mChannel.queueDeclare(shardName, false, false, false, queueArgs);
		/* Bind the queue to the exchange */
		mChannel.queueBind(shardName, BROADCAST_EXCHANGE_NAME, "");

		/* Consumer to receive messages */
		DeliverCallback deliverCallback = (consumerTag, delivery) -> {
			final String message;
			final JsonObject root;

			try {
				message = new String(delivery.getBody(), StandardCharsets.UTF_8);

				root = mGson.fromJson(message, JsonObject.class);
				if (root == null) {
					throw new Exception("Failed to parse rabbit message as json: " + message);
				}
				if (!root.has("channel") || !root.get("channel").isJsonPrimitive() || !root.get("channel").getAsJsonPrimitive().isString()) {
					throw new Exception("Rabbit message missing 'channel': " + message);
				}
				if (!root.has("source") || !root.get("source").isJsonPrimitive() || !root.get("source").getAsJsonPrimitive().isString()) {
					throw new Exception("Rabbit message missing 'source': " + message);
				}
				if (!root.has("data") || !root.get("data").isJsonObject()) {
					throw new Exception("Rabbit message missing 'data': " + message);
				}
			} catch (Exception ex) {
				mLogger.warning(ex.getMessage());
				/* Parsing this message failed - but ack it anyway, because it's not going to parse next time either */
				mChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
				return;
			}

			String channel = root.get("channel").getAsString();
			String source = root.get("source").getAsString();
			JsonObject data = root.get("data").getAsJsonObject();
			JsonObject pluginData = null;

			/* Check for heartbeat data - pluginData */
			if (root.has("pluginData")) {
				JsonElement pluginDataElement = root.get("pluginData");
				if (pluginDataElement != null && pluginDataElement.isJsonObject()) {
					pluginData = pluginDataElement.getAsJsonObject();
				}
			}
			final JsonObject pluginDataFinal = pluginData;

			/* Process the packet on the main thread */
			mAbstraction.scheduleProcessPacket(() -> {
				mLogger.finer("Processing message from=" + source + " channel=" + channel);
				mLogger.finest(() -> "content=" + mGson.toJson(root));

				/* Check for heartbeat data - online status */
				boolean isDestShutdown = false;
				JsonElement onlineJson = root.get("online");
				if (onlineJson instanceof JsonPrimitive) {
					JsonPrimitive onlinePrimitive = (JsonPrimitive) onlineJson;
					if (onlinePrimitive.isBoolean() && !onlinePrimitive.getAsBoolean()) {
						isDestShutdown = true;
						sendDestOfflineEvent(source);
						mDestinationLastHeartbeat.remove(source);
						mDestinationHeartbeatData.remove(source);
						mDestinationTypes.remove(source);
					}
				}

				if (!isDestShutdown) {
					boolean heartbeatDataPresent = mDestinationLastHeartbeat.containsKey(source);

					if (mServerFinishedStarting && pluginDataFinal != null) {
						/* This message contained heartbeat data - record it */
						mDestinationLastHeartbeat.put(source, Instant.now());
						mDestinationHeartbeatData.put(source, pluginDataFinal);

						/* Get the server type, defaulting to "minecraft" */
						JsonObject networkRelayPluginData
							= pluginDataFinal.getAsJsonObject(NetworkRelayAPI.NETWORK_RELAY_HEARTBEAT_IDENTIFIER);
						if (networkRelayPluginData == null) {
							networkRelayPluginData = new JsonObject();
						}
						JsonPrimitive serverTypeJson
							= networkRelayPluginData.getAsJsonPrimitive("server-type");
						String serverType;
						if (serverTypeJson != null && serverTypeJson.isString()) {
							serverType = serverTypeJson.getAsString();
						} else {
							serverType = "minecraft";
						}
						mDestinationTypes.put(source, serverType);
					}

					if (heartbeatDataPresent) {
						/* This shard was already marked online - deliver normally */
						mAbstraction.sendMessageEvent(channel, source, data);
					} else {
						/* This shard was not known to be online until this message */
						if (!mServerFinishedStarting || pluginDataFinal == null) {
							/*
							 * Got a message from this shard, but unfortunately it doesn't contain any plugin data
							 * (i.e. it's not a heartbeat message)
							 * This can happen randomly when other traffic is happening and this receiving shard just started
							 * Can't send the online event yet - there's no heartbeat data which plugins might depend on while handling the online event
							 * Also can't deliver the message to plugins, since they may be doing state tracking based on online status
							 *
							 * Instead, queue the packet for later delivery, once we do receive a heartbeat message containing plugin data
							 *
							 * This same logic applies if the server has not finished starting yet - queue all messages
							 */

							/* Get existing queue or create and insert a new one */
							Deque<QueuedMessage> queue = mDestinationQueuedMessages.computeIfAbsent(source, (unused) -> new ConcurrentLinkedDeque<>());
							queue.addLast(new QueuedMessage(channel, data));

							StringBuilder msg = new StringBuilder()
								.append("Queued packet from ")
								.append(source)
								.append(" as this shard ");
							if (!mServerFinishedStarting) {
								msg.append("is not ready to receive heartbeat data yet");
							} else {
								msg.append("has not received heartbeat data yet");
							}
							msg.append(". Current queue size is ").append(queue.size());
							if (queue.size() > 100) {
								mLogger.warning(msg.toString());
							} else {
								mLogger.info(msg.toString());
							}
						} else {
							/*
							 * Got a message from this shard, and it has plugin data - great!
							 * Have everything needed to send online event and deliver the message
							 */

							mLogger.fine("Shard " + source + " is online");
							mAbstraction.sendDestOnlineEvent(source);

							/* Deliver this current message */
							mAbstraction.sendMessageEvent(channel, source, data);

							/* Check if there were any queued messages from before heartbeat data was available and deliver them */
							Deque<QueuedMessage> queue = mDestinationQueuedMessages.remove(source);
							if (queue != null) {
								for (QueuedMessage msg : queue) {
									mLogger.fine(() -> "Delivering queued message from " + source + " now that it is marked as online");
									mAbstraction.sendMessageEvent(msg.getChannel(), source, msg.getData());
								}
							}
						}
					}
				}

				/*
				 * Always acknowledge messages after attempting to handle them, even if there's an error
				 * Don't want a failing message to get stuck in an infinite loop
				 */

				try {
					mChannel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
				} catch (IOException ex) {
					/*
					 * If the channel disconnects, we just won't ack this message
					 * It will be redelivered later
					 */
					mLogger.warning("Failed to acknowledge rabbit message '" + message + "'");
				}
			});
		};

		mConsumerAlive = true;
		mChannel.basicConsume(shardName, false, CONSUMER_TAG, deliverCallback,
		                      consumerTag -> {
			mConsumerAlive = false;
			if (mShutdown) {
				mLogger.info("RabbitMQ consumer has terminated");
			} else {
				mLogger.severe("RabbitMQ consumer has terminated unexpectedly - stopping the shard...");
				mAbstraction.stopServer();
			}
		});

		mLogger.info("Started RabbitMQ consumer");

		INSTANCE = this;
	}

	protected void stop() {
		mShutdown = true;
		if (mConsumerAlive) {
			try {
				mAbstraction.stopHeartbeatRunnable();
			} catch (Exception ex) {
				mLogger.warning("Failed to cancel heartbeat runnable: " + ex.getMessage());
			}

			try {
				mChannel.basicCancel(CONSUMER_TAG);
			} catch (Exception ex) {
				mLogger.warning("Failed to cancel rabbit consumer: " + ex.getMessage());
			}
			try {
				JsonObject root = new JsonObject();
				root.add("data", new JsonObject());
				root.addProperty("online", false);

				/* Heartbeat messages are only allowed to be retained by the exchange for 5x their interval */
				AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
					.expiration(Long.toString(mHeartbeatInterval * 1000L * 5L))
					.build();

				sendNetworkMessageInternal("*", NetworkRelayAPI.HEARTBEAT_CHANNEL, root, properties);
			} catch (Exception ex) {
				mLogger.warning("Failed to send shutdown heartbeat: " + ex.getMessage());
			}
			try {
				mConnection.close();
			} catch (Exception ex) {
				mLogger.warning("Failed to close rabbit channel: " + ex.getMessage());
			}
		}
	}

	protected static RabbitMQManager getInstance() {
		if (INSTANCE == null) {
			throw new RuntimeException("RabbitMQManager is not loaded");
		}
		return INSTANCE;
	}

	protected String getShardName() {
		return mShardName;
	}

	protected void sendNetworkMessage(String destination, String channel, JsonObject data) throws Exception {
		/* If no expiration set by caller, use the default */
		sendExpiringNetworkMessage(destination, channel, data, mDefaultTTL);
	}

	// ! - Should only be called from the abstraction! - usb
	protected void sendNetworkMessage(String destination, String channel, JsonObject data, AMQP.BasicProperties properties) throws Exception {
		JsonObject root = new JsonObject();
		root.add("data", data);
		sendNetworkMessageInternal(destination, channel, root, properties);
	}

	/* Called after adding the data and (optionally) pluginData to a root json object */
	private void sendNetworkMessageInternal(String destination, String channel, JsonObject root, AMQP.BasicProperties properties) throws Exception {
		root.addProperty("source", mShardName);
		root.addProperty("dest", destination);
		root.addProperty("channel", channel);

		/* Broadcasting a non-heartbeat message - add heartbeat data to it if running on the primary thread,
		 * and it's been more than half the normal heartbeat time since the last heartbeat
		 * Note that heartbeats also go through this same method, so need to not add the same data to them twice */
		if (isPrimaryThread() &&
			destination.equals("*") &&
			!channel.equals(NetworkRelayAPI.HEARTBEAT_CHANNEL)) {
			Instant now = Instant.now();
			// * 500 because of converting seconds to milliseconds (*1000) divided by 2 (half the heartbeat interval as threshold to send early)
			if (now.minusMillis(mHeartbeatInterval * 500L).compareTo(mLastHeartbeat) >= 0) {
				mLogger.finer("Adding heartbeat data to broadcast message instead of sending heartbeat");
				addHeartbeatDataToMessage(root);
				mLastHeartbeat = now;
			}
		}

		try {
			byte[] msg = mGson.toJson(root).getBytes(StandardCharsets.UTF_8);

			if (destination.equals("*")) {
				/* Broadcast message - send to the broadcast exchange to route to all queues */
				mChannel.basicPublish(BROADCAST_EXCHANGE_NAME, "", properties, msg);
			} else {
				/* Non-broadcast message - send to the default exchange, routing to the appropriate queue */
				mChannel.basicPublish("", destination, properties, msg);
			}

			mLogger.finer("Sent message destination=" + destination + " channel=" + channel);
			mLogger.finest(() -> "content=" + mGson.toJson(root));
		} catch (Exception e) {
			throw new Exception(String.format("Error sending message destination=" + destination + " channel=" + channel), e);
		}
	}

	protected void sendExpiringNetworkMessage(String destination, String channel, JsonObject data, long ttlSeconds) throws Exception {
		if (ttlSeconds <= 0) {
			throw new Exception("ttlSeconds must be a positive integer");
		}
		AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
			.expiration(Long.toString(ttlSeconds * 1000))
			.build();
		mAbstraction.sendNetworkMessage(destination, channel, data, properties);
	}

	protected Set<String> getOnlineShardNames() {
		Set<String> newSet = Collections.newSetFromMap(new ConcurrentSkipListMap<>());
		newSet.addAll(mDestinationLastHeartbeat.keySet());
		return newSet;
	}

	protected Set<String> getOnlineDestinationTypes() {
		return new HashSet<>(mDestinationTypes.values());
	}

	protected @Nullable String getOnlineDestinationType(String destination) {
		return mDestinationTypes.get(destination);
	}

	protected Set<String> getOnlineDestinationsOfType(String type) {
		return mDestinationTypes
			.entrySet()
			.stream()
			.filter(entry -> entry.getValue().equals(type))
			.map(Map.Entry::getKey)
			.collect(Collectors.toSet());
	}

	protected Map<String, JsonObject> getOnlineShardHeartbeatData() {
		return mDestinationHeartbeatData;
	}

	private void sendHeartbeat() {
		try {
			JsonObject root = new JsonObject();
			addHeartbeatDataToMessage(root);
			/* A heartbeat message contains no data but this field is required */
			root.add("data", new JsonObject());

			/* Heartbeat messages are only allowed to be retained by the exchange for 5x their interval */
			AMQP.BasicProperties properties = new AMQP.BasicProperties.Builder()
				.expiration(Long.toString(mHeartbeatInterval * 1000L * 5L))
				.build();

			sendNetworkMessageInternal("*", NetworkRelayAPI.HEARTBEAT_CHANNEL, root, properties);
		} catch (Exception ex) {
			mLogger.warning("Failed to send heartbeat: " + ex.getMessage());
		}
	}

	private void addHeartbeatDataToMessage(JsonObject root) {
		root.add("pluginData", mAbstraction.gatherHeartbeatData());
		root.addProperty("online", true);
	}

	private void sendDestOfflineEvent(String dest) {
		mLogger.fine("Shard " + dest + " is offline");
		mAbstraction.sendDestOfflineEvent(dest);
	}

	private boolean isPrimaryThread() {
		//noinspection deprecation
		return mPrimaryThreadId == Thread.currentThread().getId();
	}

	public void setServerFinishedStarting() {
		mLogger.info("Server has finished starting, will start processing messages now");
		mServerFinishedStarting = true;
	}
}
