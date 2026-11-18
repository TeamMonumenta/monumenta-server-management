package com.playmonumenta.common.event;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ZonePropertyChangeEvent extends Event {
	public enum Change {
		ADDED,
		REMOVED,
	}

	private static final HandlerList handlers = new HandlerList();

	private final Player mPlayer;
	private final String mNamespaceName;
	private final String mProperty;
	private final Change mChange;

	public ZonePropertyChangeEvent(Player player, String namespaceName, String property, Change change) {
		mPlayer = player;
		mNamespaceName = namespaceName;
		mProperty = property;
		mChange = change;
	}

	public Player getPlayer() {
		return mPlayer;
	}

	public String getNamespaceName() {
		return mNamespaceName;
	}

	/**
	 * The property name, without any prefix. Read {@link #getChange()} to tell whether the
	 * player entered the zone that grants it or left it.
	 */
	public String getProperty() {
		return mProperty;
	}

	public Change getChange() {
		return mChange;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
