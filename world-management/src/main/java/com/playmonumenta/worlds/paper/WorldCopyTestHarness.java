package com.playmonumenta.worlds.paper;

import com.playmonumenta.worlds.common.MMLog;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.apache.logging.log4j.Level;

/*
 * Test harness for WorldCopier, driven by world_copy_tests/run_java_test.py.
 *
 * When MONUMENTA_WORLD_COPY_TEST is set, WorldManagementPlugin.onLoad() delegates here instead of
 * starting normally. Every fixture is copied from the inputs dir to the outputs dir, then the JVM halts.
 * Running in onLoad (before any onEnable) means no runtime dependencies - notably Redis - are needed.
 * Output correctness is validated afterward by validate.py; this only reports whether the copy threw.
 */
public final class WorldCopyTestHarness {
	private static final String ENABLE_ENV = "MONUMENTA_WORLD_COPY_TEST";
	private static final String INPUTS_ENV = "MONUMENTA_WORLD_COPY_TEST_INPUTS";
	private static final String OUTPUTS_ENV = "MONUMENTA_WORLD_COPY_TEST_OUTPUTS";
	// Optional: TRACE|DEBUG|INFO|WARN|ERROR; raises the WorldManagement log level (e.g. to see
	// WorldCopier's per-chunk MMLog.trace output)
	private static final String LOG_LEVEL_ENV = "MONUMENTA_WORLD_COPY_TEST_LOG_LEVEL";
	private static final String LOG_PREFIX = "[world-copy-test] ";

	public static boolean isEnabled() {
		String value = System.getenv(ENABLE_ENV);
		return value != null && !value.isEmpty();
	}

	/** Copies every fixture, logs per-fixture results, and halts the JVM. Never returns. */
	public static void runAndExit() {
		int exitCode;
		try {
			exitCode = run();
		} catch (RuntimeException | IOException ex) {
			MMLog.severe(LOG_PREFIX + "harness aborted", ex);
			exitCode = 2;
		}
		// Server is stopped mid-startup deliberately; nothing else has loaded yet.
		System.exit(exitCode);
	}

	private static int run() throws IOException {
		applyLogLevel();
		Path inputs = requireDir(INPUTS_ENV);
		Path outputs = Path.of(requireEnv(OUTPUTS_ENV));
		Files.createDirectories(outputs);
		MMLog.info(LOG_PREFIX + "copying fixtures from " + inputs + " to " + outputs);

		List<Path> fixtures = new ArrayList<>();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(inputs)) {
			for (Path entry : entries) {
				if (Files.isDirectory(entry)) {
					fixtures.add(entry);
				}
			}
		}
		Collections.sort(fixtures);

		int passed = 0;
		int failed = 0;
		for (Path fixture : fixtures) {
			String name = fixture.getFileName().toString();
			try {
				WorldCopier.copyWorldRegenUuids(fixture, outputs.resolve(name));
				MMLog.info(LOG_PREFIX + "PASS " + name);
				passed++;
			} catch (IOException | RuntimeException | Error ex) {
				MMLog.severe(LOG_PREFIX + "FAIL " + name, ex);
				failed++;
			}
		}

		MMLog.info(LOG_PREFIX + "done: " + passed + " copied, " + failed + " failed");
		return failed == 0 ? 0 : 1;
	}

	private static void applyLogLevel() {
		String value = System.getenv(LOG_LEVEL_ENV);
		if (value == null || value.isEmpty()) {
			return;
		}
		Level level = Level.getLevel(value.toUpperCase(java.util.Locale.ROOT));
		if (level == null) {
			MMLog.warning(LOG_PREFIX + LOG_LEVEL_ENV + "=" + value + " is not a valid log level; ignoring");
			return;
		}
		MMLog.setLevel(level);
		MMLog.info(LOG_PREFIX + "log level for WorldManagement set to " + level);
	}

	private static Path requireDir(String envName) throws IOException {
		Path dir = Path.of(requireEnv(envName));
		if (!Files.isDirectory(dir)) {
			throw new IOException(envName + "=" + dir + " is not a directory");
		}
		return dir;
	}

	private static String requireEnv(String envName) {
		String value = System.getenv(envName);
		if (value == null || value.isEmpty()) {
			throw new IllegalStateException("Required env var " + envName + " is not set");
		}
		return value;
	}
}
