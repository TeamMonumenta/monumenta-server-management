package com.destroystokyo.paper.event.player;

import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Called when the server saves the advancement data for a player
 */
public class PlayerAdvancementDataSaveEvent extends PlayerEvent implements Cancellable {
	private static final HandlerList mHandlers = new HandlerList();
	@NotNull private String mJsonData;
	@NotNull private File mPath;
	private boolean mCancel = false;

	public PlayerAdvancementDataSaveEvent(@NotNull Player who, @NotNull File path, @NotNull String jsonData) {
		super(who);
		this.mJsonData = jsonData;
		this.mPath = path;
	}

	/**
	 * Get the file path where advancement data will be saved to.
	 *
	 * @return advancement data File to save to
	 */
	@NotNull
	public File getPath() {
		return mPath;
	}

	/**
	 * Set the file path where advancement data will be saved to.
	 */
	public void setPath(@NotNull File path) {
		this.mPath = path;
	}

	/**
	 * Get the JSON advancements data that will be saved.
	 *
	 * @return JSON data of the player's advancements
	 */
	@NotNull
	public String getJsonData() {
		return mJsonData;
	}

	/**
	 * Set the JSON advancements data that will be saved.
	 *
	 * @param jsonData advancement data JSON string to save instead
	 */
	public void setJsonData(@NotNull String jsonData) {
		this.mJsonData = jsonData;
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
