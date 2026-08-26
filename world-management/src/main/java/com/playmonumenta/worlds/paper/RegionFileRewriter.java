package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.adapters.RegionAccess;
import com.playmonumenta.worlds.common.MMLog;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Copies a region/ or entities/ folder, regenerating nested entity UUIDs.
 *
 * The folder is first copied byte for byte, then chunks are edited in place in the copy. That means
 * the template is never opened for writing, and a chunk whose UUIDs did not change keeps its original
 * bytes and compression rather than being decoded and re-encoded. Only one chunk is held in memory at
 * a time.
 */
public final class RegionFileRewriter {
	private static final int REGION_WIDTH = 32;
	private static final int CHUNK_COUNT = REGION_WIDTH * REGION_WIDTH;
	// Captures the region coordinates, which give the chunk coordinates of the chunks it holds.
	private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

	// Which kind of NBT a region file holds, and therefore where its entity UUIDs live.
	public enum RegionKind {
		// entities/*.mca: a flat Entities list.
		ENTITIES,
		// region/*.mca: terrain plus block_entities.
		REGION
	}

	private RegionFileRewriter() {
	}

	/** Copies every file in srcDir to dstDir, then regenerates entity UUIDs in the copied region files. */
	public static void rewriteDir(Path srcDir, Path dstDir, RegionKind kind) throws IOException {
		Files.createDirectories(dstDir);
		List<Path> regionFiles = new ArrayList<>();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(srcDir)) {
			for (Path entry : entries) {
				if (Files.isDirectory(entry)) {
					throw new IOException("Unexpected subdirectory in " + kind + " folder: " + entry);
				}
				Path target = dstDir.resolve(entry.getFileName().toString());
				Files.copy(entry, target);
				if (REGION_FILE.matcher(entry.getFileName().toString()).matches()) {
					regionFiles.add(target);
				}
			}
		}
		// Rewriting adds and removes .mcc files in dstDir, so the listing above must be complete first.
		for (Path regionFile : regionFiles) {
			rewriteFile(regionFile, kind);
		}
	}

	private static void rewriteFile(Path file, RegionKind kind) throws IOException {
		Matcher matcher = REGION_FILE.matcher(file.getFileName().toString());
		if (!matcher.matches()) {
			throw new IOException("Region file name does not encode coordinates: " + file);
		}
		int regionX = Integer.parseInt(matcher.group(1));
		int regionZ = Integer.parseInt(matcher.group(2));

		int rewritten = 0;
		int untouched = 0;
		try (RegionAccess region = WorldStorageAdapters.get().openRegion(file)) {
			for (int i = 0; i < CHUNK_COUNT; i++) {
				int chunkX = regionX * REGION_WIDTH + (i % REGION_WIDTH);
				int chunkZ = regionZ * REGION_WIDTH + (i / REGION_WIDTH);
				ReadWriteNBT chunk = region.readChunk(chunkX, chunkZ);
				if (chunk == null) {
					continue;
				}
				if (regenChunk(chunk, kind)) {
					region.writeChunk(chunkX, chunkZ, chunk);
					rewritten++;
				} else {
					untouched++;
				}
			}
		}
		MMLog.trace("WorldCopier: region " + file.getFileName() + " rewritten=" + rewritten
			+ " untouched=" + untouched);
	}

	// Regenerates UUIDs in a parsed chunk according to its region kind; returns whether anything changed.
	private static boolean regenChunk(ReadWriteNBT chunk, RegionKind kind) {
		String listKey = kind == RegionKind.ENTITIES ? "Entities" : "block_entities";
		if (!chunk.hasTag(listKey)) {
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
