package com.playmonumenta.limbo;

import com.playmonumenta.limbo.api.LimboEventHandler;

public record ServerFlags(
	int writeBufferInitialSize, int readBufferInitialSize, int sockSendBufSize,
	int sockRecvBufSize, boolean socketTcpNoDelay, int socketTimeout,
	LimboEventHandler eventHandler, boolean modernForwarding
) {
	public static ServerFlagsBuilder builder() {
		return new ServerFlagsBuilder();
	}

	public static class ServerFlagsBuilder {
		private int writeBufferInitialSize = 4096;
		private int readBufferInitialSize = 4096;
		private int sockSendBufSize = 4096;
		private int sockRecvBufSize = 4096;
		private boolean socketTcpNoDelay = true;
		private int socketTimeout = 15000;
		private LimboEventHandler eventHandler = new LimboEventHandler() {
		};
		private boolean modernForwarding = false;

		private ServerFlagsBuilder() {
		}

		public ServerFlagsBuilder writeBufferInitialSize(int writeBufferInitialSize) {
			this.writeBufferInitialSize = writeBufferInitialSize;
			return this;
		}

		public ServerFlagsBuilder readBufferInitialSize(int readBufferInitialSize) {
			this.readBufferInitialSize = readBufferInitialSize;
			return this;
		}

		public ServerFlagsBuilder sockSendBufSize(int sockSendBufSize) {
			this.sockSendBufSize = sockSendBufSize;
			return this;
		}

		public ServerFlagsBuilder sockRecvBufSize(int sockRecvBufSize) {
			this.sockRecvBufSize = sockRecvBufSize;
			return this;
		}

		public ServerFlagsBuilder socketTcpNoDelay(boolean socketTcpNoDelay) {
			this.socketTcpNoDelay = socketTcpNoDelay;
			return this;
		}

		public ServerFlagsBuilder socketTimeout(int socketTimeout) {
			this.socketTimeout = socketTimeout;
			return this;
		}

		public ServerFlagsBuilder eventHandler(LimboEventHandler eventHandler) {
			this.eventHandler = eventHandler;
			return this;
		}

		public ServerFlagsBuilder modernForwarding(boolean modernForwarding) {
			this.modernForwarding = modernForwarding;
			return this;
		}

		public ServerFlags build() {
			return new ServerFlags(
				writeBufferInitialSize, readBufferInitialSize, sockSendBufSize,
				sockRecvBufSize, socketTcpNoDelay, socketTimeout, eventHandler,
				modernForwarding
			);
		}
	}
}
