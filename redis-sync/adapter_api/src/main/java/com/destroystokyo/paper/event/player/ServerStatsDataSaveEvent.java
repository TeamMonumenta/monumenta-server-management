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
	private final UUID playerId;
	@NotNull
	private String jsonData;
	@NotNull
	private File path;
	private boolean cancel = false;

	public ServerStatsDataSaveEvent(@NotNull File path, @NotNull String jsonData) {
		super();
		String fileName = path.getName();
		if (!fileName.endsWith(".json")) {
			throw new RuntimeException("Player stats file expected to end in .json");
		}
		String uuidStr = fileName.substring(0, fileName.length() - 5);
		this.playerId = UUID.fromString(uuidStr);
		this.jsonData = jsonData;
		this.path = path;
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
		return this.playerId;
	}

	/**
	 * Get the file path where advancement data will be saved to.
	 *
	 * @return advancement data File to save to
	 */
	@NotNull
	public File getPath() {
		return path;
	}

	/**
	 * Set the file path where advancement data will be saved to.
	 */
	public void setPath(@NotNull File path) {
		this.path = path;
	}

	/**
	 * Get the JSON advancements data that will be saved.
	 *
	 * @return JSON data of the player's advancements
	 */
	@NotNull
	public String getJsonData() {
		return jsonData;
	}

	/**
	 * Set the JSON advancements data that will be saved.
	 *
	 * @param jsonData advancement data JSON string to save instead
	 */
	public void setJsonData(@NotNull String jsonData) {
		this.jsonData = jsonData;
	}

	@Override
	public boolean isCancelled() {
		return cancel;
	}

	@Override
	public void setCancelled(boolean cancel) {
		this.cancel = cancel;
	}

	@NotNull
	@Override
	public HandlerList getHandlers() {
		return handlers;
	}

	@Override
	public String toString() {
		return "ServerStatsDataSaveEvent{" +
		       "playerId=" + playerId +
		       ", cancel=" + cancel +
		       ", path=" + path +
		       ", jsonData='" + jsonData + '\'' +
		       '}';
	}
}
