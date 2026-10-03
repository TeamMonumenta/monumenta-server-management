package com.playmonumenta.common.ext;

import org.bukkit.Bukkit;
import org.bukkit.event.Event;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.EventExecutor;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.NotNull;

/**
 * Provides a cleaner way of registering for listeners without having to declare a full method.
 */
public record ListenerRegistry(Plugin plugin) {
	public void registerListener(Listener listener) {
		Bukkit.getPluginManager().registerEvents(listener, plugin);
	}

	public <T extends Event> void registerHandler(Class<T> clazz, EventPriority priority, boolean ignoreCancelled,
												  EventCallback<T> handler) {
		Bukkit.getPluginManager().registerEvent(clazz, handler, priority, handler, plugin, ignoreCancelled);
	}

	public <T extends Event> void registerHandler(Class<T> clazz, EventPriority priority, EventCallback<T> handler) {
		registerHandler(clazz, priority, false, handler);
	}

	@FunctionalInterface
	public interface EventCallback<T extends Event> extends EventExecutor, Listener {
		@SuppressWarnings("unchecked")
		@Override
		default void execute(@NotNull Listener listener, @NotNull Event event) {
			assert listener == this;
			call((T) event);
		}

		void call(@NotNull T event);
	}
}
