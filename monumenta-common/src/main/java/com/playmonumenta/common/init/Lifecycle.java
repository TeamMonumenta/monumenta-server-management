package com.playmonumenta.common.init;

/**
 * A default-provided injected service to register lifecycle tasks.
 */
public interface Lifecycle {
	/**
	 * Registers a lifecycle event.
	 *
	 * @param phase the lifecycle phase
	 * @param task  the task to run
	 */
	void on(InitPhase phase, Runnable task);
}
