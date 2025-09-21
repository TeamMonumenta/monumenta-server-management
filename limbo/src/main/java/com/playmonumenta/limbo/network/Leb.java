package com.playmonumenta.limbo.network;

import com.playmonumenta.limbo.Util;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class Leb {
	static final int SHIFT = 7;
	static final int NEXT = 0x80;
	static final int MASK = 0x7f;
	static final int MAX_HEADER_SIZE = 3;

	public static class FrameDecoder {
		private final SocketChannel channel;
		private ByteBuffer buffer;
		private int requiredSize;

		public FrameDecoder(int initialBufferSize, SocketChannel channel) {
			this.channel = channel;
			buffer = ByteBuffer.allocate(initialBufferSize);
			this.requiredSize = initialBufferSize;
		}

		private boolean readSinglePacket(Consumer<ByteBuffer> handler) {
			// read length
			int packetSize = 0;
			for (int i = 0; true; i++) {
				ProtocolError.check(i < MAX_HEADER_SIZE, () -> "packet header too long");

				if (!buffer.hasRemaining()) {
					return false;
				}

				final var b = buffer.get();
				packetSize |= (b & MASK) << i * SHIFT;

				if ((b & NEXT) == 0) {
					break;
				}
			}

			if (packetSize > buffer.remaining()) {
				requiredSize = Math.max(requiredSize, MAX_HEADER_SIZE + packetSize);
				return false;
			}

			final var buf = buffer.slice(buffer.position(), packetSize);
			buffer.position(buffer.position() + packetSize);
			handler.accept(buf);
			return true;
		}

		public void read(Consumer<ByteBuffer> handler) throws IOException {
			if (channel.read(buffer) == -1) {
				throw new EOFException("Disconnected");
			}

			buffer.flip();

			while (true) {
				buffer.mark();
				if (!readSinglePacket(handler)) {
					buffer.reset();
					break;
				}
			}

			buffer.compact();

			// realloc if needed
			if (requiredSize > buffer.capacity()) {
				buffer = Util.realloc(buffer, requiredSize);
			}
		}
	}

	public static class FrameEncoder {
		private final SocketChannel channel;
		private ByteBuffer buffer;
		private final ByteBuffer headerScratch = ByteBuffer.allocate(MAX_HEADER_SIZE);
		private final ByteBuffer[] sgVector = new ByteBuffer[]{headerScratch, null};

		public FrameEncoder(int initialBufferSize, SocketChannel channel) {
			this.channel = channel;
			buffer = ByteBuffer.allocate(initialBufferSize);
		}

		private void writeHeader(int size) {
			headerScratch.position(0);
			headerScratch.limit(headerScratch.capacity());

			for (int i = 0; (size & ~Leb.MASK) != 0; i++) {
				ProtocolError.check(i < 2, () -> "tried writing oversize packet");
				headerScratch.put((byte) (size & Leb.MASK | Leb.NEXT));
				size >>>= Leb.SHIFT;
			}

			headerScratch.put((byte) size);
			headerScratch.flip();
		}

		public void write(UnaryOperator<ByteBuffer> action) throws IOException {
			buffer.limit(buffer.capacity());
			buffer.position(0);
			sgVector[1] = this.buffer = action.apply(buffer);
			buffer.flip();
			writeHeader(buffer.limit());
			channel.write(sgVector);
		}
	}
}
