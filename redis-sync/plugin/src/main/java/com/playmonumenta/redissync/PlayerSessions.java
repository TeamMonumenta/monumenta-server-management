package com.playmonumenta.redissync;

import com.destroystokyo.paper.event.player.PlayerConnectionCloseEvent;
import com.destroystokyo.paper.profile.PlayerProfile;
import com.google.gson.JsonObject;
import com.playmonumenta.redissync.adapters.VersionAdapter;
import com.playmonumenta.redissync.adapters.VersionAdapter.ReturnParams;
import com.playmonumenta.redissync.data.ContentData;
import com.playmonumenta.redissync.utils.MMLog;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.Nullable;

/**
 * Every player's {@link PlayerSession} on this shard, and the login and disconnect handling that
 * starts and ends them. This decides whether a load event may load and a save event may save;
 * {@link DataEventListener} does the loading and saving.
 *
 * <p>A login starts at AsyncPlayerPreLoginEvent, which waits for the player's previous session
 * here to finish (its connection closed, and every save it made committed), so the new one
 * loads the newest data. The session starts at the first load event, saves from the tick after
 * PlayerJoinEvent, and ends at PlayerConnectionCloseEvent, which Paper fires after the final save.
 * The ended session stays here until its saves have committed, for a reconnect to wait on.
 *
 * <p>The close event carries only a UUID, which is ambiguous only when a second connection for
 * the account gets past pre-login while the first is still here, which Velocity never causes.
 * The second connection is refused, and its close is told apart from the first's where possible;
 * where not, the first is refused too. The worst case is a refused login, never unsaved play.
 *
 * <p>The static methods are the entry points for the rest of the plugin; the instance methods
 * serve {@link DataEventListener} and {@link SessionLock}.
 */
public final class PlayerSessions implements Listener {
	private static final Component LOAD_ERROR_MSG =
		Component.text("Critical error occurred when loading playerdata! Please notify a moderator.", NamedTextColor.RED);
	@SuppressWarnings("NullAway") // Always set while the plugin is enabled
	private static PlayerSessions INSTANCE = null;

	private final Plugin mPlugin;
	private final VersionAdapter mAdapter;
	/* Each player's current session; an ended one until its saves have committed */
	private final ConcurrentMap<UUID, PlayerSession> mSessions = new ConcurrentHashMap<>();

	/*
	 * Players let through pre-login whose session has not started yet, until when they are expected.
	 * Only these start a session: NPC plugins construct fake players, which fire load events but
	 * never a connection close, so their session would never end. A login that drops before loading
	 * leaves no close event either, so the expectation expires rather than letting a later fake
	 * player with the UUID start a session that never ends. Guarded by this.
	 */
	private final Map<UUID, Long> mExpectedLogins = new HashMap<>();
	private long mExpectedLoginTimeoutMillis = 60_000;
	/* Offline data writes in flight; one and a login for the same player never both go ahead. Claimed under this */
	private final ConcurrentMap<UUID, CompletableFuture<?>> mOfflineWrites = new ConcurrentHashMap<>();
	/* Player data writes outside any session (location setters, stashes, offline edits), for shutdown to wait on */
	private final Set<CompletableFuture<?>> mOtherWrites = ConcurrentHashMap.newKeySet();
	/* Shoulder entities of locked players, which SessionLock registers */
	private final ConcurrentMap<UUID, SessionLock> mLockedShoulderEntities = new ConcurrentHashMap<>();

	PlayerSessions(Plugin plugin, VersionAdapter adapter) {
		mPlugin = plugin;
		mAdapter = adapter;
		INSTANCE = this;

		/* Anyone online as the plugin starts (it was reloaded under them) has no session, so would never be saved */
		if (!BukkitConfigAPI.getSavingDisabled() && !Bukkit.getOnlinePlayers().isEmpty()) {
			MMLog.severe("Players are online as the plugin starts; kicking them, as their data would not be saved");
			Bukkit.getScheduler().runTask(plugin, () -> {
				for (Player player : Bukkit.getOnlinePlayers()) {
					if (current(player) == null) {
						player.kick(LOAD_ERROR_MSG);
					}
				}
			});
		}
	}

	Plugin getPlugin() {
		return mPlugin;
	}

	ConcurrentMap<UUID, SessionLock> getLockedShoulderEntities() {
		return mLockedShoulderEntities;
	}

	/** Schedules work on the main thread from any thread, unless the plugin is being disabled, when there is no one left to do it for */
	static void runOnMainThread(Runnable task) {
		if (!INSTANCE.mPlugin.isEnabled()) {
			MMLog.debug("Dropping main thread task scheduled while the plugin is disabled");
			return;
		}
		Bukkit.getScheduler().runTask(INSTANCE.mPlugin, task);
	}

	/* ******************* Finding sessions ******************* */

	private @Nullable PlayerSession active(UUID uuid) {
		PlayerSession session = mSessions.get(uuid);
		return session == null || session.isEnded() ? null : session;
	}

	/** The open session for this exact Player object, or null if there is none or it is another connection's */
	@Nullable PlayerSession current(Player player) {
		PlayerSession session = active(player.getUniqueId());
		return session != null && session.belongsTo(player) ? session : null;
	}

	/**
	 * The session a connection's load event should load into. Paper fires the advancement load
	 * first, as the player is constructed, and the playerdata load later, so either may start the
	 * session; a datapack reload re-fires the advancement load for a player already playing.
	 *
	 * <p>Only a connection let through pre-login gets a session that is kept. One that must not load
	 * gets a failed session, and is kicked.
	 */
	PlayerSession sessionForLoad(Player player) {
		UUID uuid = player.getUniqueId();
		PlayerSession existing = mSessions.get(uuid);
		if (existing != null && existing.belongsTo(player)) {
			/* Possibly ended, if another connection's close (which carries only the UUID) was taken for this one's: then it does not load */
			return existing;
		}
		if (existing != null && !existing.isEnded()) {
			existing.refusedLoad(player);
			return refuse(player, "another connection for this account is already loaded here");
		}

		Long expectedUntil = expectedLogin(uuid);
		if (expectedUntil == null) {
			/* A fake player from an NPC plugin, say. It loads as usual, but its session is not kept, so it is never saved */
			MMLog.debug(() -> "Not keeping a session for " + player.getName() + " uuid=" + uuid + ", which never went through pre-login");
			return new PlayerSession(player);
		}
		if (existing != null && existing.hasPendingSaves()) {
			/* Pre-login waits for these, so this is a bug; the previous session stays, for the next login to wait on */
			return refuse(player, "the previous session's saves are still in flight, so loading now would read stale data");
		}
		PlayerSession session = new PlayerSession(player);
		/* Stored before the expectation is removed, so an offline write always sees one or the other */
		mSessions.put(uuid, session);
		synchronized (this) {
			mExpectedLogins.remove(uuid, expectedUntil);
		}
		return session;
	}

	private PlayerSession refuse(Player player, String reason) {
		PlayerSession session = new PlayerSession(player);
		abortLoad(session, reason);
		return session;
	}

	/**
	 * Fails a load that cannot safely complete, so nothing the session holds is ever saved over the
	 * player's good data, and kicks the player. The session ends when the connection closes.
	 */
	void abortLoad(PlayerSession session, String reason) {
		Player player = session.getPlayer();
		MMLog.severe("Aborting load for player=" + player.getName() + " uuid=" + player.getUniqueId() + ": " + reason + ". Kicking to prevent data loss!");
		session.fail();
		runOnMainThread(() -> player.kick(LOAD_ERROR_MSG));
	}

	/** The session a save event should save into, or null if it must be skipped */
	@Nullable PlayerSession sessionForSave(Player player, String what) {
		String playerName = player.getName();
		PlayerSession session = current(player);
		if (session == null) {
			MMLog.warning("Skipping " + what + " save for player=" + playerName + " because this connection has no session on this shard");
			return null;
		}
		if (session.canSave()) {
			return session;
		}
		switch (session.getState()) {
			case LOADING -> MMLog.debug("Skipping " + what + " save for player=" + playerName + " because their playerdata is still loading");
			case FAILED -> MMLog.warning("Skipping " + what + " save for player=" + playerName + " because their playerdata failed to load");
			case PLAYING -> {
				SessionLock lock = session.getLock();
				if (lock != null && lock.isHandedOff()) {
					MMLog.warning("Skipping " + what + " save for player=" + playerName + " because their data has been replaced and they must rejoin");
					runOnMainThread(lock::kickHandedOff);
				} else {
					MMLog.debug("Skipping " + what + " save for player=" + playerName + " because their data is moving elsewhere");
				}
			}
			default -> throw new IllegalStateException("current() never returns an ended session");
		}
		return null;
	}

	/* ******************* Login ******************* */

	/*
	 * The first event of a login, on an async thread before anything loads: the one place a login
	 * can wait for the player's previous connection here to be over.
	 */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
	public void waitForPreviousSession(AsyncPlayerPreLoginEvent event) {
		if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			return;
		}
		PlayerProfile profile = event.getPlayerProfile();
		UUID uuid = profile.getId();
		if (uuid == null) {
			MMLog.warning(() -> "A player uuid=null name=" + profile.getName() + " tried to login without a UUID! Preventing duplicate uuid stupidity");
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.translatable("multiplayer.disconnect.unverified_username"));
			return;
		}

		/*
		 * A player reconnecting straight after leaving can get here before their previous connection
		 * has closed, or before its final save has committed. Loading now would hand them the save
		 * before that one, which they would then save back over their progress.
		 *
		 * If the previous connection is still open (the client dropped and came back before this
		 * server noticed), the newest login wins, as in vanilla. It is closed here, before this login
		 * loads anything, so that its final save is what this login gets.
		 */
		PlayerSession previous = mSessions.get(uuid);
		if (previous != null) {
			if (!previous.isEnded()) {
				MMLog.info(() -> "Login for " + profile.getName() + " while their previous connection is still open here; closing it");
				runOnMainThread(() -> supersede(previous));
			}
			long startTime = System.currentTimeMillis();
			try {
				previous.finished().get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
				MMLog.info(() -> "Login for " + profile.getName() + " waited " + (System.currentTimeMillis() - startTime) + "ms for their previous session to finish saving");
			} catch (InterruptedException | ExecutionException | TimeoutException ex) {
				MMLog.warning(() -> "Refusing login for uuid=" + uuid + " name=" + profile.getName() + ": their previous connection here did not close and finish saving in time");
				event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.translatable("multiplayer.disconnect.duplicate_login"));
				return;
			}
		}

		if (Bukkit.getPlayer(uuid) != null) {
			MMLog.warning(() -> "A player uuid=" + uuid + " name=" + profile.getName() + " tried to login while online here! Preventing duplicate uuid stupidity");
			event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, Component.translatable("multiplayer.disconnect.duplicate_login"));
		}
	}

	/*
	 * Closes a previous connection for a player who is logging in again. One playing is kicked, which
	 * saves it as any disconnect does. One still loading has nothing worth saving and may not be
	 * kickable yet (there is no game connection during configuration), so it is failed: it never
	 * saves, and is kicked as soon as it joins. Either way its session ends when the connection
	 * closes, which the new login is waiting for.
	 */
	private void supersede(PlayerSession session) {
		if (session.isEnded()) {
			return;
		}
		if (session.getState() == PlayerSession.State.LOADING) {
			session.fail();
		}
		session.getPlayer().kick(Component.translatable("multiplayer.disconnect.duplicate_login"), PlayerKickEvent.Cause.DUPLICATE_LOGIN);
	}

	/* Last, once nothing else will refuse the login: from here its load events may start a session */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void expectLogin(AsyncPlayerPreLoginEvent event) {
		UUID uuid = event.getPlayerProfile().getId();
		if (uuid == null || event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
			return;
		}
		CompletableFuture<?> offlineWrite;
		synchronized (this) {
			mExpectedLogins.put(uuid, System.currentTimeMillis() + mExpectedLoginTimeoutMillis);
			offlineWrite = mOfflineWrites.get(uuid);
		}
		if (offlineWrite != null) {
			try {
				offlineWrite.get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
			} catch (InterruptedException | ExecutionException | TimeoutException ex) {
				MMLog.warning(() -> "Login for uuid=" + uuid + " gave up waiting for an offline data write to finish");
			}
		}
	}

	/* When this player is expected to start loading by, or null if they are not, or no longer */
	private synchronized @Nullable Long expectedLogin(UUID uuid) {
		Long expectedUntil = mExpectedLogins.get(uuid);
		if (expectedUntil != null && expectedUntil < System.currentTimeMillis()) {
			mExpectedLogins.remove(uuid);
			return null;
		}
		return expectedUntil;
	}

	/*
	 * Refuses a connection that must not play: its session failed, or ended because another
	 * connection's close was taken for its own, or it is a second connection for an account already
	 * here. The advancement load runs before this event, while the player has no game connection,
	 * so a kick would do nothing.
	 */
	@EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
	public void refuseFailedLogin(PlayerLoginEvent event) {
		Player player = event.getPlayer();
		PlayerSession session = mSessions.get(player.getUniqueId());
		if (session == null) {
			return;
		}
		boolean refused = session.belongsTo(player) ? !session.isLive() : session.takeRefusedLogin(player);
		if (refused) {
			event.disallow(PlayerLoginEvent.Result.KICK_OTHER, LOAD_ERROR_MSG);
		}
	}

	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void playerJoinEvent(PlayerJoinEvent event) {
		Player player = event.getPlayer();
		PlayerSession session = current(player);
		if (session == null) {
			if (!BukkitConfigAPI.getSavingDisabled()) {
				MMLog.severe("Player " + player.getName() + " joined without a session; kicking, as their data would not be saved");
				player.kick(LOAD_ERROR_MSG);
			}
		} else if (session.getState() == PlayerSession.State.FAILED) {
			/* The kick scheduled when the load failed did nothing if it ran before the player had a game connection */
			player.kick(LOAD_ERROR_MSG);
		} else {
			/*
			 * Other plugins load their own state in their join handlers, and a save before they have
			 * would write their empty state. Their MONITOR handlers may still run after this one, so
			 * saving starts on the next tick, once the join event is certainly over.
			 */
			Bukkit.getScheduler().runTask(mPlugin, session::joined);
		}
	}

	/* ******************* Disconnect ******************* */

	/*
	 * The end of every connection past pre-login, including ones that never joined. Paper fires it
	 * after the quit event and the final save, so nothing of the session is still needed. It is not
	 * always fired on the main thread.
	 */
	@EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
	public void playerConnectionCloseEvent(PlayerConnectionCloseEvent event) {
		UUID uuid = event.getPlayerUniqueId();
		if (Bukkit.isPrimaryThread()) {
			connectionClosed(uuid);
		} else {
			Bukkit.getScheduler().runTask(mPlugin, () -> connectionClosed(uuid));
		}
	}

	@SuppressWarnings("ReferenceEquality") // Which connection's Player object is the question
	private void connectionClosed(UUID uuid) {
		PlayerSession session = active(uuid);
		if (session == null) {
			return;
		}
		/*
		 * Before the in-game check, which would also ignore this close: the refused connection's
		 * close must be taken now, or this session's own close would later be taken for it.
		 */
		if (session.takeRefusedClose()) {
			MMLog.debug(() -> "Taking connection close for uuid=" + uuid + " as a refused duplicate login's");
			return;
		}
		/* Paper takes a player out of the game before their connection's close, so this is another connection's */
		if (Bukkit.getPlayer(uuid) == session.getPlayer()) {
			MMLog.debug(() -> "Ignoring connection close for uuid=" + uuid + " whose session's player is still in the game");
			return;
		}

		MMLog.debug(() -> "Ending session " + session);
		/* First, so that nothing below failing can leave the next login waiting on this one forever */
		session.end();
		/* Only this session: a new login may already have replaced it */
		session.finished().whenComplete((ignored, ex) -> mSessions.remove(uuid, session));
		if (BukkitConfigAPI.getScoreboardCleanupEnabled()) {
			mAdapter.resetPlayerScores(session.getName(), Bukkit.getScoreboardManager().getMainScoreboard());
		}
	}

	/* ******************* Locks ******************* */

	static SessionLock lock(Player player) throws Exception {
		return lock(player, SessionLock.TIMEOUT_TICKS, null);
	}

	/**
	 * Saves and locks the player's session for a transfer or data handoff; see {@link SessionLock}.
	 *
	 * @throws Exception if the player is already locked, the save threw, or {@code player} is not
	 *                   the player's current connection: a lock taken through a stale Player object
	 *                   would lock the current one
	 */
	static SessionLock lock(Player player, int timeoutTicks, @Nullable ReturnParams returnParams) throws Exception {
		PlayerSession session = INSTANCE.current(player);
		if (session == null) {
			throw new Exception("Player " + player.getName() + " is not this player's current connection on this shard");
		}
		return SessionLock.take(INSTANCE, session, timeoutTicks, returnParams);
	}

	static boolean isLocked(Player player) {
		PlayerSession session = INSTANCE.current(player);
		return session != null && session.isLocked();
	}

	static boolean isLockedShoulderEntity(UUID entity) {
		return INSTANCE.mLockedShoulderEntities.containsKey(entity);
	}

	/* ******************* Waiting for saves ******************* */

	/** Completes once every save made so far for this player, by their current or just-ended session, has committed */
	CompletableFuture<Void> savesCommitted(Player player) {
		PlayerSession session = mSessions.get(player.getUniqueId());
		return session == null ? CompletableFuture.completedFuture(null) : session.savesCommitted();
	}

	/** Runs {@code callback} (on the main thread if {@code sync}, else async) once every save made so far for this player has committed */
	static void waitForSaves(Player player, Runnable callback, boolean sync) {
		if (!hasPendingSaves(player) && !BukkitConfigAPI.getSavingDisabled()) {
			MMLog.warning("Got request to wait for save commit but no pending save operations found. This might be a bug with the plugin that uses MonumentaRedisSync");
		}
		long startTime = System.currentTimeMillis();
		INSTANCE.savesCommitted(player).orTimeout(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS).whenComplete((ignored, ex) -> {
			if (ex != null) {
				MMLog.severe("Got timeout waiting to commit transactions for player '" + player.getName() + "'. This is very bad!", ex);
			}
			MMLog.debug(() -> "Committing save took " + (System.currentTimeMillis() - startTime) + " milliseconds");
			if (sync) {
				runOnMainThread(callback);
			} else if (INSTANCE.mPlugin.isEnabled()) {
				Bukkit.getScheduler().runTaskAsynchronously(INSTANCE.mPlugin, callback);
			}
		});
	}

	static boolean hasPendingSaves(Player player) {
		PlayerSession session = INSTANCE.mSessions.get(player.getUniqueId());
		return session != null && session.hasPendingSaves();
	}

	/* For tests: see PlayerSession.hasUnsettledSaves */
	static boolean hasUnsettledSaves(Player player) {
		PlayerSession session = INSTANCE.mSessions.get(player.getUniqueId());
		return session != null && session.hasUnsettledSaves();
	}

	/** Records a player data write made outside any session's saves, so shutdown waits for it. Returns it. */
	static <T> CompletableFuture<T> trackWrite(CompletableFuture<T> write) {
		INSTANCE.mOtherWrites.add(write);
		write.whenComplete((ignored, ex) -> INSTANCE.mOtherWrites.remove(write));
		return write;
	}

	/**
	 * Claims an offline write for a player until {@code write} completes, returning false (and
	 * claiming nothing) if they have a session here, a login under way, or another offline write
	 * in flight. A login that starts while the write is in flight waits for it before loading.
	 */
	static boolean claimOfflineWrite(UUID uuid, CompletableFuture<?> write) {
		synchronized (INSTANCE) {
			if (INSTANCE.mSessions.containsKey(uuid) || INSTANCE.expectedLogin(uuid) != null || Bukkit.getPlayer(uuid) != null
				|| INSTANCE.mOfflineWrites.putIfAbsent(uuid, write) != null) {
				return false;
			}
		}
		write.whenComplete((ignored, ex) -> INSTANCE.mOfflineWrites.remove(uuid, write));
		return true;
	}

	/* For tests */
	static void setExpectedLoginTimeoutMillis(long millis) {
		INSTANCE.mExpectedLoginTimeoutMillis = millis;
	}

	/*
	 * Called while the plugin is being disabled, before redis is closed: waits, up to the usual
	 * timeout, for every write of player data handed to redis to commit. The shard stops as soon as
	 * it is empty, so the last player's final save may well still be in flight.
	 */
	@SuppressWarnings("NullAway") // INSTANCE is null if enabling failed before it was created
	static void onDisable() {
		if (INSTANCE == null) {
			return;
		}
		CompletableFuture<?>[] writes = Stream.concat(
				INSTANCE.mSessions.values().stream().map(PlayerSession::savesCommitted),
				INSTANCE.mOtherWrites.stream())
			.toArray(CompletableFuture<?>[]::new);
		long startTime = System.currentTimeMillis();
		try {
			CompletableFuture.allOf(writes).get(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS);
			MMLog.info(() -> "Waited " + (System.currentTimeMillis() - startTime) + "ms for player data writes to commit before closing redis");
		} catch (InterruptedException | ExecutionException | TimeoutException ex) {
			MMLog.severe("Player data writes still in flight when closing redis may be lost", ex);
		}
	}

	/* ******************* Cached data, for the API ******************* */

	/** The player's content data, or empty content data, not kept, if they have no session here */
	static ContentData getContentData(UUID uuid) {
		PlayerSession session = INSTANCE.active(uuid);
		return session == null ? new ContentData("") : session.getContentData();
	}

	static void setContentData(UUID uuid, ContentData contentData) {
		PlayerSession session = INSTANCE.active(uuid);
		if (session == null) {
			MMLog.warning("Ignoring content data change for uuid=" + uuid + " who has no session on this shard");
			return;
		}
		session.setContentData(contentData);
	}

	static @Nullable JsonObject getPluginData(UUID uuid) {
		PlayerSession session = INSTANCE.active(uuid);
		return session == null ? null : session.getPluginData();
	}

	static @Nullable Map<String, String> getShardData(UUID uuid) {
		PlayerSession session = INSTANCE.active(uuid);
		return session == null ? null : session.getShardData();
	}
}
