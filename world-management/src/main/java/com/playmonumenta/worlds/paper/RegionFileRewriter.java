package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.adapters.RegionAccess;
import com.playmonumenta.worlds.common.MMLog;
import de.tr7zw.nbtapi.NBTType;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Copies a region/ or entities/ folder, regenerating nested entity UUIDs.
 *
 * The folder is copied byte for byte first, then chunks are edited in place in the copy. The
 * template is therefore never opened for writing, and a chunk whose UUIDs did not change keeps its
 * original bytes, compression type and external-.mcc placement rather than being re-encoded. Only
 * one chunk is decompressed at a time.
 */
public final class RegionFileRewriter {
	private static final int REGION_WIDTH = 32;
	private static final int CHUNK_COUNT = REGION_WIDTH * REGION_WIDTH;
	// Captures the region coordinates, which give the chunk coordinates of the chunks it holds.
	private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

	// Which kind of NBT a region file holds, and therefore where its entity UUIDs live. The two
	// folders use identical file names and container formats, so only the source folder says which.
	public enum RegionKind {
		// entities/*.mca: a flat Entities list. Since 1.17 mobs live here, not in region/.
		ENTITIES,
		// region/*.mca: terrain plus block_entities.
		REGION
	}

	private RegionFileRewriter() {
	}

	// Copies every file in srcDir to dstDir, then regenerates entity UUIDs in the copied region files.
	public static void rewriteDir(Path srcDir, Path dstDir, RegionKind kind) throws IOException {
		Files.createDirectories(dstDir);
		// Everything is copied, not just *.mca: a chunk too big to store inline lives in a sibling
		// c.<x>.<z>.mcc file that is part of its region file's contents.
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(srcDir)) {
			for (Path entry : entries) {
				if (Files.isDirectory(entry)) {
					throw new IOException("Unexpected subdirectory in " + kind + " folder: " + entry);
				}
				if (entry.getFileName().endsWith(".mcc")) {
					// Skip oversized chunk files; those will be recreated as required
					continue;
				}
				Path target = dstDir.resolve(entry.getFileName().toString());
				Files.copy(entry, target);
				if (REGION_FILE.matcher(entry.getFileName().toString()).matches()) {
					copyAndRegenRegionFile(entry, target, kind);
				}
			}
		}
	}

	private static void copyAndRegenRegionFile(Path fromFile, Path toFile, RegionKind kind) throws IOException {
		Matcher matcher = REGION_FILE.matcher(fromFile.getFileName().toString());
		if (!matcher.matches()) {
			throw new IOException("Region file name does not encode coordinates: " + toFile);
		}
		int regionX = Integer.parseInt(matcher.group(1));
		int regionZ = Integer.parseInt(matcher.group(2));

		int rewritten = 0;
		int untouched = 0;
		try (
			RegionAccess fromRegion = WorldStorageAdapters.get().openRegion(fromFile);
			RegionAccess toRegion = WorldStorageAdapters.get().openRegion(toFile)
		) {
			for (int i = 0; i < CHUNK_COUNT; i++) {
				int chunkX = regionX * REGION_WIDTH + (i % REGION_WIDTH);
				int chunkZ = regionZ * REGION_WIDTH + (i / REGION_WIDTH);
				ReadWriteNBT fromChunk = fromRegion.readChunk(chunkX, chunkZ);
				if (fromChunk == null) {
					continue;
				}
				if (regenChunk(fromChunk, kind, toFile, chunkX, chunkZ)) {
					toRegion.writeChunk(chunkX, chunkZ, fromChunk);
					rewritten++;
				} else {
					untouched++;
				}
			}
		}
		MMLog.trace("WorldCopier: region " + toFile.getFileName() + " rewritten=" + rewritten
			+ " untouched=" + untouched);
	}

	// Regenerates UUIDs in a parsed chunk according to its region kind; returns whether anything changed.
	private static boolean regenChunk(ReadWriteNBT chunk, RegionKind kind, Path file, int chunkX, int chunkZ)
		throws IOException {
		// Pre-1.18 chunks nest everything under "Level", which none of the paths below reach into, so
		// the chunk would be copied with its UUIDs intact and collide with the template. Minecraft
		// only upgrades a chunk when it loads it, so an untouched template can still be in this form.
		if (chunk.hasTag("Level")) {
			throw new IOException("Chunk (" + chunkX + "," + chunkZ + ") in " + file
				+ " uses the pre-1.18 'Level' layout; load the template on a modern server to"
				+ " upgrade it before copying");
		}
		String listKey = kind == RegionKind.ENTITIES ? "Entities" : "block_entities";
		// getCompoundList creates or replaces an absent or wrong-typed tag, editing the chunk we are
		// only inspecting.
		if (!chunk.hasTag(listKey) || chunk.getType(listKey) != NBTType.NBTTagList) {
			return false;
		}
		boolean modified = false;
		for (ReadWriteNBT entry : chunk.getCompoundList(listKey)) {
			modified |= kind == RegionKind.ENTITIES
				? EntityUuidRegenerator.regenEntity(entry)
				: EntityUuidRegenerator.regenBlockEntity(entry);
		}
		return modified;
	}
}
