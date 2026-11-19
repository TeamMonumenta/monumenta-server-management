package com.destroystokyo.paper.event.player;

import java.io.File;
import java.util.UUID;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class ServerStatsDataLoadEvent extends Event {
	private static final HandlerList handlers = new HandlerList();
	@NotNull
	private final UUID mPlayerId;
	@Nullable
	private String mJsonData;
	@NotNull
	private File mPath;

	public ServerStatsDataLoadEvent(@NotNull File path) {
		String fileName = path.getName();
		if (!fileName.endsWith(".json")) {
			throw new RuntimeException("Player stats file expected to end in .json");
		}
		String uuidStr = fileName.substring(0, fileName.length() - 5);
		this.mPlayerId = UUID.fromString(uuidStr);
		this.mJsonData = null;
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
	 * Get the file path where stats data will be loaded from.
	 * <p>
	 * Data will only be loaded from here if the data is not directly set by {@link #setJsonData}
	 *
	 * @return stats data File to load from
	 */
	@NotNull
	public File getPath() {
		return mPath;
	}

	/**
	 * Set the file path where stats data will be loaded from.
	 * <p>
	 * Data will only be loaded from here if the data is not directly set by {@link #setJsonData}
	 *
	 * @param path stats data File to load from
	 */
	public void setPath(@NotNull File path) {
		this.mPath = path;
	}

	/**
	 * Get the JSON data supplied by an earlier call to {@link #setJsonData}.
	 * <p>
	 * This data will be used instead of loading the player's stats file. It is null unless
	 * supplied by a plugin.
	 *
	 * @return JSON data of the player's stats as set by {@link #setJsonData}
	 */
	@Nullable
	public String getJsonData() {
		return mJsonData;
	}

	/**
	 * Set the JSON data to use for the player's stats instead of loading it from a file.
	 * <p>
	 * This data will be used instead of loading the player's stats file. It is null unless
	 * supplied by a plugin.
	 *
	 * @param jsonData stats data JSON string to load. If null, load from file
	 */
	public void setJsonData(@Nullable String jsonData) {
		this.mJsonData = jsonData;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	@Override
	public String toString() {
		return "ServerStatsDataLoadEvent{" +
		       "jsonData='" + mJsonData + '\'' +
		       ", path=" + mPath +
		       ", playerId=" + mPlayerId +
		       '}';
	}
}
