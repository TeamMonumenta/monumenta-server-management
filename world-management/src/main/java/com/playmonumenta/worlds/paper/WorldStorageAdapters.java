package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.adapters.WorldStorageAdapter;
import com.playmonumenta.worlds.common.MMLog;
import de.tr7zw.nbtapi.NBT;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.Nullable;

/*
 * Loads the WorldStorageAdapter matching the running server, following the same
 * CraftBukkit-package-version convention as MonumentaRedisSync's VersionAdapter.
 */
public final class WorldStorageAdapters {
	private static @Nullable WorldStorageAdapter mAdapter = null;

	private WorldStorageAdapters() {
	}

	/** Resolves the adapter for the running server version. Throws if this version is unsupported. */
	public static void load() {
		String packageName = Bukkit.getServer().getClass().getPackage().getName();
		String version = packageName.substring(packageName.lastIndexOf('.') + 1);
		try {
			Class<?> clazz = Class.forName("com.playmonumenta.worlds.adapters.WorldStorageAdapter_" + version);
			mAdapter = (WorldStorageAdapter) clazz.getConstructor().newInstance();
		} catch (ReflectiveOperationException | ClassCastException ex) {
			throw new IllegalStateException("No world storage adapter for server version " + version, ex);
		}
		// World copies run off the main thread; NBT-API's reflection setup is not safe to trigger there.
		NBT.preloadApi();
		MMLog.info("Loaded world storage adapter for " + version);
	}

	public static WorldStorageAdapter get() {
		WorldStorageAdapter adapter = mAdapter;
		if (adapter == null) {
			throw new IllegalStateException("World storage adapter has not been loaded");
		}
		return adapter;
	}
}
