package com.playmonumenta.structures;

import com.playmonumenta.structures.managers.RespawningStructure;
import java.time.Instant;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class StructureConquerEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final RespawningStructure structure;
	private final Instant mTimestamp;

	public StructureConquerEvent(RespawningStructure structure) {
		this.structure = structure;
		mTimestamp = Instant.now();
	}

	public RespawningStructure getStructure() {
		return structure;
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
