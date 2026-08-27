package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import com.playmonumenta.worlds.common.utils.FileUtils;
import com.playmonumenta.worlds.paper.RegionFileRewriter.RegionKind;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

/*
 * Copies a Minecraft world folder in-process while regenerating every entity UUID, so the copy can
 * be loaded alongside its template without UUID collisions. Neither world is loaded by Bukkit.
 *
 * Only level.dat, region/, entities/ and monumenta/ cross over; anything else is dropped with a
 * warning, including entries a future Minecraft version may add. The rest of a world folder is the
 * server's private record of *this* world (session.lock, uid.dat) or state it rebuilds on demand
 * (poi/, data/), neither of which a copy may inherit.
 *
 * Region and entity chunks are streamed one at a time (RegionFileRewriter); monumenta/ is copied as
 * raw bytes. Assumes the modern single-dimension layout; other layouts fail loudly.
 */
public final class WorldCopier {
	// Copied verbatim; region/ and entities/ are handled separately because their chunks are
	// rewritten. Any other top-level directory, and any file other than level.dat, is dropped.
	private static final Set<String> RAW_COPIED_DIRS = Set.of("monumenta");

	private WorldCopier() {
	}

	// Copies source to dest, regenerating all entity UUIDs and setting level.dat LevelName
	// to the destination folder name.
	public static void copyWorldRegenUuids(Path source, Path dest) throws IOException {
		MMLog.trace("WorldCopier: copyWorldRegenUuids source=" + source + " dest=" + dest);
		// Reject unsupported (multi-dimension/nested) layouts before copying anything - fail fast
		// rather than partway through copying gigabytes of region data.
		validateSingleDimension(source);

		// Start from a clean slate so retries after a partial failure don't trip over leftover files.
		if (Files.exists(dest)) {
			FileUtils.deleteRecursively(dest);
		}
		try {
			Files.createDirectories(dest);
			try (DirectoryStream<Path> entries = Files.newDirectoryStream(source)) {
				for (Path entry : entries) {
					String name = entry.getFileName().toString();
					Path target = dest.resolve(name);
					if (Files.isDirectory(entry)) {
						if (name.equals("entities")) {
							RegionFileRewriter.rewriteDir(entry, target, RegionKind.ENTITIES);
						} else if (name.equals("region")) {
							RegionFileRewriter.rewriteDir(entry, target, RegionKind.REGION);
						} else if (RAW_COPIED_DIRS.contains(name)) {
							copyTreeRaw(entry, target);
						} else {
							MMLog.warning("WorldCopier: skipping non-whitelisted directory " + entry);
						}
					} else if (name.equals("level.dat")) {
						copyLevelDat(entry, target, dest.getFileName().toString());
					} else {
						MMLog.warning("WorldCopier: skipping non-whitelisted file " + entry);
					}
				}
			}
			MMLog.trace("WorldCopier: copyWorldRegenUuids completed for dest=" + dest);
		} catch (IOException | RuntimeException | Error ex) {
			MMLog.trace("WorldCopier: copyWorldRegenUuids failed for dest=" + dest + ": " + ex);
			// Don't leave a half-written world behind; it would block the next attempt and is invalid anyway.
			try {
				FileUtils.deleteRecursively(dest);
			} catch (IOException cleanupEx) {
				ex.addSuppressed(cleanupEx);
			}
			throw ex;
		}
	}

	// Rejects any entities/ or region/ folder below the top level, which means a bundled dimension
	// the copier does not walk - its chunks would be copied with their UUIDs intact. Directories
	// only, no file reads, and it runs before anything is written.
	private static void validateSingleDimension(Path source) throws IOException {
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(source)) {
			for (Path entry : entries) {
				if (Files.isDirectory(entry)) {
					String name = entry.getFileName().toString();
					if (!name.equals("entities") && !name.equals("region")) {
						checkNoNestedMatches(entry, "entities", "region");
					}
				}
			}
		}
	}

	private static void checkNoNestedMatches(Path dir, String... names) throws IOException {
		Set<String> targets = Set.of(names);
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
			for (Path entry : entries) {
				if (Files.isDirectory(entry)) {
					if (targets.contains(entry.getFileName().toString())) {
						throw new IOException("Nested '" + entry.getFileName() + "' directory at " + entry
							+ "; multi-dimension/nested templates are not supported");
					}
					checkNoNestedMatches(entry, names);
				}
			}
		}
	}

	// Bukkit keys a loaded world by LevelName, so a copy keeping the template's name would collide
	// with the template and with every sibling instance.
	private static void copyLevelDat(Path src, Path dst, String destWorldName) throws IOException {
		ReadWriteNBT nbt = WorldStorageAdapters.get().readNbtFile(src);
		nbt.getOrCreateCompound("Data").setString("LevelName", destWorldName);
		WorldStorageAdapters.get().writeNbtFile(dst, nbt);
	}

	// Recursively copies a subtree as raw bytes. Fails if the destination already exists.
	private static void copyTreeRaw(Path srcDir, Path dstDir) throws IOException {
		Files.createDirectories(dstDir);
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(srcDir)) {
			for (Path entry : entries) {
				Path target = dstDir.resolve(entry.getFileName().toString());
				if (Files.isDirectory(entry)) {
					copyTreeRaw(entry, target);
				} else {
					Files.copy(entry, target);
				}
			}
		}
	}
}
