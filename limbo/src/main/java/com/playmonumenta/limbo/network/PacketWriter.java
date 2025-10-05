package com.playmonumenta.limbo.network;

import com.playmonumenta.limbo.Util;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class PacketWriter {
	private ByteBuffer buffer;

	public PacketWriter(ByteBuffer buffer) {
		this.buffer = buffer;
	}

	public ByteBuffer buffer() {
		return buffer;
	}

	private void ensure(int size) {
		if (buffer.remaining() < size) {
			buffer = Util.realloc(buffer, buffer.capacity() + size);
		}
	}

	public PacketWriter flag(boolean flag) {
		i8((byte) (flag ? 1 : 0));
		return this;
	}

	public PacketWriter i8(byte arg) {
		ensure(1);
		buffer.put(arg);
		return this;
	}

	public PacketWriter i16(short arg) {
		ensure(2);
		buffer.putShort(arg);
		return this;
	}

	public PacketWriter i32(int arg) {
		ensure(4);
		buffer.putInt(arg);
		return this;
	}

	public PacketWriter i64(long arg) {
		ensure(8);
		buffer.putLong(arg);
		return this;
	}

	public PacketWriter f32(float arg) {
		ensure(4);
		buffer.putFloat(arg);
		return this;
	}

	public PacketWriter f64(double arg) {
		ensure(8);
		buffer.putDouble(arg);
		return this;
	}

	public PacketWriter buf(ByteBuffer arg) {
		ensure(arg.remaining());
		buffer.put(arg);
		return this;
	}

	public PacketWriter varInt(int arg) {
		if ((arg & (-1 << Leb.SHIFT)) == 0) {
			i8((byte) arg);
		} else if ((arg & (-1 << (2 * Leb.SHIFT))) == 0) {
			i16((short) ((arg & Leb.MASK | Leb.NEXT) << Byte.SIZE | (arg >>> Leb.SHIFT)));
		} else {
			while ((arg & ~Leb.MASK) != 0) {
				i8((byte) (arg & Leb.MASK | Leb.NEXT));
				arg >>>= Leb.SHIFT;
			}

			i8((byte) arg);
		}
		return this;
	}

	public PacketWriter str(String arg) {
		final var encBuf = StandardCharsets.UTF_8.encode(arg);
		varInt(encBuf.limit());
		ensure(encBuf.limit());
		buffer.put(encBuf);
		return this;
	}

	public PacketWriter uuid(UUID uuid) {
		i64(uuid.getMostSignificantBits());
		i64(uuid.getLeastSignificantBits());
		return this;
	}
}
