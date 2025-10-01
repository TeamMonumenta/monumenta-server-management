package com.destroystokyo.paper.event.player;

import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the server saves the primary .dat data for a player
 */
public class PlayerDataSaveEvent extends PlayerEvent implements Cancellable {
	private static final HandlerList mHandlers = new HandlerList();
	@NotNull private final Object mData;
	@NotNull private File mPath;
	private boolean mCancel = false;

	public PlayerDataSaveEvent(@NotNull Player who, @NotNull File path, @NotNull Object data) {
		super(who);
		this.mData = data;
		this.mPath = path;
	}

	/**
	 * Get the file path where player data will be saved to.
	 *
	 * @return player data File to save to
	 */
	@NotNull
	public File getPath() {
		return mPath;
	}

	/**
	 * Set the file path where player data will be saved to.
	 */
	public void setPath(@NotNull File path) {
		this.mPath = path;
	}

	/**
	 * Get the NBTTagCompound player data that will be saved.
	 *
	 * @return NBTTagCompound player data
	 */
	@NotNull
	public Object getData() {
		return mData;
	}

	@Override
	public boolean isCancelled() {
		return mCancel;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.mCancel = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return mHandlers;
	}

	@NotNull
	public static HandlerList getHandlerList() {
		return mHandlers;
	}
}
