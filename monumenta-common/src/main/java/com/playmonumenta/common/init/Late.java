package com.playmonumenta.common.init;

/**
 * A lazy handle to a DI-managed service. The instance is resolved on the first call to {@link #get()} rather
 * than at construction time. Useful for avoiding circular construction dependencies or holding a reference to a
 * service without forcing it to exist at the time the holder is constructed.
 *
 * <p>Declare as a constructor/method parameter to receive one. The framework verifies at build time that the
 * wrapped type is registered and non-optional. For an optional wrapped type, combine with {@code @Optional},
 * in which case {@link #get()} may return null.
 */
@FunctionalInterface
public interface Late<T> {
	T get();
}
