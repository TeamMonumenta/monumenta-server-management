package com.playmonumenta.redissync;

import com.google.common.util.concurrent.Uninterruptibles;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisFuture;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.NettyCustomizer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Logger;

public class RedisAPI {
	private static final class StringByteCodec implements RedisCodec<String, byte[]> {
		private static final StringByteCodec INSTANCE = new StringByteCodec();
		private static final byte[] EMPTY = new byte[0];
		private final Charset mCharset = StandardCharsets.UTF_8;

		@Override
		public String decodeKey(final ByteBuffer bytes) {
			return mCharset.decode(bytes).toString();
		}

		@Override
		public byte[] decodeValue(final ByteBuffer bytes) {
			return getBytes(bytes);
		}

		@Override
		public ByteBuffer encodeKey(final String key) {
			return mCharset.encode(key);
		}

		@Override
		public ByteBuffer encodeValue(final byte[] value) {
			if (value == null) {
				return ByteBuffer.wrap(EMPTY);
			}

			return ByteBuffer.wrap(value);
		}

		private static byte[] getBytes(final ByteBuffer buffer) {
			final byte[] b = new byte[buffer.remaining()];
			buffer.get(b);
			return b;
		}
	}

	public static final RedisCodec<String, byte[]> STRING_BYTE_CODEC = StringByteCodec.INSTANCE;

	private final Logger mLogger;
	private final RedisConfig mRedisConfig;
	private final Executor mScheduler;
	private final RedisClient mRedisClient;
	private final ClientResources mClientResources;
	private final StatefulRedisConnection<String, String> mConnection;
	private final StatefulRedisConnection<String, byte[]> mStringByteConnection;
	private final ConcurrentHashMap<Long, StatefulRedisConnection<String, String>> mThreadStringStringConnections
		= new ConcurrentHashMap<>();
	private final ConcurrentHashMap<Long, StatefulRedisConnection<String, byte[]>> mThreadStringByteConnections
		= new ConcurrentHashMap<>();

	public RedisAPI(Logger logger, RedisConfig redisConfig, Executor scheduler) {
		mLogger = logger;
		mRedisConfig = redisConfig;
		mScheduler = scheduler;
		// OutOfDirectMemoryError workaround: https://github.com/redis/lettuce/issues/2590#issuecomment-1888683541
		mClientResources = ClientResources.builder()
			.nettyCustomizer(new NettyCustomizer() {
				@Override
				public void afterBootstrapInitialized(Bootstrap bootstrap) {
					bootstrap.option(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(false));
				}
			}).build();
		mRedisClient = RedisClient.create(mClientResources, RedisURI.Builder.redis(
			redisConfig.getRedisHost(), redisConfig.getRedisPort()
		).build());
		mConnection = mRedisClient.connect();
		mStringByteConnection = mRedisClient.connect(StringByteCodec.INSTANCE);

		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		mThreadStringStringConnections.put(threadId, mConnection);
		mThreadStringByteConnections.put(threadId, mStringByteConnection);
	}

	public void shutdown() {
		mConnection.close();
		mStringByteConnection.close();
		mRedisClient.shutdown();
		mClientResources.shutdown();
	}

	/**
	 * Opens a new autoClosable connection regardless of open connections
	 * Your code is responsible for closing this when it is done, ideally using a try with resources block
	 *
	 * @return A new connection that you are responsible for closing
	 */
	public <K, V> StatefulRedisConnection<K, V> openConnection(RedisCodec<K, V> codec) {
		Thread thread = Thread.currentThread();
		mLogger.info("Creating a new autocloseable connection on thread " + thread.getId());
		return mRedisClient.connect(codec);
	}

	/**
	 * Asynchronously waits for all specified task futures to complete, then closes the specified connection
	 *
	 * @param connection         A connection to be closed when all tasks are complete
	 * @param redisFutures       A collection of RedisFuture that must complete before closing the connection
	 * @param completableFutures A collection of CompletableFuture that must complete before closing the connection
	 */
	public void closeConnectionWhenDone(
		StatefulRedisConnection<?, ?> connection,
		Collection<RedisFuture<?>> redisFutures,
		Collection<CompletableFuture<?>> completableFutures
	) {
		mScheduler.execute(() -> {
			for (CompletableFuture<?> future : completableFutures) {
				try {
					future.join();
				} catch (CancellationException | CompletionException ignored) {
					// Exceptions are the responsibility of the calling code; just ensure the connection closes
				}
			}

			for (RedisFuture<?> future : redisFutures) {
				try {
					future.toCompletableFuture().join();
				} catch (CancellationException | CompletionException ignored) {
					// Exceptions are the responsibility of the calling code; just ensure the connection closes
				}
			}

			connection.close();
		});
	}

	/**
	 * Provides a connection that closes automagically when the executing thread terminates.
	 * If the current thread already has an open connection, that is returned instead.
	 * The main thread may be used as well, and is closed when the plugin is disabled.
	 *
	 * @return A connection associated with the current thread
	 */
	@Deprecated
	public StatefulRedisConnection<String, String> getMagicallyClosingStringStringConnection() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		mLogger.info("Magically closing connection request from thread " + thread.getId());
		return mThreadStringStringConnections.computeIfAbsent(threadId, k -> {
			StatefulRedisConnection<String, String> connection = mRedisClient.connect();
			mLogger.info("Created new magically closing connection request from " +
				"thread " + thread.getId());
			mScheduler.execute(() -> {
				Uninterruptibles.joinUninterruptibly(thread);
				mThreadStringStringConnections.remove(k, connection);
				connection.close();
				mLogger.info("Closed magically closing connection request from " +
					"thread " + thread.getId());
			});
			return connection;
		});
	}

	/**
	 * Provides a connection that closes automagically when the executing thread terminates.
	 * If the current thread already has an open connection, that is returned instead.
	 * The main thread may be used as well, and is closed when the plugin is disabled.
	 *
	 * @return A connection associated with the current thread
	 */
	@Deprecated
	public StatefulRedisConnection<String, byte[]> getMagicallyClosingStringByteConnection() {
		Thread thread = Thread.currentThread();
		long threadId = thread.getId();
		return mThreadStringByteConnections.computeIfAbsent(threadId, k -> {
			StatefulRedisConnection<String, byte[]> connection = mRedisClient.connect(STRING_BYTE_CODEC);
			mScheduler.execute(() -> {
				Uninterruptibles.joinUninterruptibly(thread);
				mThreadStringByteConnections.remove(k, connection);
				connection.close();
			});
			return connection;
		});
	}

	public RedisAsyncCommands<String, String> async() {
		return mConnection.async();
	}

	public RedisAsyncCommands<String, byte[]> asyncStringBytes() {
		return mStringByteConnection.async();
	}

	public boolean isReady() {
		return mConnection.isOpen() && mStringByteConnection.isOpen();
	}

	public RedisConfig getRedisConfig() {
		return mRedisConfig;
	}
}
