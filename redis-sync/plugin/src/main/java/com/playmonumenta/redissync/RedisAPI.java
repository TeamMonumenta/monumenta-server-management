package com.playmonumenta.redissync;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
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

	public RedisAsyncCommands<String, String> async() {
		return mConnection.async();
	}

	public RedisAsyncCommands<String, byte[]> asyncStringBytes() {
		return mStringByteConnection.async();
	}

	public boolean isReady() {
		return mConnection.isOpen() && mStringByteConnection.isOpen();
	}
}
