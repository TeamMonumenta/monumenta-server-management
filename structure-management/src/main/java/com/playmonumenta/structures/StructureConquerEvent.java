package com.playmonumenta.structures;

import com.playmonumenta.structures.managers.RespawningStructure;
import java.time.Instant;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class StructureConquerEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final RespawningStructure mStructure;
	private final Instant mTimestamp;

	// TODO: Should some of the methods be protected?
	public StructureConquerEvent(RespawningStructure structure) {
		this.mStructure = structure;
		mTimestamp = Instant.now();
	}

	public RespawningStructure getStructure() {
		return mStructure;
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
