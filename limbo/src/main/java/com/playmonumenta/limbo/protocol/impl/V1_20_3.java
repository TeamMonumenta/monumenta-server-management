package com.playmonumenta.limbo.protocol.impl;

import com.playmonumenta.limbo.Connection;
import com.playmonumenta.limbo.ConnectionImpl;
import com.playmonumenta.limbo.network.PacketReader;
import com.playmonumenta.limbo.network.PacketWriter;
import com.playmonumenta.limbo.protocol.ProtocolHandler;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public class V1_20_3 implements ProtocolHandler {
	protected final ConnectionImpl connection;

	protected int clientboundConfigFinishedId() {
		return 2;
	}

	protected int serverboundConfigAckId() {
		return 2;
	}

	protected int clientboundJoinGamePacketId() {
		return 41;
	}

	protected void writeLoginPacket(PacketWriter writer) {
		writer.varInt(clientboundJoinGamePacketId())
			.i32(0).flag(false)
			.varInt(1).str("minecraft:overworld")
			.varInt(0).varInt(1).varInt(1)
			.flag(false).flag(true).flag(false)
			.str("minecraft:overworld")
			.str("minecraft:overworld")
			.i64(0)
			.i8((byte) 3).i8((byte) -1)
			.flag(false).flag(false).flag(false)
			.varInt(0);
	}

	public V1_20_3(ConnectionImpl connection) {
		this.connection = connection;
	}

	@Override
	public final void onLoginComplete() {
		connection.write(clientboundConfigFinishedId());
		connection.phase(Connection.Phase.CONFIGURATION);
	}

	@Override
	public final void handleConfigurationPacket(int id, PacketReader reader) {
		if (id == serverboundConfigAckId()) {
			connection.phase(Connection.Phase.PLAY);
			connection.write(this::writeLoginPacket);
		}
	}

	@Override
	public final void handlePlayPacket(int id, PacketReader reader) {
		// NOOP
	}

	@Override
	public void sendKeepAlive() {
		// NOOP
	}
}
