package com.playmonumenta.redissync;

import io.lettuce.core.AbstractRedisAsyncCommands;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.TransactionResult;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.async.RedisAsyncCommands;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import io.lettuce.core.resource.ClientResources;
import io.lettuce.core.resource.NettyCustomizer;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.ChannelOption;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

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

	public static final RedisCodec<String, String> STRING_STRING_CODEC = StringCodec.UTF8;
	public static final RedisCodec<String, byte[]> STRING_BYTE_CODEC = StringByteCodec.INSTANCE;

	@SuppressWarnings("NullAway") // Required to avoid many null checks, this class will always be instantiated if this plugin is loaded
	private static RedisAPI INSTANCE = null;

	private final RedisClient mRedisClient;
	private final ClientResources mClientResources;
	private final StatefulRedisConnection<String, String> mConnection;
	private final StatefulRedisConnection<String, byte[]> mStringByteConnection;
	private final ReentrantLock mLock = new ReentrantLock();
	private final ReentrantLock mBytesLock = new ReentrantLock();

	/**
	 * Exclusive, try-with-resources handle to a Redis connection.
	 *
	 * <p>Holds a lock that prevents other threads from interleaving commands on the
	 * shared connection for the lifetime of the block. Always obtain via
	 * {@link RedisAPI#borrow()} or {@link RedisAPI#borrowStringBytes()}.
	 *
	 * <pre>{@code
	 * try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
	 *     conn.hset("my:key", "field", "value").exceptionally(...);
	 * }
	 * }</pre>
	 *
	 * <p>WARNING: Do not store or use this object outside its try-with-resources block.
	 * Do not call {@code multi()}/{@code exec()} directly — use
	 * {@link RedisAPI#multi(Consumer)} instead.
	 */
	public static final class BorrowedCommands<K, V> extends AbstractRedisAsyncCommands<K, V> implements AutoCloseable {
		private final ReentrantLock mLock;

		private BorrowedCommands(StatefulRedisConnection<K, V> connection, RedisCodec<K, V> codec, ReentrantLock lock) {
			super(connection, codec);
			mLock = lock;
		}

		@Override
		public void close() {
			mLock.unlock();
		}
	}

	/**
	 * Borrows exclusive access to the String/String Redis connection.
	 * Must be used in a try-with-resources statement.
	 */
	public static BorrowedCommands<String, String> borrow() {
		RedisAPI api = INSTANCE;
		api.mLock.lock();
		return new BorrowedCommands<>(api.mConnection, STRING_STRING_CODEC, api.mLock);
	}

	/**
	 * Borrows exclusive access to the String/byte[] Redis connection.
	 * Must be used in a try-with-resources statement.
	 */
	public static BorrowedCommands<String, byte[]> borrowStringBytes() {
		RedisAPI api = INSTANCE;
		api.mBytesLock.lock();
		return new BorrowedCommands<>(api.mStringByteConnection, STRING_BYTE_CODEC, api.mBytesLock);
	}

	/**
	 * Executes a MULTI/EXEC transaction on the String/String connection.
	 *
	 * <p>Acquires the connection lock, issues MULTI, runs {@code block}, then issues
	 * EXEC automatically. The connection cannot escape the block and EXEC is
	 * guaranteed to be called (or DISCARD on exception).
	 *
	 * <pre>{@code
	 * RedisAPI.multi(conn -> {
	 *     conn.hset("my:key", "field", "a");
	 *     conn.hset("my:key", "field2", "b");
	 * }).whenComplete((result, ex) -> { ... });
	 * }</pre>
	 */
	public static CompletableFuture<TransactionResult> multi(Consumer<BorrowedCommands<String, String>> block) {
		RedisAPI api = INSTANCE;
		api.mLock.lock();
		try {
			BorrowedCommands<String, String> conn = new BorrowedCommands<>(api.mConnection, STRING_STRING_CODEC, api.mLock);
			conn.multi();
			block.accept(conn);
			return conn.exec().toCompletableFuture();
		} catch (Exception e) {
			api.mConnection.async().discard();
			throw e;
		} finally {
			api.mLock.unlock();
		}
	}

	/**
	 * Executes a MULTI/EXEC transaction on the String/byte[] connection.
	 *
	 * <p>Acquires the connection lock, issues MULTI, runs {@code block}, then issues
	 * EXEC automatically. The connection cannot escape the block and EXEC is
	 * guaranteed to be called (or DISCARD on exception).
	 */
	public static CompletableFuture<TransactionResult> multiStringBytes(Consumer<BorrowedCommands<String, byte[]>> block) {
		RedisAPI api = INSTANCE;
		api.mBytesLock.lock();
		try {
			BorrowedCommands<String, byte[]> conn = new BorrowedCommands<>(api.mStringByteConnection, STRING_BYTE_CODEC, api.mBytesLock);
			conn.multi();
			block.accept(conn);
			return conn.exec().toCompletableFuture();
		} catch (Exception e) {
			api.mStringByteConnection.async().discard();
			throw e;
		} finally {
			api.mBytesLock.unlock();
		}
	}

	protected RedisAPI(String hostname, int port) {
		// OutOfDirectMemoryError workaround: https://github.com/redis/lettuce/issues/2590#issuecomment-1888683541
		mClientResources = ClientResources.builder()
			.nettyCustomizer(new NettyCustomizer() {
				@Override
				public void afterBootstrapInitialized(Bootstrap bootstrap) {
					bootstrap.option(ChannelOption.ALLOCATOR, new PooledByteBufAllocator(false));
				}
			}).build();
		mRedisClient = RedisClient.create(mClientResources, RedisURI.Builder.redis(hostname, port).build());
		mConnection = mRedisClient.connect();
		mStringByteConnection = mRedisClient.connect(StringByteCodec.INSTANCE);

		INSTANCE = this;
	}

	protected void shutdown() {
		mConnection.close();
		mStringByteConnection.close();
		mRedisClient.shutdown();
		mClientResources.shutdown();
	}

	public static RedisAPI getInstance() {
		return INSTANCE;
	}

	/** @deprecated Use {@link #borrow()} instead. */
	@Deprecated
	public RedisAsyncCommands<String, String> async() {
		return mConnection.async();
	}

	/** @deprecated Use {@link #borrowStringBytes()} instead. */
	@Deprecated
	public RedisAsyncCommands<String, byte[]> asyncStringBytes() {
		return mStringByteConnection.async();
	}

	public boolean isReady() {
		return mConnection.isOpen() && mStringByteConnection.isOpen();
	}
}
