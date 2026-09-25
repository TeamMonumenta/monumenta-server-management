package com.playmonumenta.papermixins.paperapi.v1.event;

import java.nio.file.Path;
import org.bukkit.OfflinePlayer;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when the server loads the playerdata data for a player
 */
public class PlayerDataLoadEvent extends Event {
	private static final HandlerList handlers = new HandlerList();

	@Nullable
	private Object mData;
	@NotNull
	private Path mPath;
	@NotNull
	private final OfflinePlayer mPlayer;

	public PlayerDataLoadEvent(@NotNull OfflinePlayer who, @NotNull Path path) {
		mPlayer = who;
		mData = null;
		mPath = path;
	}

	public @NotNull OfflinePlayer getPlayer() {
		return mPlayer;
	}

	@NotNull
	public static HandlerList getHandlerList() {
		return handlers;
	}

	/**
	 * Get the file path where data will be loaded from.
	 * <p>
	 * Data will only be loaded from here if the data is not directly set by {@link #setData}
	 *
	 * @return data File to load from
	 */
	@NotNull
	public Path getPath() {
		return mPath;
	}

	/**
	 * Set the file path where data will be loaded from.
	 * <p>
	 * Data will only be loaded from here if the data is not directly set by {@link #setData}
	 *
	 * @param path data File to load from
	 */
	public void setPath(@NotNull Path path) {
		this.mPath = path;
	}

	/**
	 * Get the data supplied by an earlier call to {@link #setData}.
	 * <p>
	 * This data will be used instead of loading the player's file. It is null unless
	 * supplied by a plugin.
	 *
	 * @return NBTTagCompound data of the player's .dat file as set by {@link #setData}
	 */
	@Nullable
	public Object getData() {
		return mData;
	}

	/**
	 * Set the data to use for the player's data instead of loading it from a file.
	 * <p>
	 * This data will be used instead of loading the player's .dat file. It is null unless
	 * supplied by a plugin.
	 *
	 * @param data NBTTagCompound data to load. If null, load from file
	 */
	public void setData(@Nullable Object data) {
		this.mData = data;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	@Override
	public String toString() {
		return "PlayerDataLoadEvent{" +
			"data=" + mData +
			", path=" + mPath +
			", player=" + mPlayer.getName() +
			'}';
	}
}
