package com.playmonumenta.limbo.api;

import com.playmonumenta.limbo.Connection;

public interface LimboEventHandler {
	default void disconnected() {
	}

	default void handshake(Handshake packet) {
	}

	default void uncaughtException(Throwable e) {
	}

	default void loginStart(LoginInfo loginInfo) {

	}

	default void newPhase(Connection connection, Connection.Phase phase) {

	}
}
