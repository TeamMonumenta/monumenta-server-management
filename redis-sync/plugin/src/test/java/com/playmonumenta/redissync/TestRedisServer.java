package com.playmonumenta.redissync;

import java.io.IOException;
import java.net.ServerSocket;
import org.jetbrains.annotations.Nullable;
import redis.embedded.RedisServer;

/**
 * The redis the tests talk to, shared by every test in the JVM. Each test keeps to its own keys.
 *
 * <p>Prefers an external server named by the {@code MRS_TEST_REDIS} env var ({@code host:port}),
 * which is how this runs against a container sidecar or a redis the host already has. Otherwise
 * starts the redis binary bundled in embedded-redis, so no install and no docker are required,
 * and stops it when the JVM exits.
 */
public final class TestRedisServer {
	private static final String ENV_VAR = "MRS_TEST_REDIS";
	private static @Nullable TestRedisServer INSTANCE = null;

	private final String mHost;
	private final int mPort;

	private TestRedisServer(String host, int port) {
		mHost = host;
		mPort = port;
	}

	public static synchronized TestRedisServer get() throws IOException {
		TestRedisServer instance = INSTANCE;
		if (instance == null) {
			instance = start();
			INSTANCE = instance;
		}
		return instance;
	}

	private static TestRedisServer start() throws IOException {
		String external = System.getenv(ENV_VAR);
		if (external != null && !external.isBlank()) {
			int split = external.lastIndexOf(':');
			if (split < 0) {
				throw new IllegalArgumentException(ENV_VAR + " must be host:port, got '" + external + "'");
			}
			return new TestRedisServer(external.substring(0, split), Integer.parseInt(external.substring(split + 1)));
		}

		/*
		 * Binding a port to learn it is free and then handing it to redis is a race, so retry.
		 * embedded-redis has no "pick your own port and tell me" mode.
		 */
		IOException last = null;
		for (int attempt = 0; attempt < 5; attempt++) {
			int port;
			try (ServerSocket probe = new ServerSocket(0)) {
				port = probe.getLocalPort();
			}
			RedisServer server = RedisServer.newRedisServer().port(port).setting("save ''").build();
			try {
				server.start();
			} catch (IOException ex) {
				last = ex;
				continue;
			}
			Runtime.getRuntime().addShutdownHook(new Thread(() -> {
				try {
					server.stop();
				} catch (IOException ignored) {
					/* The JVM is exiting; the process goes with it or is left to the OS */
				}
			}, "mrs-test-redis-stop"));
			return new TestRedisServer("localhost", port);
		}
		throw new IOException("Could not start an embedded redis after 5 attempts. Set " + ENV_VAR
			+ "=host:port to use an external redis instead.", last);
	}

	public String getHost() {
		return mHost;
	}

	public int getPort() {
		return mPort;
	}
}
