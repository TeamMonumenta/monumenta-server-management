package com.destroystokyo.paper.event.player;

import java.io.File;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Called when the server loads the advancement data for a player
 */
public class PlayerAdvancementDataLoadEvent extends PlayerEvent {
	private static final HandlerList mHandlers = new HandlerList();
	@Nullable private String mJsonData;
	@NotNull private File mPath;

	public PlayerAdvancementDataLoadEvent(@NotNull Player who, @NotNull File path) {
		super(who);
		this.mJsonData = null;
		this.mPath = path;
	}

	/**
	 * Get the file path where advancement data will be loaded from.
	 * <p>
	 * Data will only be loaded from here if the data is not directly set by {@link #setJsonData}
	 *
	 * @return advancement data File to load from
	 */
	@NotNull
	public File getPath() {
		return mPath;
	}

	/**
	 * Set the file path where advancement data will be loaded from.
	 * <p>
	 * Data will only be loaded from here if the data is not directly set by {@link #setJsonData}
	 *
	 * @param path advancement data File to load from
	 */
	public void setPath(@NotNull File path) {
		this.mPath = path;
	}

	/**
	 * Get the JSON data supplied by an earlier call to {@link #setJsonData}.
	 * <p>
	 * This data will be used instead of loading the player's advancement file. It is null unless
	 * supplied by a plugin.
	 *
	 * @return JSON data of the player's advancements as set by {@link #setJsonData}
	 */
	@Nullable
	public String getJsonData() {
		return mJsonData;
	}

	/**
	 * Set the JSON data to use for the player's advancements instead of loading it from a file.
	 * <p>
	 * This data will be used instead of loading the player's advancement file. It is null unless
	 * supplied by a plugin.
	 *
	 * @param jsonData advancement data JSON string to load. If null, load from file
	 */
	public void setJsonData(@Nullable String jsonData) {
		this.mJsonData = jsonData;
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
