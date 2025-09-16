package com.playmonumenta.structures.api.service;

import java.util.List;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

public interface ZoneService {
	interface ZoneInstance {
	}

	boolean isInside(Location loc);

	ZoneInstance registerInsideZone(Vector lowerCorner, Vector upperCorner, World world, String name);

	ZoneInstance registerNearbyZone(Vector lowerCorner, Vector upperCorner, World world, String name);

	void reload();

	void reset();

	List<ZoneInstance> findZones(Vector loc, boolean includeNearby);
}
