package com.playmonumenta.redissync.player;

public record HistoryMetaData(Reason reason, long timestamp) {
	public enum Reason {
		DISCONNECT,
		ADVANCEMENT_RELOAD;

		public HistoryMetaData create() {
			return new HistoryMetaData(this, System.currentTimeMillis());
		}
	}
}
