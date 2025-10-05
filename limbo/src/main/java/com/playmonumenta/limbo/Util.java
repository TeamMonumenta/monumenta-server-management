package com.playmonumenta.limbo;

import java.nio.ByteBuffer;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class Util {
	/**
	 * @param buffer     the buffer, must be in write mode
	 * @param targetSize the target size
	 * @return a buffer, in write mode
	 */
	public static ByteBuffer realloc(ByteBuffer buffer, int targetSize) {
		final var newBuf = ByteBuffer.allocate(Math.max(buffer.capacity() * 2, targetSize));
		buffer.flip();
		newBuf.put(buffer);
		return newBuf;
	}
}
