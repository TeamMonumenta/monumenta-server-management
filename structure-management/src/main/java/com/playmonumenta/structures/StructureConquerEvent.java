package com.playmonumenta.structures;

import com.playmonumenta.structures.managers.RespawningStructure;
import java.time.Instant;
import java.util.List;
import org.bukkit.entity.Player;
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

	// Quest components are called for players within the structure
	public List<Player> getPlayersWithinStructure() {
		List<Player> players = mStructure.getWorld().getPlayers();
		players.removeIf(p -> !(mStructure.isWithin(p)));
		return players;
	}

	public List<Player> getNearbyPlayers() {
		List<Player> players = mStructure.getWorld().getPlayers();
		players.removeIf(p -> !(mStructure.isNearby(p)));
		return players;
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
