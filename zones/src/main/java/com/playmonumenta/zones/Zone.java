package com.playmonumenta.zones;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;

/*
 * A zone, to be split into fragments. This class holds the name and properties, and the fragments determine
 * if a point is inside the zone after overlaps are taken into account.
 */
public class Zone extends ZoneBase {
	private final ZoneNamespace mNamespace;
	private final String mName;
	private final String mWorldRegex;
	private final Set<String> mProperties = new LinkedHashSet<>();

	/*
	 * pos1 and pos2 are used similar to /fill:
	 * - Both are inclusive coordinates.
	 * - The minimum/maximum are determined for you.
	 */
	public Zone(ZoneNamespace namespace, String worldRegex, Vector pos1, Vector pos2, String name, Set<String> properties) {
		super(pos1, pos2);
		mNamespace = namespace;
		mWorldRegex = worldRegex;
		mName = name;
		mProperties.addAll(properties);
	}

	public ZoneNamespace getNamespace() {
		return mNamespace;
	}

	public String getNamespaceName() {
		return mNamespace.getName();
	}

	public String getWorldRegex() {
		return mWorldRegex;
	}

	public boolean matchesWorld(World world) {
		return ZoneManager.getInstance().getWorldRegexMatcher().matches(world, mWorldRegex);
	}

	public boolean matchesWorld(String worldName) {
		return ZoneManager.getInstance().getWorldRegexMatcher().matches(worldName, mWorldRegex);
	}

	public boolean within(Location location) {
		return matchesWorld(location.getWorld()) && within(location.toVector());
	}

	public String getName() {
		return mName;
	}

	private static List<String> getProperties(@Nullable JsonElement propertiesElement) throws Exception {
		if (propertiesElement == null) {
			throw new Exception("Failed to parse 'properties'");
		}
		@Nullable JsonArray propertiesArray = propertiesElement.getAsJsonArray();
		if (propertiesArray == null) {
			throw new Exception("Failed to parse 'properties'");
		}
		List<String> rawProperties = new ArrayList<>();
		for (JsonElement element : propertiesArray) {
			String propertyName = element.getAsString();
			if (propertyName == null || propertyName.isBlank()) {
				throw new Exception("Property may not be empty");
			}
			rawProperties.add(propertyName);
		}
		return rawProperties;
	}

	public Set<String> getProperties() {
		return Collections.unmodifiableSet(mProperties);
	}

	public boolean hasProperty(String propertyName) {
		boolean negate = propertyName.charAt(0) == '!';
		if (negate) {
			propertyName = propertyName.substring(1);
		}
		return negate ^ mProperties.contains(propertyName);
	}

	@Override
	public boolean equals(Object o) {
		if (o instanceof Zone other) {
			return (super.equals(other) &&
				getNamespaceName().equals(other.getNamespaceName()) &&
				getName().equals(other.getName()));
		} else {
			return false;
		}
	}

	@Override
	public int hashCode() {
		int result = super.hashCode();
		result = 31*result + getNamespaceName().hashCode();
		result = 31*result + getName().hashCode();
		return result;
	}

	@Override
	public String toString() {
		return ("Zone(namespace('" + getNamespaceName() + "'), "
			+ getWorldRegex() + ", "
			+ minCorner().toString() + ", "
			+ maxCorner().toString() + ", "
			+ mName + ", "
			+ mProperties.toString() + ")");
	}
}
