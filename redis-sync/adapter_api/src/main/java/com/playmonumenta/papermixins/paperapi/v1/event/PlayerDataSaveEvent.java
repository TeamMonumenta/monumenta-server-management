package com.playmonumenta.papermixins.paperapi.v1.event;

import java.nio.file.Path;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the server saves the primary .dat data for a player
 */
public class PlayerDataSaveEvent extends PlayerEvent implements Cancellable {
	private static final HandlerList handlers = new HandlerList();

	@NotNull
	private final Object mData;
	@NotNull
	private Path mPath;
	private boolean mCancel = false;

	public PlayerDataSaveEvent(@NotNull Player who, @NotNull Path path, @NotNull Object data) {
		super(who);
		mData = data;
		mPath = path;
	}

	@NotNull
	public static HandlerList getHandlerList() {
		return handlers;
	}

	/**
	 * Get the file path where player data will be saved to.
	 *
	 * @return player data File to save to
	 */
	@NotNull
	public Path getPath() {
		return mPath;
	}

	/**
	 * Set the file path where player data will be saved to.
	 */
	public void setPath(@NotNull Path path) {
		mPath = path;
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
		mCancel = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	@Override
	public String toString() {
		return "PlayerDataSaveEvent{" +
			"player=" + player.getName() +
			", cancel=" + mCancel +
			", path=" + mPath +
			", data=" + mData +
			'}';
	}
}
