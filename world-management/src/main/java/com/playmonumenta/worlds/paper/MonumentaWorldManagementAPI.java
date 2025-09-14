package com.playmonumenta.worlds.paper;

import com.playmonumenta.redissync.MonumentaRedisSyncAPI;
import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.World.Environment;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.entity.Player;

public class MonumentaWorldManagementAPI {

	private static String[] AVAILABLE_WORLDS_CACHE = new String[0];

	/**
	 * Checks whether the named world exists and could be loaded, using the cache. Fast and suitable for main thread.
	 */
	public static boolean isCachedWorldAvailable(String worldName) {
		for (String name : AVAILABLE_WORLDS_CACHE) {
			if (name.equals(worldName)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Checks whether the named world exists and could be loaded.
	 * <p>
	 * Note that this uses file I/O and so will be slow - recommend calling this only from an async thread
	 */
	public static boolean isWorldAvailable(String worldName) {
		File test = new File(worldName);
		// File is a directory - and contains level.dat
		return test.isDirectory() && new File(test, "level.dat").isFile();
	}

	/**
	 * Gets a list of all the named world folders from the cache. Fast and suitable for main thread.
	 */
	public static String[] getCachedAvailableWorlds() {
		return AVAILABLE_WORLDS_CACHE;
	}

	/**
	 * Gets a list of all the named world folders.
	 * <p>
	 * Note that this uses file I/O and so will be slow - recommend calling this only from an async thread
	 * <p>
	 * Updates the available worlds cache, but may take a tick or two before the cache is updated
	 */
	public static String[] getAvailableWorlds() {
		File root = new File(".");
		String[] directories = root.list((current, name) -> {
			File test = new File(current, name);
			// File is a directory - and contains level.dat
			return test.isDirectory() && new File(test, "level.dat").isFile();
		});
		Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> AVAILABLE_WORLDS_CACHE = directories);
		return directories;
	}

	/**
	 * Refreshes the available worlds cache async. Can be called async or sync, does its work async
	 * Note that the cache may not be updated for several ticks!
	 */
	public static void refreshCachedAvailableWorlds() {
		Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), MonumentaWorldManagementAPI::getAvailableWorlds);
	}

	/**
	 * Sorts a player to their appropriate instance world based on their score.
	 * <p>
	 * Many trigger several other things:
	 * - Player data will be saved
	 * - Join command (if configured) runs on player the next tick if this results in changing the player's current world
	 * - Rejoin command (if configured) runs on player the next tick if they are already on this world
	 * - Additional instances will start pregenerating (if configured)
	 * <p>
	 * Must be called from the main thread
	 */
	public static void sortWorld(Player player) throws Exception {
		WorldManagementListener listener = WorldManagementListener.getInstance();
		if (listener == null) {
			throw new Exception("WorldManagementListener is null");
		}

		// Important - need to save the player's location data on the existing world
		player.saveData();

		// Figure out what world the player would sort to
		World newWorld = listener.getSortWorld(player);

		// Move the player to that world at their last position (or world spawn)
		MonumentaRedisSyncAPI.getPlayerWorldData(player, newWorld).applyToPlayer(player);
	}

	/**
	 * Gets the specified world, loading and optionally creating it if needed.
	 * <p>
	 * Will always return a non-null world, or throw an exception if the request is not possible
	 * <p>
	 * If world is already loaded will return it (fast)
	 * If world already exists but is not loaded, will load that world (slow, maybe a few ticks on good hardware)
	 * If world does not exist and templateName is not null, will rename a pregenerated world to that name and load it
	 * <p>
	 * Must be called from the main thread
	 */
	public static World ensureWorldLoaded(String worldName, @Nullable String templateName) throws Exception {
		WorldManagementPlugin plugin = WorldManagementPlugin.getInstance();
		if (plugin == null) {
			throw new Exception("MonumentaWorldManagement plugin is not loaded");
		}
		Logger logger = plugin.getLogger();
		logger.fine("ensureWorldLoaded enter: worldName=" + worldName + " templateName=" + templateName + " thread=" + Thread.currentThread().getName());

		/* Try to get existing world first */
		World newWorld = Bukkit.getWorld(worldName);
		if (newWorld != null) {
			logger.fine("ensureWorldLoaded found existing unlocked: worldName=" + worldName + " templateName=" + templateName + " thread=" + Thread.currentThread().getName());
			return newWorld;
		}

		logger.fine("ensureWorldLoaded world not loaded: worldName=" + worldName + " templateName=" + templateName + " thread=" + Thread.currentThread().getName());

		//TODO Check redis to make sure world isn't loaded or created elsewhere

		/* Copy world if it doesn't exist */
		File worldFolder = new File(worldName);
		if (worldFolder.isDirectory()) {
			logger.fine("ensureWorldLoaded folder exists: worldName=" + worldName + " templateName=" + templateName + " thread=" + Thread.currentThread().getName());
		} else {
			/* Not allowed to create so return null */
			if (templateName == null) {
				throw new Exception("World '" + worldName + "' does not exist and templateName is null");
			}

			/* Create the world using a pregenerated instance - if none are available, throw an exception */
			plugin.getWorldGenerator().getWorldInstance(worldName, templateName);

			AVAILABLE_WORLDS_CACHE = Arrays.copyOf(AVAILABLE_WORLDS_CACHE, AVAILABLE_WORLDS_CACHE.length + 1);
			AVAILABLE_WORLDS_CACHE[AVAILABLE_WORLDS_CACHE.length - 1] = worldName;

			logger.fine("ensureWorldLoaded created new: worldName=" + worldName + " templateName=" + templateName + " thread=" + Thread.currentThread().getName());
		}

		logger.fine("ensureWorldLoaded sync loadworld: worldName=" + worldName + " templateName=" + templateName + " thread=" + Thread.currentThread().getName());
		newWorld = new WorldCreator(worldName).type(WorldType.NORMAL).generateStructures(false).environment(Environment.NORMAL).createWorld();

		logger.fine("ensureWorldLoaded loaded world: worldName=" + worldName + " templateName=" + templateName + " thread=" + Thread.currentThread().getName());

		if (newWorld == null) {
			throw new Exception("Failed to create world '" + worldName + "' - world is somehow null after creating which should never happen");
		}
		return newWorld;
	}

	public static CompletableFuture<Void> unloadWorld(String worldName) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		World world = Bukkit.getWorld(worldName);
		if (world == null) {
			future.completeExceptionally(new Exception("World '" + worldName + "' is not loaded"));
			return future;
		}

		if (Bukkit.getWorlds().get(0).equals(world)) {
			future.completeExceptionally(new Exception("Can't unload main world '" + worldName + "'"));
			return future;
		}

		//TODO Mark world as unloaded in redis

		if (!world.getPlayers().isEmpty()) {
			future.completeExceptionally(new Exception("Can't unload world '" + worldName + "' because there are still players in it"));
			return future;
		}

		world.setKeepSpawnInMemory(false);
		for (Chunk chunk : world.getLoadedChunks()) {
			world.unloadChunkRequest(chunk.getX(), chunk.getZ());
		}

		if (Bukkit.unloadWorld(world, true)) {
			// Success!
			future.complete(null);
		} else {
			// Nope
			future.completeExceptionally(new Exception("Unloading world '" + worldName + "' failed, unknown reason"));
		}

		return future;
	}

	/**
	 * Copies a world
	 * <p>
	 * Does most of its work on an async thread, and completes the future on the main thread when done.
	 * <p>
	 * Checks that the source world exists and is not loaded
	 * <p>
	 * Suggest using .whenComplete((unused, ex) -> your code) to do something on the main thread when done
	 */
	public static CompletableFuture<Void> copyWorld(String fromWorldName, String newWorldName) {
		CompletableFuture<Void> future = new CompletableFuture<>();

		if (MonumentaWorldManagementAPI.isWorldAvailable(newWorldName)) {
			future.completeExceptionally(new Exception("World '" + newWorldName + "' already exists, this command is for creating new worlds"));
			return future;
		}

		if (!MonumentaWorldManagementAPI.isWorldAvailable(fromWorldName)) {
			future.completeExceptionally(new Exception("Copy-from world '" + fromWorldName + "' does not exist"));
			return future;
		}

		if (Bukkit.getWorld(fromWorldName) != null) {
			future.completeExceptionally(new Exception("Copy-from world '" + fromWorldName + "' is already loaded, must unload it first"));
			return future;
		}

		Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), () -> {
			try {
				// Copy and wait for completion
				Process process = Runtime.getRuntime().exec(WorldManagementPlugin.getCopyWorldCommand() + " " + fromWorldName + " " + newWorldName);
				int exitVal = process.waitFor();

				if (exitVal != 0) {
					throw new Exception("Failed to copy world '" + fromWorldName + "' to '" + newWorldName + "': " + exitVal);
				}

				getAvailableWorlds(); // Update the cache

				Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> future.complete(null));
			} catch (Exception ex) {
				future.completeExceptionally(ex);
				ex.printStackTrace();
			}
		});

		return future;
	}

	/**
	 * Deletes a world.
	 * <p>
	 * Does most of its work on an async thread, and completes the future on the main thread when done.
	 * <p>
	 * Checks that the world is actually a world (has a level.dat file), and will also
	 * refuse to delete the folder if it contains more than one level of subfolder.
	 * <p>
	 * Suggest using .whenComplete((unused, ex) -> your code) to do something on the main thread when done
	 */
	public static CompletableFuture<Void> deleteWorld(String worldName) {
		CompletableFuture<Void> future = new CompletableFuture<>();
		if (!isWorldAvailable(worldName)) {
			future.completeExceptionally(new Exception("Can't delete world '" + worldName + "' which either doesn't exist or is not a world"));
			return future;
		}

		World world = Bukkit.getWorld(worldName);
		if (world != null) {
			future.completeExceptionally(new Exception("Can't delete world '" + worldName + "' which is loaded"));
			return future;
		}

		Bukkit.getScheduler().runTaskAsynchronously(WorldManagementPlugin.getInstance(), () -> {
			try {
				// Make sure the folder's depth is appropriate for a world, and cancel if not so it doesn't delete something else
				final int maxDepth = 2;
				final Path rootPath = Paths.get(worldName);
				final int rootPathDepth = rootPath.getNameCount();
				try (Stream<Path> stream = Files.walk(rootPath)) {
					boolean tooManyLevels = stream.anyMatch((e) -> e.getNameCount() - rootPathDepth > maxDepth);
					if (tooManyLevels) {
						throw new Exception("Can't delete world '" + worldName + "' which has folder depth > " + maxDepth);
					}
				}

				// Delete all files recursively but do **not** follow symbolic links
				Files.walkFileTree(rootPath, new SimpleFileVisitor<Path>() {
				   @Override
				   public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
					   Files.delete(file);
					   return FileVisitResult.CONTINUE;
				   }

				   @Override
				   public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
					   Files.delete(dir);
					   return FileVisitResult.CONTINUE;
				   }
				});

				getAvailableWorlds(); // Update the cache

				Bukkit.getScheduler().runTask(WorldManagementPlugin.getInstance(), () -> future.complete(null));
			} catch (Exception ex) {
				future.completeExceptionally(ex);
				ex.printStackTrace();
			}
		});

		return future;
	}
}
