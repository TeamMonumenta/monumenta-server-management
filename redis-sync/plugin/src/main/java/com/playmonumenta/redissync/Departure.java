package com.playmonumenta.redissync;

import org.jetbrains.annotations.Nullable;

/**
 * Why a player is leaving this shard: see {@link MonumentaRedisSyncAPI#getDeparture}. A logout
 * with no departure is an ordinary logout (including a switch the proxy started on its own, such
 * as {@code /send}).
 */
public sealed interface Departure permits Departure.Transfer, Departure.Handoff {
	/** To another shard; {@code contentId} is the content the transfer was requested with, or null if none */
	record Transfer(String target, @Nullable String contentId) implements Departure {
	}

	/** A data handoff (stash get, rollback, loadFromPlayer): back to this shard with replaced data */
	record Handoff(String what) implements Departure {
	}
}
