package com.playmonumenta.networkchat.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Dedicated chat-only logger. Routing (file path, rotation, console mirror) is owned by
 * the server's log4j2 config under the logger name {@code NetworkChatLog}.
 */
public final class ChatLogger {
	private static final Logger LOGGER = LogManager.getLogger("NetworkChatLog");

	private ChatLogger() {
	}

	public static void log(String message) {
		LOGGER.info(message);
	}
}
