package com.destroystokyo.paper.event.player;

import java.io.File;
import java.util.UUID;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class ServerStatsDataSaveEvent extends Event implements Cancellable {
	private static final HandlerList handlers = new HandlerList();
	@NotNull
	private final UUID mPlayerId;
	@NotNull
	private String mJsonData;
	@NotNull
	private File mPath;
	private boolean mCancel = false;

	public ServerStatsDataSaveEvent(@NotNull File path, @NotNull String jsonData) {
		super();
		String fileName = path.getName();
		if (!fileName.endsWith(".json")) {
			throw new RuntimeException("Player stats file expected to end in .json");
		}
		String uuidStr = fileName.substring(0, fileName.length() - 5);
		this.mPlayerId = UUID.fromString(uuidStr);
		this.mJsonData = jsonData;
		this.mPath = path;
	}

	@NotNull
	public static HandlerList getHandlerList() {
		return handlers;
	}

	/**
	 * Get the player UUID associated with this stats file
	 * @return the player UUID for this stats file
	 */
	@NotNull
	public UUID getPlayerId() {
		return this.mPlayerId;
	}

	/**
	 * Get the file path where stats data will be saved to.
	 *
	 * @return stats data File to save to
	 */
	@NotNull
	public File getPath() {
		return mPath;
	}

	/**
	 * Set the file path where stats data will be saved to.
	 */
	public void setPath(@NotNull File path) {
		this.mPath = path;
	}

	/**
	 * Get the JSON stats data that will be saved.
	 *
	 * @return JSON data of the player's stats
	 */
	@NotNull
	public String getJsonData() {
		return mJsonData;
	}

	/**
	 * Set the JSON stats data that will be saved.
	 *
	 * @param jsonData stats data JSON string to save instead
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
		return handlers;
	}

	@Override
	public String toString() {
		return "ServerStatsDataSaveEvent{" +
		       "playerId=" + mPlayerId +
		       ", cancel=" + mCancel +
		       ", path=" + mPath +
		       ", jsonData='" + mJsonData + '\'' +
		       '}';
	}
}
