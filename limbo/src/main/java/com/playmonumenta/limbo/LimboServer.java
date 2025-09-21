package com.playmonumenta.limbo;

import java.io.IOException;
import java.net.SocketAddress;

public interface LimboServer {
	static LimboServer start(SocketAddress address, ServerFlags flags) throws IOException {
		final var server = new LimboServerImpl(flags);

		try {
			server.start(address);
			return server;
		} catch (Throwable e) {
			server.stop();
			throw e;
		}
	}

	void stop() throws IOException;
}
