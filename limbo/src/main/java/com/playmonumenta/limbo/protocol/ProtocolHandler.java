package com.playmonumenta.limbo.protocol;

import com.playmonumenta.limbo.ConnectionImpl;
import com.playmonumenta.limbo.network.PacketReader;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public interface ProtocolHandler {
	void onLoginComplete();

	void handleConfigurationPacket(int id, PacketReader reader);

	void handlePlayPacket(int id, PacketReader reader);

	interface Factory {
		ProtocolHandler create(ConnectionImpl connection);
	}

	void sendKeepAlive();
}
