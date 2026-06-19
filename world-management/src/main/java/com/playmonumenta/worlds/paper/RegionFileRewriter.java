package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBTCompoundList;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/*
 * Rewrites Anvil region files (region/ and entities/), regenerating nested entity UUIDs chunk by
 * chunk. Only one chunk is held in memory at a time.
 *
 * Each present chunk is parsed and its UUIDs regenerated. A chunk is re-serialized only when a UUID
 * actually changed (or it was stored externally); otherwise its original sectors are copied back
 * verbatim, which preserves the original compression and avoids round-tripping untouched terrain.
 */
public final class RegionFileRewriter {
	private static final int SECTOR_BYTES = 4096;
	private static final int HEADER_SECTORS = 2;
	private static final int HEADER_BYTES = SECTOR_BYTES * HEADER_SECTORS;
	private static final int CHUNK_COUNT = 1024;
	// The Anvil header stores a chunk's sector count in a single byte; larger chunks spill to a
	// sibling c.<x>.<z>.mcc file referenced via the external flag.
	private static final int MAX_SECTORS_PER_CHUNK = 255;
	private static final int EXTERNAL_FLAG = 0x80;
	// Captures the region coordinates so external chunk (.mcc) file names can be derived.
	private static final Pattern REGION_FILE = Pattern.compile("r\\.(-?\\d+)\\.(-?\\d+)\\.mca");

	// Which kind of NBT a region file holds, and therefore how its chunks are rewritten.
	public enum RegionKind {
		// entities/*.mca: a flat Entities list; chunks with no entities are dropped.
		ENTITIES,
		// region/*.mca: terrain plus block_entities; chunks are never dropped.
		REGION
	}

	/**
	 * Rewrites every *.mca file in srcDir into dstDir, regenerating entity UUIDs chunk by chunk.
	 * .mcc files are not copied here; writeChunk owns them so inline-external transitions are
	 * consistent and no orphaned .mcc is left behind.
	 */
	public static void rewriteDir(Path srcDir, Path dstDir, RegionKind kind) throws IOException {
		Files.createDirectories(dstDir);
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(srcDir)) {
			for (Path entry : entries) {
				String name = entry.getFileName().toString();
				if (Files.isDirectory(entry)) {
					throw new IOException("Unexpected subdirectory in " + kind + " folder: " + entry);
				}
				if (REGION_FILE.matcher(name).matches()) {
					MMLog.trace("WorldCopier: rewriting " + kind + " region " + name);
					rewriteFile(entry, dstDir.resolve(name), kind);
				} else if (name.endsWith(".mcc")) {
					MMLog.trace("WorldCopier: skipping external chunk file " + name + " (managed per-chunk)");
				} else {
					MMLog.trace("WorldCopier: raw-copying non-region file " + name);
					Files.copy(entry, dstDir.resolve(name));
				}
			}
		}
	}

	private static void rewriteFile(Path src, Path dst, RegionKind kind) throws IOException {
		long srcSize = Files.size(src);
		MMLog.trace("WorldCopier: rewriteFile src=" + src + " size=" + srcSize + " kind=" + kind);
		if (srcSize < HEADER_BYTES) {
			// Truncated/empty region file with no header; nothing to regenerate.
			Files.copy(src, dst);
			return;
		}

		Matcher m = REGION_FILE.matcher(src.getFileName().toString());
		if (!m.matches()) {
			throw new IOException("Region file name does not encode coordinates: " + src);
		}
		int rx = Integer.parseInt(m.group(1));
		int rz = Integer.parseInt(m.group(2));

		int[] locations = new int[CHUNK_COUNT];
		int[] timestamps = new int[CHUNK_COUNT];
		int[] newLocations = new int[CHUNK_COUNT];

		try (RandomAccessFile in = new RandomAccessFile(src.toFile(), "r");
			 RandomAccessFile out = new RandomAccessFile(dst.toFile(), "rw")) {
			byte[] header = new byte[HEADER_BYTES];
			in.readFully(header);
			ByteBuffer headerBuf = ByteBuffer.wrap(header);
			for (int i = 0; i < CHUNK_COUNT; i++) {
				locations[i] = headerBuf.getInt(i * 4);
				timestamps[i] = headerBuf.getInt(HEADER_BYTES / 2 + i * 4);
			}

			out.setLength(0);
			out.write(new byte[HEADER_BYTES]);
			int nextSector = HEADER_SECTORS;

			int rewritten = 0;
			int verbatim = 0;
			int dropped = 0;
			for (int i = 0; i < CHUNK_COUNT; i++) {
				int loc = locations[i];
				if (loc == 0) {
					continue;
				}
				int cx = rx * 32 + (i & 31);
				int cz = rz * 32 + (i >>> 5);

				byte[] rawSectors = readSectors(in, loc >>> 8, loc & 0xFF, src, i);
				int compByte = rawSectors[4] & 0xFF;
				boolean external = (compByte & EXTERNAL_FLAG) != 0;
				int compType = compByte & ~EXTERNAL_FLAG;
				// External chunks live in a sibling .mcc; inline chunks start after the 5-byte frame header.
				// The whole sector region is passed; the 4-byte length field is ignored: writers truncate the
				// trailing checksum and under-report the length. ChunkCodec handles this; see its class comment.
				byte[] data = external
					? Files.readAllBytes(mccPath(src, cx, cz))
					: Arrays.copyOfRange(rawSectors, 5, rawSectors.length);
				ReadWriteNBT chunk = ChunkCodec.decode(data, compType);

				boolean modified = regenChunk(chunk, kind);
				if (kind == RegionKind.ENTITIES && chunk.getCompoundList("Entities").size() == 0) {
					// Drop empty entity chunks rather than writing empty sections.
					dropped++;
					continue;
				}

				int sectors;
				// External chunks are always re-serialized so small ones collapse back inline and only
				// genuinely oversized data stays external. Unchanged inline chunks are copied verbatim.
				if (modified || external) {
					sectors = writeChunk(out, nextSector, ChunkCodec.encodeZlib(chunk), dst, cx, cz);
					rewritten++;
				} else {
					out.seek((long) nextSector * SECTOR_BYTES);
					out.write(rawSectors);
					sectors = loc & 0xFF;
					verbatim++;
				}
				newLocations[i] = (nextSector << 8) | sectors;
				nextSector += sectors;
			}
			MMLog.trace("WorldCopier: region " + src.getFileName() + " rewritten=" + rewritten
				+ " verbatim=" + verbatim + " dropped=" + dropped + " outSectors=" + nextSector);

			ByteBuffer outHeader = ByteBuffer.allocate(HEADER_BYTES);
			for (int i = 0; i < CHUNK_COUNT; i++) {
				outHeader.putInt(i * 4, newLocations[i]);
				outHeader.putInt(HEADER_BYTES / 2 + i * 4, newLocations[i] != 0 ? timestamps[i] : 0);
			}
			out.seek(0);
			out.write(outHeader.array());
		}
	}

	// Regenerates UUIDs in a parsed chunk according to its region kind; returns whether anything changed.
	private static boolean regenChunk(ReadWriteNBT chunk, RegionKind kind) {
		boolean modified = false;
		if (kind == RegionKind.ENTITIES) {
			for (ReadWriteNBT entity : chunk.getCompoundList("Entities")) {
				modified |= EntityUuidRegenerator.regenEntity(entity);
			}
		} else if (chunk.hasTag("block_entities")) {
			ReadWriteNBTCompoundList blockEntities = chunk.getCompoundList("block_entities");
			for (ReadWriteNBT blockEntity : blockEntities) {
				modified |= EntityUuidRegenerator.regenBlockEntity(blockEntity);
			}
		}
		return modified;
	}

	// Path of the external chunk file for chunk (cx, cz) in the same directory as regionFile.
	private static Path mccPath(Path regionFile, int cx, int cz) {
		return regionFile.resolveSibling("c." + cx + "." + cz + ".mcc");
	}

	// Reads all reserved sectors of a chunk verbatim (compressed payload plus zero padding).
	private static byte[] readSectors(RandomAccessFile in, int sectorOffset, int sectorCount, Path src, int index) throws IOException {
		if (sectorCount < 1) {
			throw new IOException("Chunk at index " + index + " in " + src + " reserves no sectors");
		}
		in.seek((long) sectorOffset * SECTOR_BYTES);
		byte[] sectors = new byte[sectorCount * SECTOR_BYTES];
		in.readFully(sectors);
		return sectors;
	}

	// Writes an already-zlib-compressed chunk inline (Anvil compression type 2); returns the number of
	// sectors it occupies. Chunks too large to address inline (> 255 sectors) spill into an external
	// c.<cx>.<cz>.mcc file, referenced by a 1-sector pointer frame.
	private static int writeChunk(RandomAccessFile out, int sector, byte[] zlibNbt,
								   Path dstRegionFile, int cx, int cz) throws IOException {
		int frameLength = zlibNbt.length + 1; // compression-type byte + data
		int totalBytes = frameLength + 4; // plus the 4-byte length prefix
		int sectors = (totalBytes + SECTOR_BYTES - 1) / SECTOR_BYTES;

		if (sectors > MAX_SECTORS_PER_CHUNK) {
			Files.write(mccPath(dstRegionFile, cx, cz), zlibNbt);
			out.seek((long) sector * SECTOR_BYTES);
			out.writeInt(1); // just the compression byte; no inline payload
			out.writeByte(ChunkCodec.ZLIB_COMPRESSION | EXTERNAL_FLAG);
			padSector(out, 5);
			return 1;
		}

		out.seek((long) sector * SECTOR_BYTES);
		out.writeInt(frameLength);
		out.writeByte(ChunkCodec.ZLIB_COMPRESSION);
		out.write(zlibNbt);
		padSector(out, totalBytes);
		return sectors;
	}

	// Zero-pads the current chunk frame out to a full sector boundary.
	private static void padSector(RandomAccessFile out, int totalBytes) throws IOException {
		int sectors = (totalBytes + SECTOR_BYTES - 1) / SECTOR_BYTES;
		int pad = sectors * SECTOR_BYTES - totalBytes;
		if (pad > 0) {
			out.write(new byte[pad]);
		}
	}
}
