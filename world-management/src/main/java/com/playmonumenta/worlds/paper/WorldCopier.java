package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import com.playmonumenta.worlds.common.utils.FileUtils;
import com.playmonumenta.worlds.paper.RegionFileRewriter.RegionKind;
import de.tr7zw.nbtapi.NBT;
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
 * Only a whitelist of top-level entries is copied (see COPIED_DIRS plus level.dat); everything else
 * (server-managed scratch state, per-world runtime data, datapacks, ...) is dropped.
 * Region/entity chunks are streamed one at a time (RegionFileRewriter);
 * monumenta/ is copied as raw bytes. Assumes the modern (1.17+) single-dimension layout;
 * nested/multi-dimension templates fail loudly.
 */
public final class WorldCopier {
	// Top-level directories copied into the destination; any other top-level entry (and any file
	// other than level.dat) is dropped. Mirrors the Python reference tool's whitelist.
	private static final Set<String> COPIED_DIRS = Set.of("region", "entities", "monumenta");

	private WorldCopier() {
	}

	/**
	 * Copies source to dest, regenerating all entity UUIDs and setting level.dat LevelName
	 * to the destination folder name.
	 */
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
						} else if (COPIED_DIRS.contains(name)) {
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

	// Walks the source tree (directories only, no file reads) and rejects any entities/region folder
	// below the top level. The single supported layout has exactly one top-level entities/ and region/.
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

	private static void copyLevelDat(Path src, Path dst, String destWorldName) throws IOException {
		ReadWriteNBT nbt = NBT.readFile(src.toFile());
		nbt.getOrCreateCompound("Data").setString("LevelName", destWorldName);
		NBT.writeFile(dst.toFile(), nbt);
	}

	/**
	 * Recursively copies a subtree as raw bytes.
	 */
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
