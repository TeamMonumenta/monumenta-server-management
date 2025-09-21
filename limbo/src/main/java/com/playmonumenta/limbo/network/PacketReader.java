package com.playmonumenta.limbo.network;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.jetbrains.annotations.ApiStatus;

import static com.playmonumenta.limbo.network.ProtocolError.check;

@ApiStatus.Internal
public record PacketReader(ByteBuffer buffer) {
	private static final int MAX_BYTE_PER_CHAR = (int) StandardCharsets.UTF_8.newEncoder().maxBytesPerChar();

	private String decodeStr(int len) {
		if (len == 0) {
			return "";
		}

		final byte[] array;
		final int offset;

		if (buffer.hasArray()) {
			array = buffer.array();
			offset = buffer.arrayOffset() + buffer.position();
			buffer.position(buffer.position() + len);
		} else {
			array = new byte[len];
			offset = 0;
			buffer.get(array, 0, len);
		}

		return new String(array, offset, len, StandardCharsets.UTF_8);
	}

	public byte i8() {
		return buffer.get();
	}

	public short i16() {
		return buffer.getShort();
	}

	public int i32() {
		return buffer.get();
	}

	public long i64() {
		return buffer.getLong();
	}

	public int varInt() {
		int res = 0;
		for (int i = 0; true; i++) {
			check(i < 5, () -> "varint too long");

			final var b = i8();
			res |= (b & Leb.MASK) << (i * Leb.SHIFT);
			if ((b & Leb.NEXT) == 0) {
				break;
			}
		}

		return res;
	}

	public String str(int maxLength) {
		final var maxByteLen = MAX_BYTE_PER_CHAR * maxLength;
		final var bufLen = varInt();
		check(bufLen <= maxByteLen, () -> "buf too long (%d > %d)".formatted(bufLen, maxByteLen));
		check(bufLen >= 0, () -> "buf length is negative: %s".formatted(bufLen));
		final var remaining = buffer.remaining();
		check(bufLen <= remaining, () -> "not enough bytes left: %s".formatted(bufLen));
		final var str = decodeStr(bufLen);
		check(str.length() <= maxLength, () -> "string too long (%d > %d)".formatted(str.length(), maxLength));
		return str;
	}

	public UUID uuid() {
		return new UUID(i64(), i64());
	}
}
