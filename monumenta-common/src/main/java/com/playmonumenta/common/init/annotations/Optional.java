package com.playmonumenta.common.init.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a constructor or method parameter as an optional dependency. If the dependency was not
 * loaded (e.g. because it is itself optional and returned null), null is injected instead.
 * Should be paired with {@code @Nullable} for static analysis.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface Optional {
}
