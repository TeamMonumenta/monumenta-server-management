package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import com.playmonumenta.worlds.common.utils.FileUtils;
import java.io.File;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/*
 * Pregenerates world instances by copying template worlds on an async thread, so a player joining an
 * instance that does not yet exist can be served a ready-made copy with a cheap rename on the main thread.
 *
 * All scheduler state lives on the main thread. The only async work is a single world copy at a time:
 * the main thread picks the next instance to generate, hands the copy to an async task, records the result
 * back on the main thread, and repeats until every template's pool is full. Consuming an instance and
 * reloading config both simply poke scheduleNext().
 *
 * A spare is considered up to date if its level.dat was modified at or after the template's; since a
 * freshly copied spare's level.dat is written at copy time, restarting the server with an unchanged
 * template finds a full pool of fresh spares and generates nothing. Editing a template makes its existing
 * spares read as outdated, so they are regenerated (and served only as a fallback meanwhile).
 */
public class WorldGenerator {
	// State is only written on the main thread - no synchronization needed.
	private static class TemplateState {
		public final String mName;
		public final int mLimit;
		public final long mTemplateMtime;
		public final Pattern mRegex;
		public final Set<Integer> mFresh = new TreeSet<>();
		public final Set<Integer> mOutdated = new TreeSet<>();
		public int mConsecutiveFailures = 0;

		public TemplateState(String name, int limit, long templateMtime) {
			mName = name;
			mLimit = limit;
			mTemplateMtime = templateMtime;
			mRegex = Pattern.compile(String.format("%s%s(\\d+)", PREGEN_PREFIX, Pattern.quote(name)));
		}

		public boolean reachedMaxFailures() {
			return mConsecutiveFailures >= MAX_PREGEN_SEQUENTIAL_FAILURES;
		}
	}

	private static @Nullable WorldGenerator INSTANCE = null;
	private static final String PREGEN_PREFIX = "pregen_";
	private static final String GENERATING_SUFFIX = ".generating";
	private static final int MAX_PREGEN_SEQUENTIAL_FAILURES = 5;
	private final Map<String, TemplateState> mTemplates = new LinkedHashMap<>();
	private boolean mStopped = true;
	private boolean mCopyInFlight = false;

	private WorldGenerator() {
		INSTANCE = this;
	}

	public static WorldGenerator getInstance() {
		if (INSTANCE == null) {
			INSTANCE = new WorldGenerator();
		}
		return INSTANCE;
	}

	public void reloadConfig() {
		cancelGeneration(true);
		mTemplates.clear();

		Map<String, Integer> templatePregenLimits = WorldManagementPlugin.getPregeneratedInstanceLimits();
		if (templatePregenLimits.isEmpty()) {
			MMLog.info("No template pregeneration specified, shutting down world generator.");
			return;
		}

		for (Map.Entry<String, Integer> entry : templatePregenLimits.entrySet()) {
			String templateName = entry.getKey();
			int pregenLimit = entry.getValue();

			char finalChar = templateName.charAt(templateName.length() - 1);
			if (finalChar >= '0' && finalChar <= '9') {
				MMLog.severe("templates may not end with a number: " + templateName);
				continue;
			}

			File templateLevelDat = new File(templateName, "level.dat");
			if (!templateLevelDat.isFile()) {
				MMLog.severe("template is not a world: " + templateName);
				continue;
			}

			mTemplates.put(templateName, new TemplateState(templateName, pregenLimit, templateLevelDat.lastModified()));
		}
		if (mTemplates.isEmpty()) {
			MMLog.severe("No valid templates, shutting down world generator.");
			return;
		}

		scanExistingSpares();

		mStopped = false;
		scheduleNext();
	}

	// Classifies every existing pregen_* folder as a fresh or outdated spare, and clears failed copies.
	private void scanExistingSpares() {
		File root = new File(".");
		String[] childPaths = root.list();
		if (childPaths == null) {
			MMLog.severe("Failed to list pregenerated worlds");
			return;
		}

		for (String name : childPaths) {
			if (!name.startsWith(PREGEN_PREFIX)) {
				continue;
			}
			if (name.endsWith(GENERATING_SUFFIX)) {
				MMLog.info("Deleting failed generating world " + name);
				deleteWorldFolder(new File(root, name));
				continue;
			}

			for (TemplateState state : mTemplates.values()) {
				Matcher matcher = state.mRegex.matcher(name);
				if (!matcher.matches()) {
					continue;
				}
				int index = Integer.parseInt(matcher.group(1));
				File spareDir = new File(root, name);
				if (index >= state.mLimit) {
					MMLog.info("Deleting pregenerated world " + name + " beyond configured limit " + state.mLimit);
					deleteWorldFolder(spareDir);
				} else if (new File(spareDir, "level.dat").lastModified() >= state.mTemplateMtime) {
					MMLog.info("Detected up to date pregenerated world " + name);
					state.mFresh.add(index);
				} else {
					MMLog.info("Detected outdated pregenerated world " + name
						+ "; will use as fallback while generating a replacement");
					state.mOutdated.add(index);
				}
				break;
			}
		}
	}

	public float progress() {
		int completed = 0;
		int limit = 0;
		for (TemplateState state : mTemplates.values()) {
			if (state.mLimit <= 0) {
				continue;
			}
			completed += Math.min(state.mFresh.size(), state.mLimit);
			limit += state.mLimit;
		}
		if (limit == 0) {
			return 1.0f;
		}
		return (float) completed / (float) limit;
	}

	public int pregeneratedInstances(String templateName) {
		TemplateState state = mTemplates.get(templateName);
		return state == null ? 0 : state.mFresh.size();
	}

	public static boolean worldExists(String name) {
		File target = new File(name);
		return target.isDirectory() && new File(target, "level.dat").isFile();
	}

	/**
	 * Renames a ready spare into place as worldName. Prefers a fresh spare; falls back to outdated.
	 * Throws immediately if none is available - callers are on the main thread and must not block.
	 */
	public void getWorldInstance(String worldName, String templateName) throws Exception {
		MMLog.debug("Preparing world " + worldName);
		if (worldExists(worldName)) {
			MMLog.debug("World already exists: " + worldName);
			return;
		}

		TemplateState state = mTemplates.get(templateName);
		if (state == null) {
			throw new Exception("No such template world " + templateName);
		}

		boolean outdated = false;
		Integer index = pollAny(state.mFresh);
		if (index == null) {
			index = pollAny(state.mOutdated);
			outdated = true;
		}
		if (index == null) {
			// Kick generation so a spare is ready next time, but don't block waiting for it.
			scheduleNext();
			throw new Exception("No pregenerated worlds are currently available");
		}

		String pregenName = PREGEN_PREFIX + templateName + index;
		if (outdated) {
			MMLog.warning("Using outdated pregenerated world " + pregenName + " due to lack of updated instances");
		}

		MMLog.info("Moving " + pregenName + " to " + worldName);
		File oldPath = new File(pregenName);
		File target = new File(worldName);
		if (!oldPath.renameTo(target)) {
			if (worldExists(pregenName)) {
				MMLog.severe("Failed to rename " + pregenName + " to " + worldName + "; original directory still exists");
			} else if (worldExists(worldName)) {
				MMLog.severe("Failed to rename " + pregenName + " to " + worldName + "; destination directory exists but renameTo() reported failure");
			} else {
				MMLog.severe("Failed to rename " + pregenName + " to " + worldName + "; neither source or destination exist");
			}
			throw new Exception("Failed to rename " + pregenName + " to " + worldName);
		}

		scheduleNext();
	}

	/**
	 * Starts a background copy for the next missing spare, if any. At most one copy runs at a time;
	 * the result is recorded on the main thread, which then calls this again to continue filling pools.
	 */
	public void scheduleNext() {
		// All state mutations happen on the main thread; if called async, re-schedule there.
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), this::scheduleNext);
			return;
		}
		if (mStopped || mCopyInFlight) {
			return;
		}

		// Prioritize the template with the fewest ready (fresh) spares so pools fill evenly rather
		// than draining the configuration in order. Ties keep configuration order (first wins).
		TemplateState target = null;
		int index = -1;
		for (TemplateState state : mTemplates.values()) {
			if (state.reachedMaxFailures() || state.mFresh.size() >= state.mLimit) {
				continue;
			}
			int free = nextFreeIndex(state);
			if (free < 0) {
				continue;
			}
			if (target == null || state.mFresh.size() < target.mFresh.size()) {
				target = state;
				index = free;
			}
		}
		if (target == null) {
			// Every pool is full or failing; stay idle until consumed or reloaded.
			return;
		}

		mCopyInFlight = true;
		final TemplateState state = target;
		final int spareIndex = index;
		Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), () -> {
			Exception failure = null;
			try {
				copyOne(state, spareIndex);
			} catch (Exception ex) {
				failure = ex;
			}
			final Exception finalFailure = failure;
			Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(),
				() -> onCopyComplete(state, spareIndex, finalFailure));
		});
	}

	// Lowest spare index in [0, limit) that is not already a fresh spare. May host an outdated spare.
	// Returns -1 if all indices are already populated
	private int nextFreeIndex(TemplateState state) {
		for (int i = 0; i < state.mLimit; i++) {
			if (!state.mFresh.contains(i)) {
				return i;
			}
		}
		return -1;
	}

	// Copies the template into the spare slot. Called async; blocks for potentially a long time.
	private void copyOne(TemplateState state, int index) throws Exception {
		if (!worldExists(state.mName)) {
			throw new Exception("Template world does not exist: " + state.mName);
		}

		String pregenName = PREGEN_PREFIX + state.mName + index;
		MMLog.info("Starting pregeneration of " + pregenName
			+ " (" + (state.mFresh.size() + 1) + "/" + state.mLimit
			+ ", " + (int) (100 * progress()) + "% total)");

		String generatingName = pregenName + GENERATING_SUFFIX;
		WorldCopier.copyWorldRegenUuids(new File(state.mName).toPath(), new File(generatingName).toPath());

		// Replace any outdated spare sitting in this slot before moving the fresh copy into place.
		File pregenDir = new File(pregenName);
		if (pregenDir.exists()) {
			FileUtils.deleteRecursively(pregenDir.toPath());
		}
		if (!new File(generatingName).renameTo(pregenDir)) {
			throw new Exception("Failed to move pregenerating world " + generatingName + " to " + pregenName);
		}
	}

	// Records the outcome of a copy on the main thread and schedules the next one.
	private void onCopyComplete(TemplateState state, int index, @Nullable Exception failure) {
		mCopyInFlight = false;

		if (failure != null) {
			state.mConsecutiveFailures++;
			MMLog.severe("Error pregenerating " + state.mName + " (failure count=" + state.mConsecutiveFailures + ")", failure);
			if (state.reachedMaxFailures()) {
				MMLog.severe("Template " + state.mName + " pregeneration failures exceed threshold; not trying again until config is reloaded");
			}
		} else {
			state.mConsecutiveFailures = 0;
			state.mOutdated.remove(index);
			state.mFresh.add(index);
			MMLog.info("Finished pregenerating " + PREGEN_PREFIX + state.mName + index
				+ " (" + state.mFresh.size() + "/" + state.mLimit
				+ ", " + (int) (100 * progress()) + "% total)");
		}

		if (!mStopped) {
			scheduleNext();
		}
	}

	/**
	 * Stops (or resumes) scheduling new copies. Any in-flight copy finishes independently;
	 * there is no long-lived task to cancel.
	 */
	public void cancelGeneration(boolean stopGenerating) {
		mStopped = stopGenerating;
	}

	private static @Nullable Integer pollAny(Set<Integer> set) {
		var it = set.iterator();
		if (!it.hasNext()) {
			return null;
		}
		Integer value = it.next();
		it.remove();
		return value;
	}

	private void deleteWorldFolder(File dir) {
		try {
			FileUtils.deleteRecursively(dir.toPath());
		} catch (IOException ex) {
			MMLog.severe("Failed to delete " + dir.getName(), ex);
		}
	}
}
