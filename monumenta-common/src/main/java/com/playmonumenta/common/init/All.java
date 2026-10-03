package com.playmonumenta.common.init;

import java.util.List;

/**
 * Obtain all DI-managed services implementing {@code T} that have been constructed by the time the consuming service
 * is constructed. Services from later phases are not yet constructed and will not appear in the list.
 * <p>
 * The returned list is an immutable snapshot captured at construction time; it does not update as further services are
 * constructed. Unlike {@link Late}, {@code All<T>} never throws, and will result in an empty list if no valid
 * candidate services exist.
 */
@FunctionalInterface
public interface All<T> {
	List<T> get();
}
