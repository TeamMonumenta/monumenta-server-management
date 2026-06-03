package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import com.playmonumenta.worlds.common.utils.FileUtils;
import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBTCompoundList;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.InflaterInputStream;

/*
 * Copies a Minecraft world folder in-process while regenerating every entity UUID, so the copy can
 * be loaded alongside its template without UUID collisions.
 *
 * Memory efficiency is the primary goal. Only entities/*.mca chunks are parsed and rewritten (one
 * chunk at a time); the bulk region/ block data and everything else is copied as raw bytes and never
 * decompressed. Neither the source nor destination world is loaded by Bukkit.
 *
 * Assumes the modern (1.17+) single-dimension layout where entity data lives in a flat entities/
 * folder. Nested/multi-dimension templates are unsupported and fail loudly.
 */
public final class WorldCopier {
	private static final int SECTOR_BYTES = 4096;
	private static final int HEADER_SECTORS = 2;
	private static final int HEADER_BYTES = SECTOR_BYTES * HEADER_SECTORS;
	// The Anvil header stores a chunk's sector count in a single byte.
	private static final int MAX_SECTORS_PER_CHUNK = 255;
	private static final int ZLIB_COMPRESSION = 2;
	private static final int EXTERNAL_FLAG = 0x80;
	private static final Pattern REGION_FILE = Pattern.compile("r\\.-?\\d+\\.-?\\d+\\.mca");

	private WorldCopier() {
	}

	// Copies the world folder at source to dest, regenerating all entity UUIDs and updating
	// level.dat's LevelName to match the destination folder name.
	public static void copyWorldRegenUuids(Path source, Path dest) throws IOException {
		MMLog.trace("WorldCopier: copyWorldRegenUuids source=" + source + " dest=" + dest);
		// Reject unsupported (multi-dimension/nested) layouts before copying anything, so we fail in
		// milliseconds rather than partway through copying gigabytes of region data.
		validateSingleDimension(source);

		// Start from a clean slate so retries after a partial failure don't trip over leftover files.
		if (Files.exists(dest)) {
			MMLog.trace("WorldCopier: dest exists, deleting recursively: " + dest);
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
							MMLog.trace("WorldCopier: rewriting entities dir " + entry);
							rewriteEntitiesDir(entry, target);
						} else {
							MMLog.trace("WorldCopier: raw-copying dir " + entry);
							copyTreeRaw(entry, target);
						}
					} else if (name.equals("level.dat")) {
						MMLog.trace("WorldCopier: copying level.dat " + entry);
						copyLevelDat(entry, target, dest.getFileName().toString());
					} else {
						MMLog.trace("WorldCopier: raw-copying file " + entry);
						Files.copy(entry, target);
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

	// Walks the source tree (directories only, no file reads) and rejects any entities folder below the
	// top level. The single supported layout has exactly one top-level entities/.
	private static void validateSingleDimension(Path source) throws IOException {
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(source)) {
			for (Path entry : entries) {
				if (Files.isDirectory(entry) && !entry.getFileName().toString().equals("entities")) {
					checkNoNestedEntities(entry);
				}
			}
		}
	}

	private static void checkNoNestedEntities(Path dir) throws IOException {
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(dir)) {
			for (Path entry : entries) {
				if (Files.isDirectory(entry)) {
					if (entry.getFileName().toString().equals("entities")) {
						throw new IOException("Nested 'entities' directory at " + entry
							+ "; multi-dimension/nested templates are not supported");
					}
					checkNoNestedEntities(entry);
				}
			}
		}
	}

	private static void copyLevelDat(Path src, Path dst, String destWorldName) throws IOException {
		ReadWriteNBT nbt = NBT.readFile(src.toFile());
		nbt.getOrCreateCompound("Data").setString("LevelName", destWorldName);
		NBT.writeFile(dst.toFile(), nbt);
	}

	// Recursively copies a subtree as raw bytes. Nested entity data is rejected up front by
	// validateSingleDimension, so everything here is safe to copy verbatim.
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

	private static void rewriteEntitiesDir(Path srcDir, Path dstDir) throws IOException {
		Files.createDirectories(dstDir);
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(srcDir)) {
			for (Path entry : entries) {
				String name = entry.getFileName().toString();
				if (Files.isDirectory(entry)) {
					throw new IOException("Unexpected subdirectory in entities folder: " + entry);
				}
				Path target = dstDir.resolve(name);
				if (REGION_FILE.matcher(name).matches()) {
					MMLog.trace("WorldCopier: rewriting entities region " + name);
					rewriteEntitiesRegion(entry, target);
				} else {
					MMLog.trace("WorldCopier: raw-copying non-region entities file " + name);
					Files.copy(entry, target);
				}
			}
		}
	}

	// Streams one entities region file, regenerating entity UUIDs chunk by chunk and dropping chunks
	// that hold no entities. Only one chunk is held in memory at a time.
	private static void rewriteEntitiesRegion(Path src, Path dst) throws IOException {
		long srcSize = Files.size(src);
		MMLog.trace("WorldCopier: rewriteEntitiesRegion src=" + src + " size=" + srcSize);
		if (srcSize < HEADER_BYTES) {
			// Truncated/empty region file with no header; nothing to regenerate.
			MMLog.trace("WorldCopier: region smaller than header (" + srcSize + " < " + HEADER_BYTES + "), raw-copying");
			Files.copy(src, dst);
			return;
		}

		int[] locations = new int[1024];
		int[] timestamps = new int[1024];
		int[] newLocations = new int[1024];

		try (RandomAccessFile in = new RandomAccessFile(src.toFile(), "r");
			 RandomAccessFile out = new RandomAccessFile(dst.toFile(), "rw")) {
			byte[] header = new byte[HEADER_BYTES];
			in.readFully(header);
			ByteBuffer headerBuf = ByteBuffer.wrap(header);
			for (int i = 0; i < 1024; i++) {
				locations[i] = headerBuf.getInt(i * 4);
				timestamps[i] = headerBuf.getInt(HEADER_BYTES / 2 + i * 4);
			}

			out.setLength(0);
			out.write(new byte[HEADER_BYTES]);
			int nextSector = HEADER_SECTORS;

			int present = 0;
			int dropped = 0;
			int rewritten = 0;
			for (int i = 0; i < 1024; i++) {
				int loc = locations[i];
				if (loc == 0) {
					continue;
				}
				present++;
				final int fi = i;
				final int floc = loc;
				MMLog.trace(() -> "WorldCopier: chunk index=" + fi + " loc=0x" + Integer.toHexString(floc)
					+ " sectorOffset=" + (floc >>> 8) + " sectorCount=" + (floc & 0xFF));
				byte[] chunkNbt = readChunkNbt(in, loc, src, i);
				ReadWriteNBT chunk = NBT.readNBT(new ByteArrayInputStream(chunkNbt));
				ReadWriteNBTCompoundList entityList = chunk.getCompoundList("Entities");
				MMLog.trace("WorldCopier: chunk index=" + i + " decompressedNbtBytes=" + chunkNbt.length
					+ " entityCount=" + entityList.size());
				if (entityList.size() == 0) {
					// Drop empty entity chunks rather than writing empty sections.
					dropped++;
					continue;
				}
				for (ReadWriteNBT entity : entityList) {
					regenEntity(entity);
				}

				int sectors = writeChunk(out, nextSector, chunk, src, i);
				newLocations[i] = (nextSector << 8) | sectors;
				nextSector += sectors;
				rewritten++;
			}
			MMLog.trace("WorldCopier: region " + src.getFileName() + " present=" + present
				+ " rewritten=" + rewritten + " dropped=" + dropped + " outSectors=" + nextSector);

			ByteBuffer outHeader = ByteBuffer.allocate(HEADER_BYTES);
			for (int i = 0; i < 1024; i++) {
				outHeader.putInt(i * 4, newLocations[i]);
				outHeader.putInt(HEADER_BYTES / 2 + i * 4, newLocations[i] != 0 ? timestamps[i] : 0);
			}
			out.seek(0);
			out.write(outHeader.array());
		}
	}

	// Reads and decompresses a single chunk's NBT payload.
	private static byte[] readChunkNbt(RandomAccessFile in, int loc, Path src, int index) throws IOException {
		int sectorOffset = loc >>> 8;
		int sectorCount = loc & 0xFF;
		in.seek((long) sectorOffset * SECTOR_BYTES);
		int length = in.readInt();
		int compression = in.readUnsignedByte();
		MMLog.trace("WorldCopier: readChunkNbt index=" + index + " byteOffset=" + ((long) sectorOffset * SECTOR_BYTES)
			+ " length=" + length + " compression=" + compression);
		if ((compression & EXTERNAL_FLAG) != 0) {
			throw new IOException("External (.mcc) entities chunk in " + src + " at index " + index
				+ "; oversized entities chunks are not supported");
		}
		// The frame is a 4-byte length prefix followed by length bytes (1 compression-type byte + payload),
		// all within the chunk's reserved sectors. Validate before allocating so a corrupt header can't drive a
		// negative or multi-gigabyte allocation.
		long maxLength = (long) sectorCount * SECTOR_BYTES - 4;
		if (length < 2 || length > maxLength) {
			throw new IOException("Corrupt chunk length " + length + " in " + src + " at index " + index
				+ " (sectorCount=" + sectorCount + ", max=" + maxLength + ")");
		}
		byte[] payload = new byte[length - 1];
		in.readFully(payload);
		MMLog.trace(() -> "WorldCopier: readChunkNbt index=" + index + " payloadBytes=" + payload.length
			+ " firstBytes=" + hexPreview(payload));
		return decompress(compression, payload);
	}

	// Serializes, compresses, and writes a chunk inline; returns the number of sectors it occupies.
	private static int writeChunk(RandomAccessFile out, int sector, ReadWriteNBT chunk, Path src, int index) throws IOException {
		ByteArrayOutputStream nbtBytes = new ByteArrayOutputStream();
		chunk.writeCompound(nbtBytes);
		byte[] compressed = deflate(nbtBytes.toByteArray());

		int frameLength = compressed.length + 1; // compression-type byte + data
		int totalBytes = frameLength + 4; // plus the 4-byte length prefix
		int sectors = (totalBytes + SECTOR_BYTES - 1) / SECTOR_BYTES;
		MMLog.trace("WorldCopier: writeChunk index=" + index + " sector=" + sector + " rawNbtBytes="
			+ nbtBytes.size() + " compressedBytes=" + compressed.length + " sectors=" + sectors);
		if (sectors > MAX_SECTORS_PER_CHUNK) {
			throw new IOException("Reserialized entities chunk in " + src + " at index " + index
				+ " needs " + sectors + " sectors (> " + MAX_SECTORS_PER_CHUNK + "); cannot store inline");
		}

		out.seek((long) sector * SECTOR_BYTES);
		out.writeInt(frameLength);
		out.writeByte(ZLIB_COMPRESSION);
		out.write(compressed);
		int pad = sectors * SECTOR_BYTES - totalBytes;
		if (pad > 0) {
			out.write(new byte[pad]);
		}
		return sectors;
	}

	private static void regenEntity(ReadWriteNBT entity) {
		entity.removeKey("UUIDMost");
		entity.removeKey("UUIDLeast");
		entity.removeKey("WorldUUIDMost");
		entity.removeKey("WorldUUIDLeast");
		entity.setIntArray("UUID", uuidToIntArray(UUID.randomUUID()));
		for (ReadWriteNBT passenger : entity.getCompoundList("Passengers")) {
			regenEntity(passenger);
		}
	}

	// Encodes a UUID as Minecraft's big-endian 4-int array (matches net.minecraft UUIDUtil).
	private static int[] uuidToIntArray(UUID uuid) {
		long msb = uuid.getMostSignificantBits();
		long lsb = uuid.getLeastSignificantBits();
		return new int[] {
			(int) (msb >> 32),
			(int) msb,
			(int) (lsb >> 32),
			(int) lsb,
		};
	}

	private static byte[] decompress(int compression, byte[] data) throws IOException {
		MMLog.trace("WorldCopier: decompress type=" + compression + " inBytes=" + data.length);
		switch (compression) {
			case 1:
				try (InputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
					return in.readAllBytes();
				}
			case 2:
				try (InputStream in = new InflaterInputStream(new ByteArrayInputStream(data))) {
					return in.readAllBytes();
				}
			case 3:
				return data;
			default:
				throw new IOException("Unknown chunk compression type " + compression);
		}
	}

	// Hex dump of up to the first 16 bytes of a buffer, for trace logging.
	private static String hexPreview(byte[] data) {
		StringBuilder sb = new StringBuilder();
		int n = Math.min(16, data.length);
		for (int i = 0; i < n; i++) {
			sb.append(String.format("%02x ", data[i]));
		}
		return sb.toString().trim();
	}

	private static byte[] deflate(byte[] data) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		try (DeflaterOutputStream out = new DeflaterOutputStream(baos, new Deflater(Deflater.DEFAULT_COMPRESSION))) {
			out.write(data);
		}
		return baos.toByteArray();
	}
}
