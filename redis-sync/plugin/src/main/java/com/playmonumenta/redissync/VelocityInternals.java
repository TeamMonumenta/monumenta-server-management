package com.playmonumenta.redissync;

import com.playmonumenta.redissync.utils.MMLog;
import com.velocitypowered.api.proxy.Player;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import org.jetbrains.annotations.Nullable;

/**
 * The Velocity internals a clean switch needs, found by name so nothing is compiled against a
 * particular Velocity build. Present since Velocity 3.3.0 (1.20.2 support); see ARCHITECTURE.md,
 * "The proxy: clean switches".
 */
final class VelocityInternals {
	private static final String CONNECTED_PLAYER = "com.velocitypowered.proxy.connection.client.ConnectedPlayer";
	private static final String MINECRAFT_CONNECTION = "com.velocitypowered.proxy.connection.MinecraftConnection";
	private static final String PLAY_HANDLER = "com.velocitypowered.proxy.connection.client.ClientPlaySessionHandler";

	private static @Nullable Method GET_CONNECTION = null;
	private static @Nullable Method EVENT_LOOP = null;
	private static @Nullable Method ACTIVE_HANDLER = null;
	private static @Nullable Method DO_SWITCH = null;
	private static @Nullable Method SEND_KEEPALIVE = null;

	private VelocityInternals() {
	}

	/** Looks everything up; returns whether clean switches can be done. Call once at startup. */
	static boolean check(ClassLoader loader) {
		try {
			Class<?> player = Class.forName(CONNECTED_PLAYER, false, loader);
			Class<?> connection = Class.forName(MINECRAFT_CONNECTION, false, loader);
			Class<?> playHandler = Class.forName(PLAY_HANDLER, false, loader);
			GET_CONNECTION = player.getMethod("getConnection");
			SEND_KEEPALIVE = player.getMethod("sendKeepAlive");
			EVENT_LOOP = connection.getMethod("eventLoop");
			ACTIVE_HANDLER = connection.getMethod("getActiveSessionHandler");
			DO_SWITCH = playHandler.getMethod("doSwitch");
			if (!CompletableFuture.class.isAssignableFrom(DO_SWITCH.getReturnType())) {
				throw new NoSuchMethodException("doSwitch returns " + DO_SWITCH.getReturnType());
			}
			return true;
		} catch (ReflectiveOperationException | LinkageError ex) {
			MMLog.severe("Velocity internals for clean server switches not found; switches will ask players to reconnect instead", ex);
			GET_CONNECTION = null;
			return false;
		}
	}

	static boolean available() {
		return GET_CONNECTION != null;
	}

	/**
	 * Detaches the player from their server and moves their client into configuration, on the client's
	 * event loop. Completes once the client has acknowledged; exceptionally if anything failed, after
	 * which the server connection may or may not be closed.
	 */
	@SuppressWarnings("NullAway") // Only called when available()
	static CompletableFuture<Void> detachToConfiguration(Player player) {
		CompletableFuture<Void> result = new CompletableFuture<>();
		try {
			Object connection = GET_CONNECTION.invoke(player);
			Executor loop = (Executor) EVENT_LOOP.invoke(connection);
			loop.execute(() -> {
				try {
					Object handler = ACTIVE_HANDLER.invoke(connection);
					if (handler == null || !DO_SWITCH.getDeclaringClass().isInstance(handler)) {
						throw new IllegalStateException("client is not in play (handler " + (handler == null ? "none" : handler.getClass().getSimpleName()) + ")");
					}
					CompletableFuture<?> acknowledged = (CompletableFuture<?>) DO_SWITCH.invoke(handler);
					acknowledged.whenComplete((ignored, ex) -> {
						if (ex != null) {
							result.completeExceptionally(ex);
						} else {
							result.complete(null);
						}
					});
				} catch (ReflectiveOperationException | RuntimeException ex) {
					result.completeExceptionally(ex);
				}
			});
		} catch (ReflectiveOperationException | RuntimeException ex) {
			result.completeExceptionally(ex);
		}
		return result;
	}

	@SuppressWarnings("NullAway") // Only called when available()
	static void sendKeepAlive(Player player) {
		try {
			SEND_KEEPALIVE.invoke(player);
		} catch (ReflectiveOperationException | RuntimeException ex) {
			MMLog.warning("Failed to send keepalive to " + player.getUsername() + ": " + ex);
		}
	}
}
