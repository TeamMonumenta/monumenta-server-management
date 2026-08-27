package com.playmonumenta.common.init;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

class LifecycleImpl implements Lifecycle {
	private final Map<InitPhase, List<Runnable>> mTasks = new EnumMap<>(InitPhase.class);

	@Override
	public void on(InitPhase phase, Runnable task) {
		mTasks.computeIfAbsent(phase, k -> new ArrayList<>()).add(task);
	}

	void runPhase(InitPhase phase) {
		final var tasks = mTasks.getOrDefault(phase, List.of());
		for (final var task : tasks) {
			task.run();
		}
	}
}
