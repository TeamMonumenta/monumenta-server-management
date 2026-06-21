package com.playmonumenta.common;

import java.util.OptionalDouble;
import org.bukkit.Location;
import org.jetbrains.annotations.Nullable;

@SuppressWarnings("PMD.UseLocUtilForDistance")
public class LocUtil {
	/**
	 * Returns true if the location is safe to use for world/block/distance operations.
	 * Prefer this over calling isWorldLoaded() + isChunkLoaded() directly -- the order
	 * matters and is easy to get wrong.
	 */
	public static boolean isLoaded(@Nullable Location loc) {
		return loc != null && loc.isWorldLoaded() && loc.isChunkLoaded();
	}

	/**
	 * Null-safe world equality. Returns false for null or unloaded-world locations.
	 * Returns false if the world is unloaded, even if both locations refer to the same
	 * unloaded world.
	 */
	public static boolean sameWorld(@Nullable Location a, @Nullable Location b) {
		if (a == null || b == null || !a.isWorldLoaded() || !b.isWorldLoaded()) {
			return false;
		}
		return a.getWorld().equals(b.getWorld());
	}

	/**
	 * Returns true if both locations are on the same loaded world and within radius of each other.
	 * Unloaded worlds are treated the same as cross-world and return false.
	 *   null, cross-world, or unloaded:    false
	 *   same world, within radius:         true
	 *   same world, outside radius:        false
	 *
	 * There are two options to get the opposite of this:
	 * - notWithinRangeSameWorld: true means out of range AND on the same world
	 * - notWithinRangeAnyWorld:  true means not within range; other worlds are always out of range
	 * Using !withinRange will trigger a PMD warning
	 */
	public static boolean withinRange(@Nullable Location a, @Nullable Location b, double radius) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return false;
		}
		return a.distanceSquared(b) <= radius * radius;
	}

	/**
	 * Returns true only when both locations are on the same world AND farther apart than radius.
	 *   null, cross-world, or unloaded:    false
	 *   same world, within radius:         false
	 *   same world, outside radius:        true
	 */
	public static boolean notWithinRangeSameWorld(@Nullable Location a, @Nullable Location b, double radius) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return false;
		}
		return a.distanceSquared(b) > radius * radius;
	}

	/**
	 * Returns true when the locations are NOT within radius of each other, treating cross-world,
	 * null, or unloaded inputs as out of range.
	 *   null, cross-world, or unloaded:    true
	 *   same world, within radius:         false
	 *   same world, outside radius:        true
	 *
	 * Use this when cross-world entities should count as out of range (e.g. cleanup/stop
	 * conditions). Note that unloaded worlds always return true, which is usually the right
	 * behavior for those cases.
	 */
	public static boolean notWithinRangeAnyWorld(@Nullable Location a, @Nullable Location b, double radius) {
		return !withinRange(a, b, radius);
	}

	private LocUtil() {
	}

	/**
	 * Returns the squared distance between two locations, or empty when null/unloaded/cross-world.
	 * Only use this if withinRange() absolutely will not work.
	 */
	public static OptionalDouble safeDistanceSquared(@Nullable Location a, @Nullable Location b) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(a.distanceSquared(b));
	}
}
