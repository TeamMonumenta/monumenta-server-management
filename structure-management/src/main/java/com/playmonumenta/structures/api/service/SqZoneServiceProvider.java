package com.playmonumenta.structures.api.service;

import com.playmonumenta.scriptedquests.Plugin;
import com.playmonumenta.scriptedquests.zones.Zone;
import com.playmonumenta.scriptedquests.zones.ZoneManager;
import com.playmonumenta.scriptedquests.zones.ZoneNamespace;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.regex.Pattern;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

// TODO: move this to the sq repo
public class SqZoneServiceProvider implements ZoneServiceProvider {
	private static class ZoneServiceImpl implements ZoneService {
		private record ZoneImpl(Zone sqZone) implements ZoneInstance {
		}

		private static final String ZONE_NAMESPACE_INSIDE = "Respawning Structures Inside";
		private static final String ZONE_NAMESPACE_NEARBY = "Respawning Structures Nearby";

		private final ZoneManager mZoneManager = Plugin.getInstance().mZoneManager;
		private ZoneNamespace mZoneNamespaceInside = new ZoneNamespace(ZONE_NAMESPACE_INSIDE);
		private ZoneNamespace mZoneNamespaceNearby = new ZoneNamespace(ZONE_NAMESPACE_NEARBY, true);

		@Override
		public boolean isInside(Location loc) {
			return mZoneManager.getZone(loc, ZONE_NAMESPACE_INSIDE) != null;
		}


		@Override
		public ZoneInstance registerInsideZone(Vector lowerCorner, Vector upperCorner, World world, String name) {
			return new ZoneImpl(new Zone(mZoneNamespaceInside, Pattern.quote(world.getName()), lowerCorner, upperCorner, name, new LinkedHashSet<>()));
		}

		@Override
		public ZoneInstance registerNearbyZone(Vector lowerCorner, Vector upperCorner, World world, String name) {
			return new ZoneImpl(new Zone(mZoneNamespaceInside, Pattern.quote(world.getName()), lowerCorner, upperCorner, name, new LinkedHashSet<>()));
		}

		@Override
		public void reload() {
			mZoneManager.replacePluginZoneNamespace(mZoneNamespaceInside);
			mZoneManager.replacePluginZoneNamespace(mZoneNamespaceNearby);
		}

		@Override
		public void reset() {
			mZoneNamespaceInside = new ZoneNamespace(ZONE_NAMESPACE_INSIDE);
			mZoneNamespaceNearby = new ZoneNamespace(ZONE_NAMESPACE_NEARBY, true);
		}

		@Override
		public List<ZoneInstance> findZones(Vector loc, boolean includeNearby) {
			return List.of();
		}
	}

	@Override
	public ZoneService createService() {
		return new ZoneServiceImpl();
	}
}
