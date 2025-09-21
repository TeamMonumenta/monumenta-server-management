package com.playmonumenta.limbo.api;

public record Handshake(int protocol, String addr, short port, Intent intent) {
	public enum Intent {
		STATUS,
		LOGIN,
		TRANSFER
	}
}
