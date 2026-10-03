package com.playmonumenta.common.init.annotations;

import com.playmonumenta.common.init.InitPhase;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies the init function (or constructor) for a particular class. It may return void, in which case only
 * initialization is performed, providing no outputs.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.TYPE})
public @interface Init {
	InitPhase value() default InitPhase.LAZY;

	boolean optional() default false;
}
