package com.playmonumenta.redissync.event;

import com.playmonumenta.redissync.data.ContentData;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * This event is called by the RedisSync plugin to request that other plugins change the player's content,
 * and forwards along details required to do so.
 * <p/>
 * If no implementing plugin handles this event, the player's content is not updated.
 */
public class PlayerContentChangeRequestEvent extends PlayerEvent {

	private static final HandlerList HANDLERS = new HandlerList();

	private final ContentData mContent;

	public PlayerContentChangeRequestEvent(Player player, ContentData content) {
		super(player);
		mContent = content;
	}

	public ContentData getContent() {
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
