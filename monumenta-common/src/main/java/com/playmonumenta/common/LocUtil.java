package com.playmonumenta.common;

import java.util.OptionalDouble;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.jetbrains.annotations.Nullable;

/**
 * Location comparisons that force the caller to decide how cross-world and unloaded-world inputs
 * are handled. PMD flags direct use of {@code Location.distance()} / {@code distanceSquared()}
 * (UseLocUtilForDistance; use these methods instead), {@code Location.equals()}
 * (UseLocUtilForLocationEquals; use samePosition) and negating a within... method with {@code !}
 * (UseLocUtilNegation; use the matching notWithin...SameWorld / notWithin...AnyWorld).
 *
 * <h2>Why this exists</h2>
 * <ul>
 *   <li>{@code Location.distance()} and {@code distanceSquared()} throw when the two locations are
 *       in different worlds, and {@code Location.getWorld()} throws when a stored Location's world
 *       has since been unloaded. Code like this works every time in testing and only explodes in
 *       production, when a teleport, shard transfer, or instance unload puts the two inputs in
 *       different (or no) worlds. This has caused hundreds of bugs.</li>
 *   <li>A shard hosts many worlds at once, and will increasingly host different kinds of content in
 *       them (e.g. several different dungeons on one shard). "These two things are in the same
 *       world" is never a safe assumption, even inside a boss fight or a dungeon.</li>
 *   <li>There is no single correct answer for a cross-world comparison. "Is the player in range of
 *       the boss" should be false. "Has the player left the arena" should be true. "How long is
 *       this beam" does not care about worlds at all. Any short generic {@code distance(a, b)} has
 *       to silently pick one of these, and whichever it picks is wrong for a large share of
 *       callers.</li>
 * </ul>
 *
 * <p>So every method here names its cross-world behavior, and the names are deliberately verbose
 * and specific.
 *
 * <h2>Choosing a method</h2>
 * <pre>
 * Question                                       Method                          Cross-world / unloaded
 * ---------------------------------------------  ------------------------------  ----------------------
 * Is it safe to touch this location's world?     isLoaded                        n/a
 * Same world?                                    sameWorld                       false
 * Same spot (approx, ignores yaw/pitch)?         samePosition                    false
 * Within radius?                                 withinRange                     false
 * Out of range, but only if in the same world?   notWithinRangeSameWorld         false
 * Out of range, other worlds count as out?       notWithinRangeAnyWorld          true
 * Need the actual distance value?                distance(Squared)               empty, caller picks
 * Pure geometry, world is irrelevant?            distance(Squared)IgnoringWorld  computed anyway
 * </pre>
 *
 * <p>Horizontal (x/z only, y ignored) and upright cylinder versions follow the same pattern:
 * <pre>
 * Question                                       Method                                    Cross-world / unloaded
 * ---------------------------------------------  ----------------------------------------  ----------------------
 * Within horizontal radius?                      withinHorizontalRange                     false
 * Out of horizontal range, same world only?      notWithinHorizontalRangeSameWorld         false
 * Out of horizontal range, other worlds out?     notWithinHorizontalRangeAnyWorld          true
 * Need the actual horizontal distance?           horizontalDistance(Squared)               empty, caller picks
 * Pure geometry, world is irrelevant?            horizontalDistance(Squared)IgnoringWorld  computed anyway
 * Inside an upright cylinder?                    withinCylinder                            false
 * Outside cylinder, same world only?             notWithinCylinderSameWorld                false
 * Outside cylinder, other worlds outside?        notWithinCylinderAnyWorld                 true
 * </pre>
 */
public class LocUtil {
	private static final double POSITION_EPSILON_SQUARED = 1.0e-4 * 1.0e-4;

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
	 * Null-safe check that the entity is in the location's world. Returns false for null inputs or
	 * an unloaded-world location.
	 */
	public static boolean sameWorld(@Nullable Entity entity, @Nullable Location loc) {
		if (entity == null || loc == null || !loc.isWorldLoaded()) {
			return false;
		}
		return entity.getWorld().equals(loc.getWorld());
	}

	/**
	 * Null-safe check that both entities are in the same world. Returns false for null inputs.
	 */
	public static boolean sameWorld(@Nullable Entity a, @Nullable Entity b) {
		if (a == null || b == null) {
			return false;
		}
		return a.getWorld().equals(b.getWorld());
	}

	/**
	 * Returns true if both locations are in the same loaded world and at approximately the same
	 * x/y/z (within 1e-4 blocks). Yaw and pitch are ignored.
	 *   null, cross-world, or unloaded:    false
	 *   same world, same position:         true
	 *
	 * Prefer this over Location.equals(), which compares doubles exactly, also compares yaw/pitch,
	 * and treats locations whose worlds have unloaded as equal regardless of which world they were
	 * in.
	 */
	public static boolean samePosition(@Nullable Location a, @Nullable Location b) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return false;
		}
		return distanceSquaredIgnoringWorld(a, b) <= POSITION_EPSILON_SQUARED;
	}

	/**
	 * Returns true if both locations are on the same loaded world and within radius of each other.
	 * Unloaded worlds are treated the same as cross-world and return false.
	 *   null, cross-world, or unloaded:    false
	 *   same world, within radius:         true
	 *   same world, outside radius:        false
	 *
	 * Opposites: notWithinRangeSameWorld (true only if out of range AND same world) /
	 * notWithinRangeAnyWorld (other worlds always count as out of range).
	 * Using !withinRange is flagged by PMD.
	 */
	public static boolean withinRange(@Nullable Location a, @Nullable Location b, double radius) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return false;
		}
		return distanceSquaredIgnoringWorld(a, b) <= radius * radius;
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
		return !(distanceSquaredIgnoringWorld(a, b) <= radius * radius);
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

	/**
	 * Returns the distance between two locations, or empty when null/unloaded/cross-world.
	 * The caller must choose the fallback (e.g. orElse(Double.MAX_VALUE) to sort other worlds last).
	 * Only use this if withinRange() absolutely will not work.
	 */
	public static OptionalDouble distance(@Nullable Location a, @Nullable Location b) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(distanceIgnoringWorld(a, b));
	}

	/**
	 * Returns the squared distance between two locations, or empty when null/unloaded/cross-world.
	 * Only use this if withinRange() absolutely will not work.
	 */
	public static OptionalDouble distanceSquared(@Nullable Location a, @Nullable Location b) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(distanceSquaredIgnoringWorld(a, b));
	}

	/**
	 * @deprecated Use {@link #distance(Location, Location)}.
	 */
	@Deprecated
	public static OptionalDouble safeDistance(@Nullable Location a, @Nullable Location b) {
		return distance(a, b);
	}

	/**
	 * @deprecated Use {@link #distanceSquared(Location, Location)}.
	 */
	@Deprecated
	public static OptionalDouble safeDistanceSquared(@Nullable Location a, @Nullable Location b) {
		return distanceSquared(a, b);
	}

	/**
	 * Returns the distance between the x/y/z of two locations, ignoring their worlds entirely.
	 * Never throws for cross-world or unloaded-world inputs, and never says they are far apart.
	 *
	 * Only use this when the world genuinely does not matter: both locations describe the same
	 * piece of geometry (e.g. the start and end of a beam computed from one origin), or a
	 * cross-world answer is harmless. If either location comes from an entity that could have
	 * changed worlds, you almost certainly want withinRange() or distance() instead.
	 *
	 * Equivalent to a.toVector().distance(b.toVector()) without the allocations.
	 */
	public static double distanceIgnoringWorld(Location a, Location b) {
		return Math.sqrt(distanceSquaredIgnoringWorld(a, b));
	}

	/**
	 * Squared form of distanceIgnoringWorld(); the same caveats apply.
	 */
	public static double distanceSquaredIgnoringWorld(Location a, Location b) {
		double dx = a.getX() - b.getX();
		double dy = a.getY() - b.getY();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dy * dy + dz * dz;
	}

	/**
	 * Horizontal (x/z only, y ignored) version of withinRange(). Returns true if both locations are
	 * on the same loaded world and within radius of each other horizontally.
	 *   null, cross-world, or unloaded:    false
	 *   same world, within radius:         true
	 *   same world, outside radius:        false
	 *
	 * Opposites: notWithinHorizontalRangeSameWorld / notWithinHorizontalRangeAnyWorld.
	 * Using !withinHorizontalRange is flagged by PMD.
	 */
	public static boolean withinHorizontalRange(@Nullable Location a, @Nullable Location b, double radius) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return false;
		}
		return horizontalDistanceSquaredIgnoringWorld(a, b) <= radius * radius;
	}

	/**
	 * Horizontal version of notWithinRangeSameWorld(). Returns true only when both locations are on
	 * the same world AND horizontally farther apart than radius.
	 *   null, cross-world, or unloaded:    false
	 *   same world, within radius:         false
	 *   same world, outside radius:        true
	 */
	public static boolean notWithinHorizontalRangeSameWorld(@Nullable Location a, @Nullable Location b, double radius) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return false;
		}
		return !(horizontalDistanceSquaredIgnoringWorld(a, b) <= radius * radius);
	}

	/**
	 * Horizontal version of notWithinRangeAnyWorld(). Returns true when the locations are NOT
	 * horizontally within radius of each other, treating cross-world, null, or unloaded inputs as
	 * out of range.
	 *   null, cross-world, or unloaded:    true
	 *   same world, within radius:         false
	 *   same world, outside radius:        true
	 */
	public static boolean notWithinHorizontalRangeAnyWorld(@Nullable Location a, @Nullable Location b, double radius) {
		return !withinHorizontalRange(a, b, radius);
	}

	/**
	 * Returns the horizontal (x/z only) distance between two locations, or empty when
	 * null/unloaded/cross-world. The caller must choose the fallback.
	 * Only use this if withinHorizontalRange() absolutely will not work.
	 */
	public static OptionalDouble horizontalDistance(@Nullable Location a, @Nullable Location b) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(horizontalDistanceIgnoringWorld(a, b));
	}

	/**
	 * Returns the squared horizontal (x/z only) distance between two locations, or empty when
	 * null/unloaded/cross-world. Only use this if withinHorizontalRange() absolutely will not work.
	 */
	public static OptionalDouble horizontalDistanceSquared(@Nullable Location a, @Nullable Location b) {
		if (a == null || b == null || !sameWorld(a, b)) {
			return OptionalDouble.empty();
		}
		return OptionalDouble.of(horizontalDistanceSquaredIgnoringWorld(a, b));
	}

	/**
	 * Returns the horizontal (x/z only) distance between two locations, ignoring their worlds
	 * entirely. Same caveats as distanceIgnoringWorld(): only use this when the world genuinely does
	 * not matter (e.g. both points describe the same piece of geometry).
	 */
	public static double horizontalDistanceIgnoringWorld(Location a, Location b) {
		return Math.sqrt(horizontalDistanceSquaredIgnoringWorld(a, b));
	}

	/**
	 * Squared form of horizontalDistanceIgnoringWorld(); the same caveats apply.
	 */
	public static double horizontalDistanceSquaredIgnoringWorld(Location a, Location b) {
		double dx = a.getX() - b.getX();
		double dz = a.getZ() - b.getZ();
		return dx * dx + dz * dz;
	}

	/**
	 * Returns true if loc is on the same loaded world as center and inside the upright cylinder
	 * around center: horizontally within radius, and y within [center.y - below, center.y + above].
	 * For a cylinder centered vertically on center, pass the same half-height for below and above.
	 *   null, cross-world, or unloaded:    false
	 *   same world, inside cylinder:       true
	 *   same world, outside cylinder:      false
	 *
	 * Opposites: notWithinCylinderSameWorld / notWithinCylinderAnyWorld.
	 * Using !withinCylinder is flagged by PMD.
	 */
	public static boolean withinCylinder(@Nullable Location loc, @Nullable Location center, double radius, double below, double above) {
		if (loc == null || center == null || !sameWorld(loc, center)) {
			return false;
		}
		return insideCylinder(loc, center, radius, below, above);
	}

	/**
	 * Returns true only when loc is on the same world as center AND outside the cylinder described
	 * in withinCylinder().
	 *   null, cross-world, or unloaded:    false
	 *   same world, inside cylinder:       false
	 *   same world, outside cylinder:      true
	 */
	public static boolean notWithinCylinderSameWorld(@Nullable Location loc, @Nullable Location center, double radius, double below, double above) {
		if (loc == null || center == null || !sameWorld(loc, center)) {
			return false;
		}
		return !insideCylinder(loc, center, radius, below, above);
	}

	/**
	 * Returns true when loc is NOT inside the cylinder described in withinCylinder(), treating
	 * cross-world, null, or unloaded inputs as outside.
	 *   null, cross-world, or unloaded:    true
	 *   same world, inside cylinder:       false
	 *   same world, outside cylinder:      true
	 */
	public static boolean notWithinCylinderAnyWorld(@Nullable Location loc, @Nullable Location center, double radius, double below, double above) {
		return !withinCylinder(loc, center, radius, below, above);
	}

	private static boolean insideCylinder(Location loc, Location center, double radius, double below, double above) {
		double dy = loc.getY() - center.getY();
		return dy >= -below && dy <= above && horizontalDistanceSquaredIgnoringWorld(loc, center) <= radius * radius;
	}
}
