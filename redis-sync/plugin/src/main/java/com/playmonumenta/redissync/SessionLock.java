package com.playmonumenta.redissync;

import com.playmonumenta.common.event.PlayerTransferFailEvent;
import com.playmonumenta.redissync.adapters.VersionAdapter.ReturnParams;
import com.playmonumenta.redissync.utils.MMLog;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.stream.Stream;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

/**
 * Locks a player's session while their data moves elsewhere: to another shard (a transfer), or
 * over their own saved data (a data handoff: stash get, rollback, loadFrom). While locked the
 * session is not saved, because wherever the data is going owns it now, and the player is frozen
 * (see {@link LockedPlayerListener}). The public API, and players, call a locked player
 * "transferring" either way.
 *
 * <p>Success ends with the player leaving: the connection closes, ending the session and the lock
 * with it. Before a handoff writes, anything else is a failure (the lock times out, or the work
 * done under it fails), and releasing the lock lets the player carry on here. Once a handoff
 * writes, the player's state is stale whatever happens next, so the lock is never released and
 * the player is kicked until they go.
 */
final class SessionLock {
	static final int TIMEOUT_TICKS = 10 * 20;

	enum Phase {
		/** Waiting for the saves the work must see to commit */
		SAVING,
		/** The work is running; the lock does not time out from under it */
		WORKING,
		/** The work is done, and the player should be leaving */
		MOVING,
		/** A handoff has replaced the player's saved data: never released, nothing this connection holds may be saved */
		HANDED_OFF,
	}

	private final PlayerSessions mSessions;
	private final PlayerSession mSession;
	private final boolean mSavePushed;
	private final List<UUID> mShoulderEntities;
	private final int mTimeoutTicks;
	/* Moved on from WORKING by redis callbacks, so only ever changed through advance() */
	private volatile Phase mPhase = Phase.SAVING;
	private @Nullable BukkitTask mTimeout = null;

	private SessionLock(PlayerSessions sessions, PlayerSession session, boolean savePushed, List<UUID> shoulderEntities, int timeoutTicks) {
		mSessions = sessions;
		mSession = session;
		mSavePushed = savePushed;
		mShoulderEntities = shoulderEntities;
		mTimeoutTicks = timeoutTicks;
	}

	/**
	 * Saves the session, applying {@code returnParams} (if any) to that save, since it is the one a
	 * transfer's target shard loads. Then locks it. A session still loading can be locked, so
	 * a plugin can transfer a player from its join handler; that save is skipped, and the player's
	 * data stays as it was loaded. Main thread only, as are taking the lock on and off.
	 *
	 * @throws Exception if the session is already locked, or the save threw
	 */
	@SuppressWarnings("deprecation") // No replacement API exists for getShoulderEntityLeft/Right
	static SessionLock take(PlayerSessions sessions, PlayerSession session, int timeoutTicks, @Nullable ReturnParams returnParams) throws Exception {
		Player player = session.getPlayer();
		if (session.isLocked()) {
			throw new Exception("Player " + player.getName() + " is already transferring");
		}

		long savesBefore = session.getPlayerdataSaves();
		session.setReturnParams(returnParams);
		try {
			MonumentaRedisSyncAPI.savePlayer(player);
		} finally {
			session.setReturnParams(null);
		}

		/* The player's shoulder entities (parrots) go with them, so must not be spawned here meanwhile */
		List<UUID> shoulderEntities = new ArrayList<>();
		for (Entity shoulder : new Entity[] {player.getShoulderEntityLeft(), player.getShoulderEntityRight()}) {
			if (shoulder != null) {
				shoulderEntities.add(shoulder.getUniqueId());
			}
		}

		SessionLock lock = new SessionLock(sessions, session, session.getPlayerdataSaves() > savesBefore, shoulderEntities, timeoutTicks);
		session.setLock(lock);
		shoulderEntities.forEach(shoulder -> sessions.getLockedShoulderEntities().put(shoulder, lock));
		lock.scheduleTimeout();
		return lock;
	}

	Player getPlayer() {
		return mSession.getPlayer();
	}

	/** Whether the save made as the lock was taken pushed a new entry onto the player's history */
	boolean savePushedEntry() {
		return mSavePushed;
	}

	boolean isHeld() {
		return mSession.getLock() == this;
	}

	boolean isHandedOff() {
		return mPhase == Phase.HANDED_OFF;
	}

	/** Moves to {@code to} if still in {@code from}. Any thread. */
	private synchronized void advance(Phase from, Phase to) {
		if (mPhase == from) {
			mPhase = to;
		}
	}

	/* ******************* Ending ******************* */

	private void scheduleTimeout() {
		mTimeout = Bukkit.getScheduler().runTaskLater(mSessions.getPlugin(), () -> {
			if (!isHeld()) {
				return;
			}
			switch (mPhase) {
				case WORKING -> {
					MMLog.warning("Lock for player=" + mSession.getName() + " timed out while its work is still running; keeping it");
					scheduleTimeout();
				}
				case HANDED_OFF -> {
					kickHandedOff();
					scheduleTimeout();
				}
				default -> {
					getPlayer().sendMessage(Component.text("Transferring timed out and your player has been unlocked", NamedTextColor.RED));
					release();
				}
			}
		}, mTimeoutTicks);
	}

	/** The player is still here although their data has been replaced: another plugin cancelled the kick. Main thread only. */
	void kickHandedOff() {
		MMLog.warning("Player=" + mSession.getName() + " is still here after their data was replaced; kicking them again");
		getPlayer().kick(Component.text("Your player data has been replaced, please rejoin"));
	}

	/* Takes the lock off its session, as the session ends or the lock is released */
	void detach() {
		if (!isHeld()) {
			return;
		}
		mSession.setLock(null);
		if (mTimeout != null) {
			mTimeout.cancel();
		}
		mShoulderEntities.forEach(shoulder -> mSessions.getLockedShoulderEntities().remove(shoulder, this));
	}

	/** Unlocks the player, if this lock is still held and not handed off, and tells other plugins the transfer failed. Main thread only. */
	@SuppressWarnings("deprecation")
	void release() {
		if (!isHeld() || isHandedOff()) {
			return;
		}
		detach();
		Player player = getPlayer();
		Bukkit.getPluginManager().callEvent(new com.playmonumenta.redissync.event.PlayerTransferFailEvent(player));
		Bukkit.getPluginManager().callEvent(new PlayerTransferFailEvent(player));
	}

	void releaseLater() {
		PlayerSessions.runOnMainThread(this::release);
	}

	/* ******************* The work ******************* */

	/**
	 * Runs {@code action} on the main thread once every save made so far for this player and each
	 * of {@code alsoWaitFor} has been acknowledged by redis, but only if this lock is still held,
	 * since an unlocked player may have saved again since. If the saves do not commit in time, the
	 * lock is released instead.
	 *
	 * <p>The action returns the redis work it started, which is tracked like one of the player's
	 * saves: if they leave before it is done, their next login here waits for it.
	 */
	void afterSaves(Collection<Player> alsoWaitFor, Supplier<? extends CompletableFuture<?>> action) {
		CompletableFuture<?>[] saves = Stream.concat(Stream.of(getPlayer()), alsoWaitFor.stream())
			.map(mSessions::savesCommitted)
			.toArray(CompletableFuture<?>[]::new);
		CompletableFuture.allOf(saves).orTimeout(MonumentaRedisSyncAPI.TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.whenComplete((ignored, ex) -> PlayerSessions.runOnMainThread(() -> {
				if (!isHeld()) {
					MMLog.warning("Abandoning work for player=" + mSession.getName() + ": they are no longer locked for it (timed out or left)");
					return;
				}
				if (ex != null) {
					MMLog.severe("Saves for player=" + mSession.getName() + " did not commit in time; abandoning the work they were locked for", ex);
					getPlayer().sendMessage(Component.text("Your data could not be saved in time, please try again", NamedTextColor.RED));
					release();
					return;
				}
				advance(Phase.SAVING, Phase.WORKING);
				CompletableFuture<?> work;
				try {
					work = action.get();
				} catch (RuntimeException actionEx) {
					MMLog.severe("Work for locked player=" + mSession.getName() + " failed to start", actionEx);
					release();
					return;
				}
				work.whenComplete((ignored2, ex2) -> advance(Phase.WORKING, Phase.MOVING));
				mSession.trackSave(work, () -> "Work for locked player=" + mSession.getName() + " failed");
			}));
	}

	/**
	 * Marks the player's saved data as replaced, from any thread while the work runs. From here
	 * their in-memory state is stale whatever happens next (another plugin may cancel the kick
	 * that should follow), so nothing this connection holds is saved again, and they must leave.
	 */
	void handOff() {
		advance(Phase.WORKING, Phase.HANDED_OFF);
	}
}
