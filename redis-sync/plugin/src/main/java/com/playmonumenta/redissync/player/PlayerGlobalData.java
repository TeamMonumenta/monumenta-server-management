package com.playmonumenta.redissync.player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * <i>package-private because this is only used by {@link LocalRedisPlayer} as a temporary object to wrap json</i>
 *
 * @param stashes
 * @param profiles
 * @param currentProfile
 * @param history
 */
record PlayerGlobalData(
	Map<String, UUID> stashes,
	Map<String, UUID> profiles,
	String currentProfile,
	List<LocalRedisPlayer.HistoryEntry> history
) {
}
