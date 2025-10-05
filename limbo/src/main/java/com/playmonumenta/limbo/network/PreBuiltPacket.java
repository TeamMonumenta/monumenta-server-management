package com.playmonumenta.limbo.network;

import java.nio.ByteBuffer;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public record PreBuiltPacket(ByteBuffer contents) {
	public static PreBuiltPacket build(Consumer<PacketWriter> action) {
		final var writer = new PacketWriter(ByteBuffer.allocate(8));
		action.accept(writer);
		final var buf = writer.buffer();
		buf.flip();
		final var hw = new PacketWriter(ByteBuffer.allocate(Leb.MAX_HEADER_SIZE + buf.remaining()));
		hw.varInt(buf.remaining());
		hw.buf(buf);

		return new PreBuiltPacket(hw.buffer());
	}

	public static PreBuiltPacket ofId(int id) {
		return build(writer -> writer.varInt(id));
	}
}
