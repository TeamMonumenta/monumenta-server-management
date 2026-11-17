package com.playmonumenta.redissync;

import com.google.gson.JsonObject;
import com.playmonumenta.redissync.adapters.VersionAdapter.ReturnParams;
import com.playmonumenta.redissync.data.ContentData;
import com.playmonumenta.redissync.utils.MMLog;
import io.lettuce.core.TransactionResult;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;

/**
 * One connection's worth of a player on this shard, from its first load event until Paper fires
 * PlayerConnectionCloseEvent for it. {@link PlayerSessions} starts and ends sessions.
 *
 * <p>A session belongs to one {@link Player} object (Paper makes a new one per connection) and
 * is only used for events carrying that exact object, so a stale Player from an earlier
 * connection can never save over a newer one.
 *
 * <p>What the plugin keeps about a connection lives here, so it all starts and ends together: the
 * data cached from redis, the saves handed to redis that have not committed yet, and the
 * {@link Departure} the player is leaving for, if any.
 */
final class PlayerSession {
	/**
	 * <pre>
	 * LOADING --join over--> PLAYING
	 * LOADING, PLAYING --load failed, or superseded while loading--> FAILED
	 * any --connection closed--> ENDED
	 * </pre>
	 * PLAYING can fail when a datapack reload's advancement load fails. A connection refused as it
	 * starts loading gets a session that is FAILED from the start and never kept.
	 */
	enum State {
		/** From the first load event until the join event is over. Not saved: other plugins have not loaded their state yet */
		LOADING,
		/** Saved */
		PLAYING,
		/** The load failed or was refused. Never saved, since that would put partial data over the real thing; the player is refused at login, or kicked */
		FAILED,
		/** The connection has closed */
		ENDED,
	}

	private final Player mPlayer;
	private final String mName;

	/** Why the proxy has been asked to take the player off this shard, until they leave or the request times out */
	sealed interface Departure permits Transfer, Replacement {
	}

	/** To another shard; the return location is applied to every save meanwhile, so the logout save carries it */
	record Transfer(String target, @Nullable ReturnParams returnParams) implements Departure {
	}

	/** Back to this shard with their data replaced: {@code write} is made after their final save, and {@code onWritten} told how it went */
	record Replacement(String what, SessionLease.FencedWrite write, Consumer<@Nullable Throwable> onWritten) implements Departure {
		@Override
		public String toString() {
			return "Replacement[" + what + "]";
		}
	}

	private volatile State mState = State.LOADING;
	/* Main thread only */
	private @Nullable Departure mDeparture = null;
	/* This session's lease token; null if it holds none (saving disabled, or not kept) */
	private final @Nullable String mLeaseToken;

	/*
	 * Another connection for this account refused while loading, until PlayerLoginEvent refuses it
	 * too, and then the count of such connections whose close is still to come. NPC plugins'
	 * fake players also land here, but never reach PlayerLoginEvent, so never expect a close.
	 * Main thread only.
	 */
	private @Nullable Player mRefusedLogin = null;
	private int mRefusedClosesToCome = 0;

	/*
	 * Keeps the advancements history in step with the other lists, which a playerdata save pushes
	 * together. A full save makes the playerdata save and then the advancements save, but the server
	 * also saves advancements on their own, before a datapack reload. Main thread only.
	 */
	private boolean mAwaitingAdvancementsSave = false;
	private boolean mUnpairedAdvancementsAtHead = false;

	private JsonObject mPluginData = new JsonObject();
	private Map<String, String> mShardData = new HashMap<>();
	private ContentData mContentData = new ContentData("");

	private final Set<CompletableFuture<?>> mPendingSaves = ConcurrentHashMap.newKeySet();
	private final CompletableFuture<Void> mEnded = new CompletableFuture<>();

	PlayerSession(Player player) {
		this(player, null);
	}

	PlayerSession(Player player, @Nullable String leaseToken) {
		mPlayer = player;
		mName = player.getName();
		mLeaseToken = leaseToken;
	}

	@Nullable String getLeaseToken() {
		return mLeaseToken;
	}

	boolean belongsTo(Player player) {
		return mPlayer == player;
	}

	Player getPlayer() {
		return mPlayer;
	}

	String getName() {
		return mName;
	}

	/* ******************* State ******************* */

	State getState() {
		return mState;
	}

	boolean isEnded() {
		return mState == State.ENDED;
	}

	/** Loading or playing: its load events load (a datapack reload re-fires the advancement load), and it can still fail */
	boolean isLive() {
		return mState == State.LOADING || mState == State.PLAYING;
	}

	/** The join event is over, so saving can start, unless the session has failed meanwhile */
	void joined() {
		if (mState == State.LOADING) {
			setState(State.PLAYING);
		}
	}

	void fail() {
		if (isLive()) {
			setState(State.FAILED);
		}
	}

	private void setState(State state) {
		State previous = mState;
		mState = state;
		MMLog.debug(() -> "Session player=" + mName + " " + previous + " -> " + state);
	}

	/**
	 * The connection has closed. No save can be made after this, so {@link #finished()} completes
	 * once the saves already made (and any replacement written after them) have committed.
	 */
	void end() {
		setState(State.ENDED);
		mEnded.complete(null);
	}

	/** Whether the session's saves are written: it has loaded and joined */
	boolean canSave() {
		return mState == State.PLAYING;
	}

	/* ******************* Departure ******************* */

	@Nullable Departure getDeparture() {
		return mDeparture;
	}

	void setDeparture(@Nullable Departure departure) {
		mDeparture = departure;
	}

	boolean isDeparting() {
		return mDeparture != null;
	}

	@Nullable ReturnParams getReturnParams() {
		return mDeparture instanceof Transfer transfer ? transfer.returnParams() : null;
	}

	/* ******************* Other connections for this account ******************* */

	void refusedLoad(Player player) {
		mRefusedLogin = player;
	}

	/** Whether this is the other connection refused while loading; if so its close is now expected */
	boolean takeRefusedLogin(Player player) {
		if (mRefusedLogin != player) {
			return false;
		}
		mRefusedLogin = null;
		mRefusedClosesToCome++;
		return true;
	}

	/** Takes a connection close as a refused connection's, if one is to come: it must not end this session */
	boolean takeRefusedClose() {
		if (mRefusedClosesToCome == 0) {
			return false;
		}
		mRefusedClosesToCome--;
		return true;
	}

	/* ******************* Cached data ******************* */

	JsonObject getPluginData() {
		return mPluginData;
	}

	void setPluginData(JsonObject pluginData) {
		mPluginData = pluginData;
	}

	/** The player's locations on every shard and world, for the API to look up without reading redis */
	Map<String, String> getShardData() {
		return mShardData;
	}

	void setShardData(Map<String, String> shardData) {
		mShardData = shardData;
	}

	ContentData getContentData() {
		return mContentData;
	}

	void setContentData(ContentData contentData) {
		mContentData = contentData;
	}

	/* ******************* Saves ******************* */

	/** A playerdata save, which pushes onto every history list but advancements, is being made; its advancements save comes next */
	void beginPlayerdataSave() {
		mAwaitingAdvancementsSave = true;
	}

	/**
	 * An advancements save is being made. Returns whether it should replace the newest advancements
	 * entry rather than push a new one: it should if that entry was pushed by an advancements save
	 * that was not part of a full save, and so has no matching entry on the other lists.
	 */
	boolean beginAdvancementsSave() {
		boolean replaceHead = mUnpairedAdvancementsAtHead;
		mUnpairedAdvancementsAtHead = !mAwaitingAdvancementsSave;
		mAwaitingAdvancementsSave = false;
		return replaceHead;
	}

	/** Records a write of the player's data handed to redis, for anything that must see it committed to wait for */
	void trackSave(CompletableFuture<?> save, Supplier<String> failureMessage) {
		mPendingSaves.add(save);
		save.whenComplete((result, ex) -> {
			mPendingSaves.remove(save);
			Throwable failure = ex != null ? ex : result instanceof TransactionResult transaction ? RedisAPI.firstCommandError(transaction) : null;
			if (failure != null) {
				MMLog.severe(failureMessage.get(), failure);
			}
		});
	}

	/** Completes once every save made so far has been acknowledged by redis, successfully or not */
	CompletableFuture<Void> savesCommitted() {
		CompletableFuture<?>[] saves = mPendingSaves.toArray(new CompletableFuture<?>[0]);
		/* Failures are logged as they happen; waiting only needs to know the save is over */
		return CompletableFuture.allOf(saves).handle((ignored, ex) -> null);
	}

	boolean hasPendingSaves() {
		/* Not isEmpty(): a save leaves the set in its own completion callback, which can run after something waiting on it */
		return mPendingSaves.stream().anyMatch(save -> !save.isDone());
	}

	/** Completes once the session has ended and every save it made has been acknowledged by redis */
	CompletableFuture<Void> finished() {
		return mEnded.thenCompose(ignored -> savesCommitted());
	}

	@Override
	public String toString() {
		return "PlayerSession{name=" + mName + " uuid=" + mPlayer.getUniqueId() + " state=" + mState
			+ (mDeparture != null ? " departure=" + mDeparture : "") + "}";
	}
}
