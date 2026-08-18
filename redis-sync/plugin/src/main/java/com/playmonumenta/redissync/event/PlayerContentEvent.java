package com.playmonumenta.redissync.event;

import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;


public class PlayerContentEvent extends PlayerEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	private final @NotNull String mContent;

	public PlayerContentEvent(Player player, @NotNull String content) {
		super(player);
		mContent = content;
	}

	public @NotNull String getContent() {
		return mContent;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return HANDLERS;
	}

	public static HandlerList getHandlerList() {
		return HANDLERS;
	}
}
