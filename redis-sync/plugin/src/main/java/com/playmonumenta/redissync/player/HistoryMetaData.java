package com.playmonumenta.redissync.player;

/**
 * Represents a single entry in the history for a player.
 *
 * @param reason    The reason for the history entry, e.g. player disconnect, advancement reload, etc.
 * @param timestamp When the history entry was created.
 */
public record HistoryMetaData(Reason reason, long timestamp) {
	public enum Reason {
		DISCONNECT,
		ADVANCEMENT_RELOAD;

		public HistoryMetaData create() {
			return new HistoryMetaData(this, System.currentTimeMillis());
		}
	}
}
