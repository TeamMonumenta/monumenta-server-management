package com.playmonumenta.redissync;

import com.playmonumenta.redissync.RedisSyncTestHarness.TestPlayer;
import com.playmonumenta.redissync.event.PlayerSaveEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

/**
 * Random sequences of everything that can happen to one player on one shard (saves, a stalled
 * redis, disconnects of every kind, immediate and overlapping reconnects, transfers, stash,
 * rollback, datapack reloads), checked against a model of the player's save history.
 *
 * <p>The other tests each pin one interaction; this one looks for the interactions nobody thought
 * to write a test for. The model is the list of progress values each save should have pushed.
 * Every login must load the newest, in all three places progress is stored, and at the end redis
 * must hold the model's newest entries, aligned across the history lists.
 *
 * <p>Whether a save lands depends on whether the player is locked for a transfer, which the model
 * takes from the plugin rather than predicting: lock timing is covered by {@link TransferTest} and
 * {@link DataHandoffTest}.
 *
 * <p>Seeds are fixed so a failure reproduces, though not exactly: redis timing is real. Run more
 * with {@code ./gradlew test -Pmrs.fuzz.seeds=200}, or one with {@code -Pmrs.fuzz.seed=N}. A
 * failure reports its seed and the operations that led to it.
 */
public class LifecycleFuzzTest {
	private static final int DEFAULT_SEEDS = 8;
	private static final int OPS_PER_SEED = 60;
	private static final String TARGET = "other_shard";

	@Test
	void randomLifecyclesAlwaysLoadTheLatestSave() throws Exception {
		String only = System.getProperty("mrs.fuzz.seed");
		if (only != null) {
			runSeed(Long.parseLong(only));
			return;
		}
		int seeds = Integer.getInteger("mrs.fuzz.seeds", DEFAULT_SEEDS);
		for (long seed = 1; seed <= seeds; seed++) {
			runSeed(seed);
		}
	}

	private static void runSeed(long seed) throws Exception {
		try (RedisSyncTestHarness harness = new RedisSyncTestHarness()) {
			Run run = new Run(harness, seed);
			try {
				run.execute();
			} catch (Throwable ex) {
				throw new AssertionError("Seed " + seed + " failed after:\n  " + String.join("\n  ", run.mLog), ex);
			}
		}
	}

	private static final class Run {
		private final RedisSyncTestHarness mHarness;
		private final Random mRandom;
		private final List<String> mLog = new ArrayList<>();
		private final TestPlayer mModerator;
		private @Nullable TestPlayer mCurrent = null;
		/* The current connection's progress, as the model has it */
		private int mLive = 0;
		/* The progress each save should have pushed, oldest first; the last is what must load */
		private final List<Integer> mHistory = new ArrayList<>();
		private @Nullable Integer mStash = null;
		/* Saves the plugin actually made, per connection */
		private final Map<Player, Integer> mSaves = Collections.synchronizedMap(new IdentityHashMap<>());
		private int mNextValue = 1;

		private Run(RedisSyncTestHarness harness, long seed) throws Exception {
			mHarness = harness;
			mRandom = new Random(seed);
			mModerator = harness.join("Moderator");
			/* Fired only for a save the plugin is actually making */
			harness.onEvent(PlayerSaveEvent.class, EventPriority.MONITOR, event -> mSaves.merge(event.getPlayer(), 1, Integer::sum));
		}

		private void execute() throws Exception {
			/* Something to load from the start, so every check is of a real save */
			log("first login");
			mCurrent = mHarness.join("Tester");
			setProgress(0);
			disconnect(mCurrent);

			for (int i = 0; i < OPS_PER_SEED; i++) {
				if (mCurrent == null) {
					offlineStep();
				} else {
					onlineStep(mCurrent);
				}
			}

			if (mCurrent != null) {
				log("final disconnect");
				disconnect(mCurrent);
			}
			log("final login");
			login();
			checkHistory();
		}

		private void offlineStep() throws Exception {
			int roll = mRandom.nextInt(100);
			if (roll < 60) {
				log("login");
				login();
			} else if (roll < 75) {
				log("login dropped during configuration");
				TestPlayer dropped = mHarness.newPlayer("Tester");
				check(mHarness.preLogin(dropped) == AsyncPlayerPreLoginEvent.Result.ALLOWED, "pre-login allowed");
				check(mHarness.verifyLogin(dropped), "login allowed");
				mHarness.tick();
				mHarness.closeUnplacedConnection(dropped);
			} else if (roll < 80) {
				log("two logins at once, the second dropping while the first configures");
				TestPlayer first = mHarness.newPlayer("Tester");
				TestPlayer second = mHarness.newPlayer("Tester");
				check(mHarness.preLogin(first) == AsyncPlayerPreLoginEvent.Result.ALLOWED, "first pre-login allowed");
				check(mHarness.preLogin(second) == AsyncPlayerPreLoginEvent.Result.ALLOWED, "second pre-login allowed");
				check(mHarness.verifyLogin(first), "first login allowed");
				mHarness.tick();
				mHarness.closeUnplacedConnection(second);
				mHarness.tick();
				mHarness.placePlayer(first);
				mHarness.tick(2);
				/* Its session cannot be told from the dropped one's, so it is refused */
				check(!first.isPlaced(), "first connection refused");
			} else if (roll < 88) {
				log("two logins at once");
				TestPlayer first = mHarness.newPlayer("Tester");
				TestPlayer second = mHarness.newPlayer("Tester");
				check(mHarness.preLogin(first) == AsyncPlayerPreLoginEvent.Result.ALLOWED, "first pre-login allowed");
				check(mHarness.preLogin(second) == AsyncPlayerPreLoginEvent.Result.ALLOWED, "second pre-login allowed");
				check(mHarness.completeLogin(first) == AsyncPlayerPreLoginEvent.Result.ALLOWED, "first login completes");
				check(mHarness.completeLogin(second) != AsyncPlayerPreLoginEvent.Result.ALLOWED, "second login refused");
				mHarness.tick();
				check(first.isPlaced(), "first connection still in the game");
				joined(first);
			} else {
				timeStep();
			}
		}

		private void onlineStep(TestPlayer current) throws Exception {
			check(current.isPlaced(), "the current connection is still in the game");
			int roll = mRandom.nextInt(100);
			if (roll < 20) {
				setProgress(mNextValue++);
			} else if (roll < 28) {
				log("save" + (transferring(current) ? " (transferring, skipped)" : ""));
				saving(current);
				MonumentaRedisSyncAPI.savePlayer(current);
			} else if (roll < 34) {
				log("disconnect");
				disconnect(current);
			} else if (roll < 37) {
				log("disconnect, close event off the main thread");
				saving(current);
				mHarness.disconnectWithCloseOffMainThread(current);
				mCurrent = null;
			} else if (roll < 41) {
				log("kick");
				saving(current);
				current.kick(Component.text("fuzz"));
				mCurrent = null;
			} else if (roll < 46) {
				log("disconnect and straight back");
				disconnect(current);
				login();
			} else if (roll < 51) {
				log("reconnect while the old connection is still in the game");
				/*
				 * The old connection is kicked inside the login, which ticks the server while it
				 * waits, so a transfer lock can time out first. A lock can only go away, not appear.
				 */
				boolean wasTransferring = transferring(current);
				int savesBefore = mSaves.getOrDefault(current, 0);
				TestPlayer next = mHarness.newPlayer("Tester");
				check(mHarness.login(next) == AsyncPlayerPreLoginEvent.Result.ALLOWED, "re-entrant login allowed");
				check(!current.isPlaced(), "old connection closed");
				int saved = mSaves.getOrDefault(current, 0) - savesBefore;
				check(wasTransferring ? saved <= 1 : saved == 1, "the old connection saved " + saved + " times");
				if (saved == 1) {
					mHistory.add(mLive);
				}
				mHarness.tick();
				joined(next);
			} else if (roll < 54) {
				if (!transferring(current)) {
					log("transfer (never completes; the lock times out)");
					saving(current);
					MonumentaRedisSyncAPI.sendPlayer(current, TARGET);
				}
			} else if (roll < 57) {
				/* While transferring, the reload would load back the transfer save's advancements: correct, but not this model */
				if (!transferring(current)) {
					log("datapack reload");
					saving(current);
					mHarness.reloadDatapacks();
				}
			} else if (roll < 60) {
				if (!transferring(current)) {
					log("stash put");
					saving(current);
					/* The stash is of the newest save once stash put's own save has committed; nothing else happens until then */
					long confirmations = stashConfirmations(current);
					MonumentaRedisSyncAPI.stashPut(current, null);
					mHarness.awaitWithTicks("stash written", () -> stashConfirmations(current) > confirmations);
					mStash = newest();
				}
			} else if (roll < 63) {
				Integer stash = mStash;
				if (stash != null && !transferring(current)) {
					log("stash get (" + stash + ")");
					saving(current);
					MonumentaRedisSyncAPI.stashGet(current, null);
					mHistory.add(stash);
					awaitKicked(current);
				}
			} else if (roll < 66) {
				int index = mRandom.nextInt(3);
				/* Rollback first saves, then counts back from the save before that one */
				if (!transferring(current) && mHistory.size() >= index + 2) {
					saving(current);
					int restored = mHistory.get(mHistory.size() - 2 - index);
					log("rollback index " + index + " (" + restored + ")");
					MonumentaRedisSyncAPI.playerRollback(mModerator, current, index);
					mHistory.add(restored);
					awaitKicked(current);
				}
			} else {
				timeStep();
			}
		}

		private void timeStep() throws Exception {
			if (mRandom.nextBoolean()) {
				int ticks = 1 + mRandom.nextInt(30);
				log("tick x" + ticks);
				mHarness.tick(ticks);
			} else {
				int millis = 50 + mRandom.nextInt(200);
				log("redis stalls writes for " + millis + "ms");
				mHarness.pauseRedisWrites(millis);
			}
		}

		private void setProgress(int value) {
			TestPlayer current = mCurrent;
			if (current == null) {
				throw new IllegalStateException("offline");
			}
			log("progress -> " + value);
			mLive = value;
			mHarness.setProgress(current, value);
		}

		private void disconnect(TestPlayer current) {
			saving(current);
			mHarness.disconnect(current);
			mCurrent = null;
		}

		private void login() throws Exception {
			TestPlayer player = mHarness.newPlayer("Tester");
			check(mHarness.login(player) == AsyncPlayerPreLoginEvent.Result.ALLOWED, "login allowed");
			mHarness.tick();
			joined(player);
		}

		private void awaitKicked(TestPlayer current) throws InterruptedException {
			mHarness.awaitWithTicks("kicked to apply the handoff", () -> !current.isPlaced());
			mCurrent = null;
		}

		/* A connection has just finished joining: it must have loaded the newest save */
		private void joined(TestPlayer player) {
			int loaded = mHarness.progress(player);
			check(loaded == newest(), "expected to load progress " + newest() + ", loaded " + loaded);
			mCurrent = player;
			mLive = loaded;
		}

		/* The connection is about to save: unless it is transferring, what it has now is what must load next */
		private void saving(TestPlayer current) {
			if (!transferring(current)) {
				mHistory.add(mLive);
			}
		}

		/* Redis holds the model's newest saves, newest first, aligned across the history lists */
		private void checkHistory() throws Exception {
			TestPlayer current = mCurrent;
			if (current == null) {
				throw new IllegalStateException("offline");
			}
			List<Integer> saved = mHarness.savedProgressHistory(current);
			List<Integer> expected = new ArrayList<>(mHistory);
			Collections.reverse(expected);
			check(saved.size() <= expected.size() && saved.equals(expected.subList(0, saved.size())),
				"saved history " + saved + " is the newest of the model's " + expected);
		}

		private int newest() {
			return mHistory.get(mHistory.size() - 1);
		}

		private static long stashConfirmations(TestPlayer player) {
			return player.getChat().stream().filter(line -> line.contains("saved to stash successfully")).count();
		}

		private static boolean transferring(TestPlayer player) {
			return MonumentaRedisSyncAPI.isPlayerTransferring(player);
		}

		private static void check(boolean condition, String what) {
			if (!condition) {
				throw new AssertionError(what);
			}
		}

		private void log(String what) {
			mLog.add(what);
		}
	}
}
