package com.playmonumenta.redissync.event;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * This event is fired by the RedisSync plugin when other plugins request it,
 * allowing them to register any content that they can transfer a player to.
 */
public class UpdateAvailableContentIdsEvent extends Event {
	private static final HandlerList HANDLERS = new HandlerList();

	private final Set<String> mContent = new HashSet<>();

	public UpdateAvailableContentIdsEvent() {
	}

	public void registerContent(Set<String> content) {
		mContent.addAll(content);
	}

	public Set<String> getContentIds() {
		return Collections.unmodifiableSet(mContent);
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
