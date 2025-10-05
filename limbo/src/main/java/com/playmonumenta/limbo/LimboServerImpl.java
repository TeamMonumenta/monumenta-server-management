package com.playmonumenta.limbo;

import com.playmonumenta.limbo.api.LimboEventHandler;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

class LimboServerImpl implements LimboServer {
	final ExecutorService pool = Executors.newThreadPerTaskExecutor(
		Thread.ofVirtual().name("LimboWorker-", 0).factory()
	);
	private ServerSocketChannel server;
	private SocketAddress address;
	final AtomicBoolean stop = new AtomicBoolean(false);

	final ServerFlags flags;
	final LimboEventHandler eventHandler;

	LimboServerImpl(ServerFlags flags) {
		this.flags = flags;
		this.eventHandler = flags.eventHandler();
	}

	private void configureSocket(SocketChannel client) throws IOException {
		if (client.getLocalAddress() instanceof InetSocketAddress) {
			final var socket = client.socket();
			socket.setSendBufferSize(flags.sockSendBufSize());
			socket.setReceiveBufferSize(flags.sockRecvBufSize());
			socket.setTcpNoDelay(flags.socketTcpNoDelay());
			socket.setSoTimeout(flags.socketTimeout());
		}
	}

	private void acceptLoop() {
		while (!stop.get()) {
			try {
				final var channel = server.accept();
				configureSocket(channel);
				pool.execute(new ConnectionImpl(this, channel)::spin);
			} catch (AsynchronousCloseException ignored) {
				// We are exiting, bye bye!
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}
	}

	void start(SocketAddress address) throws IOException {
		if (server != null) {
			throw new IllegalStateException("server already started");
		}

		this.address = address;
		final var family = switch (address) {
			case InetSocketAddress inetAddr -> inetAddr.getAddress().getAddress().length == 4 ?
				StandardProtocolFamily.INET :
				StandardProtocolFamily.INET6;
			case UnixDomainSocketAddress ignored -> StandardProtocolFamily.UNIX;
			default -> throw new IllegalArgumentException("Bad argument type: " + address);
		};

		server = ServerSocketChannel.open(family);
		server.bind(address);
		pool.execute(this::acceptLoop);
	}

	public void stop() throws IOException {
		if (stop.getAndSet(true)) {
			return;
		}

		if (server != null) {
			server.close();
		}

		if (address instanceof UnixDomainSocketAddress unixSocketAddr) {
			Files.deleteIfExists(unixSocketAddr.getPath());
		}
	}
}
