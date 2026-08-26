package com.playmonumenta.worldcopytests;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import org.bukkit.Chunk;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Throwaway plugin for run_load_test.py: loads each world named in MONUMENTA_WORLD_LOAD_VERIFY
 * through Minecraft's own world loader and chunk deserializer, then reports what came back.
 *
 * <p>Not part of the gradle build; run_load_test.py compiles it with plain javac.
 */
public class WorldLoadVerifier extends JavaPlugin {
	private static final String WORLDS_ENV = "MONUMENTA_WORLD_LOAD_VERIFY";
	private static final int LOCATIONS = 1024;

	private int mFailures = 0;

	@Override
	public void onEnable() {
		String spec = System.getenv(WORLDS_ENV);
		if (spec != null) {
			for (String name : spec.split(",")) {
				String worldName = name.trim();
				if (!worldName.isEmpty()) {
					verifyWorld(worldName);
				}
			}
		} else {
			say("LOADFAIL - chunk=- IllegalStateException: " + WORLDS_ENV + " is not set");
			mFailures++;
		}
		say("LOADVERIFY-DONE");
		System.out.flush();
		// halt rather than shutdown: nothing here wants the worlds saved back out, and a normal
		// shutdown would rewrite every region file we just read.
		Runtime.getRuntime().halt(mFailures == 0 ? 0 : 1);
	}

	private void verifyWorld(String worldName) {
		List<int[]> chunks;
		try {
			// Enumerated before the world is created: loading a world generates fresh spawn
			// chunks, and only the chunks the copier actually produced are of interest.
			chunks = presentChunks(new File(getServer().getWorldContainer(), worldName + "/region"));
		} catch (IOException | RuntimeException ex) {
			fail(worldName, "-", ex);
			return;
		}

		World world;
		try {
			world = new WorldCreator(worldName).createWorld();
		} catch (Throwable ex) {
			fail(worldName, "-", ex);
			return;
		}
		if (world == null) {
			fail(worldName, "-", new IllegalStateException("createWorld returned null"));
			return;
		}

		int loaded = 0;
		int entities = 0;
		int blockEntities = 0;
		for (int[] pos : chunks) {
			try {
				Chunk chunk = world.getChunkAt(pos[0], pos[1]);
				entities += chunk.getEntities().length;
				blockEntities += chunk.getTileEntities().length;
				loaded++;
			} catch (Throwable ex) {
				fail(worldName, pos[0] + "," + pos[1], ex);
			}
		}
		say("LOADVERIFY " + worldName + " chunks=" + loaded + " entities=" + entities
			+ " blockEntities=" + blockEntities);
	}

	/** Chunk coordinates of every chunk with a nonzero location-table entry in region/. */
	private static List<int[]> presentChunks(File regionDir) throws IOException {
		List<int[]> chunks = new ArrayList<>();
		File[] files = regionDir.listFiles((dir, name) -> name.startsWith("r.") && name.endsWith(".mca"));
		if (files == null) {
			return chunks;
		}
		Arrays.sort(files);
		for (File file : files) {
			String[] parts = file.getName().split("\\.");
			if (parts.length != 4) {
				throw new IOException("unparseable region file name: " + file);
			}
			int regionX = Integer.parseInt(parts[1]);
			int regionZ = Integer.parseInt(parts[2]);
			byte[] header = new byte[LOCATIONS * 4];
			try (DataInputStream in = new DataInputStream(new FileInputStream(file))) {
				in.readFully(header);
			}
			ByteBuffer locations = ByteBuffer.wrap(header);
			for (int i = 0; i < LOCATIONS; i++) {
				if (locations.getInt(i * 4) != 0) {
					chunks.add(new int[] {regionX * 32 + (i % 32), regionZ * 32 + (i / 32)});
				}
			}
		}
		return chunks;
	}

	private void fail(String worldName, String chunk, Throwable ex) {
		mFailures++;
		say("LOADFAIL " + worldName + " chunk=" + chunk + " "
			+ ex.getClass().getName() + ": " + String.valueOf(ex.getMessage()).replace('\n', ' '));
		getLogger().log(Level.SEVERE, "load failure detail for " + worldName, ex);
	}

	private static void say(String line) {
		System.out.println(line);
	}
}
