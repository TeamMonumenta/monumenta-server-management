package com.playmonumenta.redissync;

import com.playmonumenta.redissync.utils.MMLog;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.event.player.ServerPreConnectEvent;
import com.velocitypowered.api.proxy.ConnectionRequestBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.messages.MinecraftChannelIdentifier;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;
import io.lettuce.core.RedisFuture;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.jetbrains.annotations.Nullable;

/**
 * Makes every server switch away from a server a clean one: the player is detached from it (which
 * saves them there as for any logout) and held in configuration until their session lease is
 * released, and only then connected to the next server. Also reconnects a player to their own
 * server when it asks, for data handoffs. See ARCHITECTURE.md, "The proxy: clean switches".
 */
public class CleanSwitchListener {
	/** Sent by a shard over a player's connection to have the proxy reconnect them to that shard */
	public static final String RECONNECT_CHANNEL = "redissync:reconnect";
	private static final MinecraftChannelIdentifier RECONNECT_ID = MinecraftChannelIdentifier.from(RECONNECT_CHANNEL);
	private static final long ACKNOWLEDGE_TIMEOUT_SECONDS = 10;
	/* Past the lease TTL, so a lease no one renews is gone by then */
	private static final long LEASE_WAIT_MILLIS = SessionLease.TTL_MILLIS + 5_000;
	private static final long LEASE_POLL_MAX_MILLIS = 200;

	private final MonumentaRedisSyncVelocity mPlugin;
	/* Players being switched, and the one server each may connect to next */
	private final Map<UUID, Switch> mSwitches = new ConcurrentHashMap<>();

	private static final class Switch {
		private volatile @Nullable RegisteredServer mConnecting = null;
		private @Nullable ScheduledTask mKeepAlive = null;
	}

	public CleanSwitchListener(MonumentaRedisSyncVelocity plugin) {
		mPlugin = plugin;
		plugin.mServer.getChannelRegistrar().register(RECONNECT_ID);
	}

	/* Last, so the destination is whatever other plugins made it */
	@Subscribe(priority = Short.MIN_VALUE)
	public void serverPreConnect(ServerPreConnectEvent event) {
		Player player = event.getPlayer();
		RegisteredServer target = event.getResult().getServer().orElse(null);
		if (target == null) {
			return;
		}
		Switch existing = mSwitches.get(player.getUniqueId());
		if (existing != null) {
			denyUnlessOurs(event, player, existing, target);
			return;
		}
		ServerConnection current = player.getCurrentServer().orElse(null);
		if (current == null || current.getServer().equals(target)) {
			return;
		}
		event.setResult(ServerPreConnectEvent.ServerResult.denied());
		Switch sw = new Switch();
		existing = mSwitches.putIfAbsent(player.getUniqueId(), sw);
		if (existing != null) {
			/* Another pre-connect for this player started a switch meanwhile */
			denyUnlessOurs(event, player, existing, target);
			return;
		}
		start(player, sw, current.getServer(), target);
	}

	private static void denyUnlessOurs(ServerPreConnectEvent event, Player player, Switch sw, RegisteredServer target) {
		if (!target.equals(sw.mConnecting)) {
			MMLog.info(() -> "Denying connection of " + player.getUsername() + " to " + target.getServerInfo().getName() + ": a clean switch is under way");
			event.setResult(ServerPreConnectEvent.ServerResult.denied());
		} else {
			event.setResult(ServerPreConnectEvent.ServerResult.allowed(target));
		}
	}

	/* Never forwarded to the client, and only taken from a server */
	@Subscribe
	public void pluginMessage(PluginMessageEvent event) {
		if (!event.getIdentifier().equals(RECONNECT_ID)) {
			return;
		}
		event.setResult(PluginMessageEvent.ForwardResult.handled());
		if (!(event.getSource() instanceof ServerConnection connection)) {
			return;
		}
		Player player = connection.getPlayer();
		Switch sw = new Switch();
		if (mSwitches.putIfAbsent(player.getUniqueId(), sw) != null) {
			MMLog.warning("Ignoring reconnect of " + player.getUsername() + ": a clean switch is under way");
			return;
		}
		start(player, sw, connection.getServer(), connection.getServer());
	}

	@Subscribe
	public void disconnect(DisconnectEvent event) {
		Switch sw = mSwitches.remove(event.getPlayer().getUniqueId());
		if (sw != null) {
			MMLog.info(() -> "Player " + event.getPlayer().getUsername() + " disconnected during a clean switch");
			stopKeepAlive(sw);
		}
	}

	private void start(Player player, Switch sw, RegisteredServer from, RegisteredServer target) {
		String name = player.getUsername();
		String fromName = from.getServerInfo().getName();
		String targetName = target.getServerInfo().getName();
		long startNanos = System.nanoTime();
		MMLog.info(() -> "Clean switch of " + name + " from " + fromName + " to " + targetName);

		if (!VelocityInternals.available()) {
			reconnectInstead(player, sw, target);
			return;
		}

		VelocityInternals.detachToConfiguration(player)
			.orTimeout(ACKNOWLEDGE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.whenComplete((ignored, ex) -> {
				if (ex != null) {
					MMLog.severe("Clean switch of " + name + " to " + targetName + " could not detach them from " + fromName, ex);
					if (player.getCurrentServer().isPresent()) {
						/* Nothing happened: they are still playing where they were */
						end(player, sw);
					} else {
						reconnectInstead(player, sw, target);
					}
					return;
				}
				MMLog.debug(() -> "Detached " + name + " from " + fromName + " in " + elapsedMillis(startNanos) + "ms");
				sw.mKeepAlive = mPlugin.mServer.getScheduler().buildTask(mPlugin, () -> VelocityInternals.sendKeepAlive(player))
					.repeat(1, TimeUnit.SECONDS).schedule();
				awaitLeaseReleased(player)
					.thenCompose(ignored2 -> connect(player, sw, target))
					.thenCompose(connected -> {
						if (connected || !player.isActive()) {
							return CompletableFuture.completedFuture(connected);
						}
						MMLog.warning("Clean switch of " + name + " to " + targetName + " failed; sending them back to " + fromName);
						return connect(player, sw, from).thenApply(returned -> {
							if (returned) {
								player.sendMessage(Component.text("Could not transfer you to " + targetName + "; you are back on " + fromName + ".", NamedTextColor.RED));
							}
							return returned;
						});
					}).whenComplete((connected, connectEx) -> {
						end(player, sw);
						if (!player.isActive()) {
							return;
						}
						if (connectEx != null || !Boolean.TRUE.equals(connected)) {
							if (connectEx != null) {
								MMLog.severe("Clean switch of " + name + " failed, and so did returning them to " + fromName + "; disconnecting them", connectEx);
							} else {
								MMLog.severe("Clean switch of " + name + " failed, and so did returning them to " + fromName + "; disconnecting them");
							}
							player.disconnect(Component.text("Transferring to " + targetName + " failed. Please reconnect.", NamedTextColor.RED));
						} else {
							MMLog.info(() -> "Clean switch of " + name + " done in " + elapsedMillis(startNanos) + "ms, now on "
								+ player.getCurrentServer().map(s -> s.getServerInfo().getName()).orElse("?"));
						}
					});
			});
	}

	/* Completes once no session holds the player's lease, or after LEASE_WAIT_MILLIS; the target's login then decides */
	private CompletableFuture<Void> awaitLeaseReleased(Player player) {
		CompletableFuture<Void> released = new CompletableFuture<>();
		pollLease(player, SessionLease.key(player.getUniqueId()), System.nanoTime(), 10, released);
		return released;
	}

	private void pollLease(Player player, String key, long startNanos, long nextDelayMillis, CompletableFuture<Void> released) {
		RedisFuture<Long> exists;
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			exists = conn.exists(key);
		}
		exists.whenComplete((count, ex) -> {
			long waited = elapsedMillis(startNanos);
			if (ex != null) {
				MMLog.warning("Could not check the session lease of " + player.getUsername() + ", connecting anyway: " + ex);
				released.complete(null);
			} else if (count == null || count == 0) {
				if (waited >= 1000) {
					MMLog.info(() -> "Session lease of " + player.getUsername() + " released after " + waited + "ms");
				} else {
					MMLog.debug(() -> "Session lease of " + player.getUsername() + " released after " + waited + "ms");
				}
				released.complete(null);
			} else if (!player.isActive()) {
				released.complete(null);
			} else if (waited >= LEASE_WAIT_MILLIS) {
				MMLog.warning("Session lease of " + player.getUsername() + " still held after " + waited + "ms; connecting anyway");
				released.complete(null);
			} else {
				mPlugin.mServer.getScheduler().buildTask(mPlugin, () -> pollLease(player, key, startNanos, Math.min(nextDelayMillis * 2, LEASE_POLL_MAX_MILLIS), released))
					.delay(nextDelayMillis, TimeUnit.MILLISECONDS).schedule();
			}
		});
	}

	/* Connects a player who has no current server; completes with whether they got there */
	private CompletableFuture<Boolean> connect(Player player, Switch sw, RegisteredServer server) {
		if (!player.isActive()) {
			return CompletableFuture.completedFuture(false);
		}
		sw.mConnecting = server;
		return player.createConnectionRequest(server).connect().handle((result, ex) -> {
			sw.mConnecting = null;
			if (ex != null) {
				MMLog.warning("Connecting " + player.getUsername() + " to " + server.getServerInfo().getName() + " failed: " + ex);
				return false;
			}
			if (result.getStatus() != ConnectionRequestBuilder.Status.SUCCESS) {
				MMLog.warning("Connecting " + player.getUsername() + " to " + server.getServerInfo().getName() + " failed: " + result.getStatus()
					+ result.getReasonComponent().map(reason -> " " + PlainTextComponentSerializer.plainText().serialize(reason)).orElse(""));
				return false;
			}
			return true;
		});
	}

	/* Without the internals: make the target the player's login server, and ask them to reconnect */
	private void reconnectInstead(Player player, Switch sw, RegisteredServer target) {
		String targetName = target.getServerInfo().getName();
		MMLog.warning("Asking " + player.getUsername() + " to reconnect to reach " + targetName);
		try (RedisAPI.BorrowedCommands<String, String> conn = RedisAPI.borrow()) {
			conn.hset(VelocityListener.locationsKey(), player.getUniqueId().toString(), targetName);
		}
		end(player, sw);
		player.disconnect(Component.text("Please reconnect to continue to " + targetName + ".", NamedTextColor.GOLD));
	}

	private void end(Player player, Switch sw) {
		mSwitches.remove(player.getUniqueId(), sw);
		stopKeepAlive(sw);
	}

	private static void stopKeepAlive(Switch sw) {
		ScheduledTask task = sw.mKeepAlive;
		if (task != null) {
			task.cancel();
		}
	}

	private static long elapsedMillis(long startNanos) {
		return (System.nanoTime() - startNanos) / 1_000_000;
	}
}
