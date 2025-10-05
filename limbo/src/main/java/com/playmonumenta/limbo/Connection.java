package com.playmonumenta.limbo;

public interface Connection {
	enum Phase {
		HANDSHAKE,
		STATUS,
		LOGIN,
		CONFIGURATION,
		PLAY
	}
}
