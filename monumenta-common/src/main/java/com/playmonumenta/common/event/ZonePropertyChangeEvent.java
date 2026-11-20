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

	private static final String REMOVAL_MARKER = "!";

	private static final HandlerList handlers = new HandlerList();

	private final Player mPlayer;
	private final String mNamespaceName;
	private final String mProperty;
	private final Change mChange;

	/**
	 * Creates an event from the name form used before the change enum existed: a name that
	 * starts with '!' is a removal.
	 */
	public ZonePropertyChangeEvent(Player player, String namespaceName, String property) {
		this(player, namespaceName, nameOf(property), changeOf(property));
	}

	public ZonePropertyChangeEvent(Player player, String namespaceName, String property, Change change) {
		mPlayer = player;
		mNamespaceName = namespaceName;
		mProperty = property;
		mChange = change;
	}

	private static String nameOf(String property) {
		return property != null && property.startsWith(REMOVAL_MARKER) ? property.substring(1) : property;
	}

	private static Change changeOf(String property) {
		return property != null && property.startsWith(REMOVAL_MARKER) ? Change.REMOVED : Change.ADDED;
	}

	public Player getPlayer() {
		return mPlayer;
	}

	public String getNamespaceName() {
		return mNamespaceName;
	}

	/**
	 * The property name in the form used before the change enum existed: a removal is
	 * returned with a leading '!'.
	 *
	 * @deprecated read {@link #getPropertyName()} and {@link #getChange()} instead
	 */
	@Deprecated
	public String getProperty() {
		return mChange == Change.REMOVED ? REMOVAL_MARKER + mProperty : mProperty;
	}

	/**
	 * The property name, without any prefix. Read {@link #getChange()} to tell whether the
	 * player entered the zone that grants it or left it.
	 */
	public String getPropertyName() {
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
