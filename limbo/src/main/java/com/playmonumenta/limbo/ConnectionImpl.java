package com.playmonumenta.limbo;

import com.playmonumenta.limbo.api.Handshake;
import com.playmonumenta.limbo.api.LoginInfo;
import com.playmonumenta.limbo.network.Leb;
import com.playmonumenta.limbo.network.PacketReader;
import com.playmonumenta.limbo.network.PacketWriter;
import com.playmonumenta.limbo.network.PreBuiltPacket;
import com.playmonumenta.limbo.network.ProtocolError;
import com.playmonumenta.limbo.protocol.Protocol;
import com.playmonumenta.limbo.protocol.ProtocolHandler;
import java.io.EOFException;
import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.function.Consumer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public class ConnectionImpl implements Connection {
	private final LimboServerImpl server;
	private final SocketChannel channel;
	private final Leb.FrameDecoder decoder;
	private final Leb.FrameEncoder encoder;
	@Nullable
	private Protocol protocol;
	@Nullable
	private ProtocolHandler handler;

	@Nullable
	private LoginInfo loginInfo;
	private Phase phase = Phase.HANDSHAKE;

	ConnectionImpl(LimboServerImpl server, SocketChannel channel) {
		this.server = server;
		this.channel = channel;
		decoder = new Leb.FrameDecoder(server.flags.readBufferInitialSize(), channel);
		encoder = new Leb.FrameEncoder(server.flags.writeBufferInitialSize(), channel);
	}

	private Protocol protocol() {
		if (protocol == null) {
			throw new AssertionError();
		}
		return protocol;
	}

	private ProtocolHandler protocolHandler() {
		if (handler == null) {
			throw new AssertionError();
		}

		return handler;
	}

	public void phase(Phase newPhase) {
		phase = newPhase;
		server.eventHandler.newPhase(this, phase);
	}

	// connection state handlers
	private void disconnect() {
		server.eventHandler.disconnected();
	}

	private void kick(String message) {
		switch (phase) {
		case HANDSHAKE -> {
		}
		case LOGIN -> write(writer -> {
			writer.varInt(Protocol.CLIENTBOUND_LOGIN_DISCONNECT);
			writer.str('"' + message.replace("\"", "\\\"") + '"');
		});
		case null, default -> throw new UnsupportedOperationException("TODO");
		}

		disconnect();
	}

	// packet handlers
	private void handleHandshakePacket(PacketReader reader) {
		final var id = reader.varInt();
		ProtocolError.check(
			id == Protocol.SERVERBOUND_HANDSHAKE,
			() -> "bad handshake: packet id %s".formatted(id)
		);

		final var packet = new Handshake(
			reader.varInt(),
			reader.str(255),
			reader.i16(),
			switch (reader.varInt()) {
				case 1 -> Handshake.Intent.STATUS;
				case 2 -> Handshake.Intent.LOGIN;
				case 3 -> Handshake.Intent.TRANSFER;
				default -> throw ProtocolError.of("bad intent");
			}
		);

		server.eventHandler.handshake(packet);
		protocol = Protocol.byId(packet.protocol());
		if (protocol == null) {
			throw ProtocolError.of("unknown protocol version %s", packet.protocol());
		}

		handler = protocol().handlerFactory().create(this);

		switch (packet.intent()) {
		case STATUS -> phase(Phase.STATUS);
		case LOGIN, TRANSFER -> phase(Phase.LOGIN);
		}
	}

	private void handleStatusPacket(PacketReader reader) {
		final var id = reader.varInt();

		switch (id) {
		case Protocol.SERVERBOUND_STATUS_REQUEST -> write(pw -> {
			pw.varInt(Protocol.CLIENTBOUND_STATUS_RESPONSE);
			pw.str("{" +
				"\"version\":{\"name\":\"Limbo\",\"protocol\":-1}," +
				"\"players\":{\"max\":1,\"online\":0}," +
				"\"description\":{\"text\":\"Monumenta Limbo\"}," +
				"\"enforcesSecureChat\":false" +
				"}");
		});
		case Protocol.SERVERBOUND_STATUS_PING -> {
			final var timestamp = reader.i64();
			write(pw -> {
				pw.varInt(Protocol.CLIENTBOUND_STATUS_PONG);
				pw.i64(timestamp);
			});
		}
		default -> ProtocolError.of("unknown packet in %s: %s", phase, id);
		}
	}

	private void handleLoginPacket(PacketReader reader) {
		final var id = reader.varInt();
		switch (id) {
		case Protocol.SERVERBOUND_LOGIN_START -> {
			if (protocol == null) {
				kick("unsupported minecraft version");
				return;
			}

			if (server.flags.modernForwarding()) {
				write(writer -> {
					writer.varInt(Protocol.CLIENTBOUND_LOGIN_PLUGIN_MESSAGE);
					writer.varInt(0);
					writer.str("velocity:player_info");
				});
			}

			loginInfo = new LoginInfo(
				reader.str(16),
				reader.uuid()
			);
			server.eventHandler.loginStart(loginInfo);

			write(writer -> {
				writer.varInt(Protocol.CLIENTBOUND_LOGIN_SUCCESS);
				writer.uuid(loginInfo.uuid());
				writer.str(loginInfo.name());

				if (protocol.inRange(Protocol.V1_20_5, Protocol.V1_21)) {
					writer.flag(false);
				}

				writer.varInt(0);
			});
		}
		case Protocol.SERVERBOUND_LOGIN_ACK -> {
			ProtocolError.check(loginInfo != null, () -> "serverbound login ack sent too early");
			protocolHandler().onLoginComplete();
		}
		case Protocol.SERVERBOUND_LOGIN_PLUGIN_MESSAGE -> {
		}
		default -> ProtocolError.of("unknown packet in %s: %s", phase, id);
		}
	}

	// main loop for connection
	void spin() {
		while (!server.stop.get()) {
			try {
				decoder.read(buffer -> {
					final var reader = new PacketReader(buffer);
					switch (phase) {
					case HANDSHAKE -> handleHandshakePacket(reader);
					case STATUS -> handleStatusPacket(reader);
					case LOGIN -> handleLoginPacket(reader);
					case CONFIGURATION -> protocolHandler().handleConfigurationPacket(reader.varInt(), reader);
					case PLAY -> protocolHandler().handlePlayPacket(reader.varInt(), reader);
					}
				});
			} catch (ClosedChannelException ignored) {
				break;
			} catch (EOFException e) {
				disconnect();
				break;
			} catch (Throwable e) {
				boolean isExpected = e instanceof SocketException && e.getMessage().equals("Connection reset");

				if (!isExpected) {
					server.eventHandler.uncaughtException(e);
				}

				disconnect();
				break;
			}
		}

		try {
			channel.close();
		} catch (IOException e) {
			server.eventHandler.uncaughtException(e);
		}
	}

	public void write(Consumer<PacketWriter> action) {
		try {
			encoder.write(buffer -> {
				final var writer = new PacketWriter(buffer);
				action.accept(writer);
				return writer.buffer();
			});
		} catch (IOException e) {
			server.eventHandler.uncaughtException(e);
		}
	}

	public void write(int id) {
		write(packetWriter -> packetWriter.varInt(id));
	}

	public void write(PreBuiltPacket packet) {
		try {
			channel.write(packet.contents());
		} catch (IOException e) {
			server.eventHandler.uncaughtException(e);
		}
	}
}
