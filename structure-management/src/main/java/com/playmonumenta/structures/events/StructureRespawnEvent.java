package com.playmonumenta.structures.events;

import com.playmonumenta.structures.managers.RespawningStructure;
import java.time.Instant;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fires when a structure respawns successfully without throwing an exception. Not cancellable. */
public class StructureRespawnEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final RespawningStructure mStructure;
	private final boolean mForcedRespawn;
	private final boolean mConquered;
	private final Instant mTimestamp;

	public StructureRespawnEvent(RespawningStructure structure, boolean forcedRespawn, boolean conquered) {
		mStructure = structure;
		mForcedRespawn = forcedRespawn;
		mConquered = conquered;
		mTimestamp = Instant.now();
	}

	public RespawningStructure getStructure() {
		return mStructure;
	}

	public boolean isForcedRespawn() {
		return mForcedRespawn;
	}

	public boolean isConquered() {
		return mConquered;
	}

	public Instant getTimestamp() {
		return mTimestamp;
	}

	// Mandatory Event Methods

	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
