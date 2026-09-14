package com.playmonumenta.common.init.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Helper to inject a {@link java.nio.file.Path} object, which is resolved relative to the plugin's configuration base.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ConfigPath {
	String value();
}
