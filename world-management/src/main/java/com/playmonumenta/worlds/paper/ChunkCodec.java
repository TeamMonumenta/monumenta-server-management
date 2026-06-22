package com.playmonumenta.worlds.paper;

import de.tr7zw.nbtapi.NBT;
import de.tr7zw.nbtapi.iface.ReadWriteNBT;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.DataFormatException;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.Inflater;

/*
 * Bridges Anvil chunk compression to the NBT-API, which only reads and writes gzip-wrapped NBT.
 * Anvil stores chunks as gzip (type 1), zlib (type 2), or uncompressed (type 3); all three are
 * read and always written as zlib (type 2), matching vanilla.
 *
 * Truncation tolerance is load-bearing, not defensive: real Anvil writers (including quarry, the
 * fixture generator) omit the trailing checksum and under-report the 4-byte length field.
 * InflaterInputStream/GZIPInputStream throw on these streams; instead the whole sector region is
 * passed to a raw Inflater that stops at end-of-stream. The NBT payload is always complete before
 * the truncation point.
 */
public final class ChunkCodec {
	// Anvil per-chunk compression types.
	public static final int GZIP_COMPRESSION = 1;
	public static final int ZLIB_COMPRESSION = 2;
	public static final int UNCOMPRESSED = 3;

	private ChunkCodec() {
	}

	/** Parses a chunk's data region (inline sector bytes or .mcc file) into NBT. */
	public static ReadWriteNBT decode(byte[] data, int compType) throws IOException {
		byte[] rawNbt;
		switch (compType) {
			case GZIP_COMPRESSION:
				// gzip = header, a raw deflate stream, then a trailer. Skip the header and inflate the
				// deflate body directly (nowrap) so a missing/truncated trailer is tolerated.
				rawNbt = inflate(data, gzipBodyOffset(data), true);
				break;
			case ZLIB_COMPRESSION:
				rawNbt = inflate(data, 0, false);
				break;
			case UNCOMPRESSED:
				rawNbt = data;
				break;
			default:
				throw new IOException("Unknown chunk compression type " + compType);
		}
		return NBT.readNBT(new ByteArrayInputStream(gzip(rawNbt)));
	}

	/** Serializes a chunk as a bare zlib (type 2) payload, unwrapping NBT-API's gzip output first. */
	public static byte[] encodeZlib(ReadWriteNBT chunk) throws IOException {
		ByteArrayOutputStream gzipped = new ByteArrayOutputStream();
		chunk.writeCompound(gzipped);
		return deflateZlib(gunzip(gzipped.toByteArray()));
	}

	// Inflates tolerantly: stops on needsInput rather than draining to EOF, so truncated checksums
	// don't throw. nowrap=true selects raw deflate (gzip body); false selects zlib-framed.
	private static byte[] inflate(byte[] data, int offset, boolean nowrap) throws IOException {
		Inflater inf = new Inflater(nowrap);
		inf.setInput(data, offset, data.length - offset);
		ByteArrayOutputStream out = new ByteArrayOutputStream(Math.max(64, data.length * 4));
		byte[] buf = new byte[8192];
		try {
			while (!inf.finished()) {
				int n = inf.inflate(buf);
				if (n > 0) {
					out.write(buf, 0, n);
				} else if (inf.finished() || inf.needsDictionary() || inf.needsInput()) {
					// Finished or input exhausted (trailer truncated) - NBT is complete.
					break;
				}
			}
		} catch (DataFormatException ex) {
			throw new IOException("Corrupt compressed chunk data", ex);
		} finally {
			inf.end();
		}
		return out.toByteArray();
	}

	// Offset of the deflate body within a gzip stream: the fixed 10-byte header plus any optional
	// FEXTRA/FNAME/FCOMMENT/FHCRC fields indicated by the flag byte.
	private static int gzipBodyOffset(byte[] d) throws IOException {
		if (d.length < 18 || (d[0] & 0xFF) != 0x1f || (d[1] & 0xFF) != 0x8b || (d[2] & 0xFF) != 0x08) {
			throw new IOException("Malformed gzip chunk header");
		}
		int flg = d[3] & 0xFF;
		int off = 10;
		if ((flg & 0x04) != 0) { // FEXTRA
			off += 2 + ((d[off] & 0xFF) | ((d[off + 1] & 0xFF) << 8));
		}
		if ((flg & 0x08) != 0) { // FNAME (zero-terminated)
			while (d[off] != 0) {
				off++;
			}
			off++;
		}
		if ((flg & 0x10) != 0) { // FCOMMENT (zero-terminated)
			while (d[off] != 0) {
				off++;
			}
			off++;
		}
		if ((flg & 0x02) != 0) { // FHCRC
			off += 2;
		}
		return off;
	}

	private static byte[] gzip(byte[] data) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(32, data.length / 2));
		try (GZIPOutputStream out = new GZIPOutputStream(baos)) {
			out.write(data);
		}
		return baos.toByteArray();
	}

	private static byte[] gunzip(byte[] data) throws IOException {
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(data))) {
			return in.readAllBytes();
		}
	}

	private static byte[] deflateZlib(byte[] data) throws IOException {
		ByteArrayOutputStream baos = new ByteArrayOutputStream(Math.max(32, data.length / 2));
		try (DeflaterOutputStream out = new DeflaterOutputStream(baos)) {
			out.write(data);
		}
		return baos.toByteArray();
	}
}
