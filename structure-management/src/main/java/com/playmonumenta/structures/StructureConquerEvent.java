package com.playmonumenta.structures;

import com.playmonumenta.structures.managers.RespawningStructure;
import java.time.Instant;
import java.util.List;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

public class StructureConquerEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	private final RespawningStructure mStructure;
	private final Location mLocation;
	private final Instant mTimestamp;

	public StructureConquerEvent(RespawningStructure structure, Location location) {
		mStructure = structure;
		mLocation = location;
		mTimestamp = Instant.now();
	}

	public RespawningStructure getStructure() {
		return mStructure;
	}

	public Instant getTimestamp() {
		return mTimestamp;
	}

	// The location is the last spawner broken

	public Location getLocation() {
		return mLocation;
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
