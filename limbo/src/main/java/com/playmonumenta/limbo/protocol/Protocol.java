package com.playmonumenta.limbo.protocol;

import com.playmonumenta.limbo.protocol.impl.Post1_20_5;
import com.playmonumenta.limbo.protocol.impl.Post1_21_2;
import com.playmonumenta.limbo.protocol.impl.V1_20_3;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;

@ApiStatus.Internal
public enum Protocol {
	V1_20_3(765, V1_20_3::new),
	V1_20_5(766, connection -> new Post1_20_5(Post1_20_5.V1_20_5_DATA, connection)),
	V1_21(767, connection -> new Post1_20_5(Post1_20_5.V1_21_DATA, connection)),
	V1_21_2(768, connection -> new Post1_21_2(Post1_21_2.V1_21_2_DATA, connection)),
	V1_21_4(769, connection -> new Post1_21_2(Post1_21_2.V1_21_4_DATA, connection)),
	V1_21_5(770, connection -> new Post1_21_2(Post1_21_2.V1_21_5_DATA, connection)),
	V1_21_6(771, connection -> new Post1_21_2(Post1_21_2.V1_21_6_DATA, connection)),
	V1_21_7(772, connection -> new Post1_21_2(Post1_21_2.V1_21_7_DATA, connection));

	private final int id;
	private final ProtocolHandler.Factory handlerFactory;

	public static final int SERVERBOUND_HANDSHAKE = 0;
	public static final int CLIENTBOUND_STATUS_RESPONSE = 0;
	public static final int CLIENTBOUND_STATUS_PONG = 1;
	public static final int SERVERBOUND_STATUS_REQUEST = 0;
	public static final int SERVERBOUND_STATUS_PING = 1;
	public static final int SERVERBOUND_LOGIN_START = 0;
	public static final int SERVERBOUND_LOGIN_ACK = 3;
	public static final int SERVERBOUND_LOGIN_PLUGIN_MESSAGE = 2;
	public static final int CLIENTBOUND_LOGIN_PLUGIN_MESSAGE = 4;
	public static final int CLIENTBOUND_LOGIN_SUCCESS = 2;
	public static final int CLIENTBOUND_LOGIN_DISCONNECT = 0;

	private static final Map<Integer, Protocol> BY_ID = Arrays.stream(values())
		.collect(Collectors.toMap(Protocol::id, protocol -> protocol));

	Protocol(int id, ProtocolHandler.Factory handlerFactory) {
		this.id = id;
		this.handlerFactory = handlerFactory;
	}

	public ProtocolHandler.Factory handlerFactory() {
		return handlerFactory;
	}

	public int id() {
		return id;
	}

	@Nullable
	public static Protocol byId(int id) {
		return BY_ID.get(id);
	}

	public boolean inRange(Protocol min, Protocol max) {
		return ordinal() >= min.ordinal() && ordinal() <= max.ordinal();
	}
}
