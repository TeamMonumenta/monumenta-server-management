package com.playmonumenta.limbo.network;

import java.util.function.Supplier;
import org.intellij.lang.annotations.PrintFormat;
import org.jetbrains.annotations.ApiStatus;

@ApiStatus.Internal
public final class ProtocolError extends RuntimeException {
	private ProtocolError(String message) {
		super(message);
	}

	public static ProtocolError of(@PrintFormat String msg, Object... args) {
		throw new ProtocolError(String.format(msg, args));
	}

	public static void check(boolean flag, Supplier<String> onError) {
		if (!flag) {
			throw new ProtocolError(onError.get());
		}
	}
}
