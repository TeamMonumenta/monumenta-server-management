package com.playmonumenta.redissync.event;

import com.playmonumenta.redissync.AccountTransferDetails;
import java.time.LocalDateTime;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class PlayerAccountTransferEvent extends Event {

	private static final HandlerList handlers = new HandlerList();

	private final LocalDateTime mTransferTime;
	private final UUID mOldId;
	private final String mOldName;
	private final UUID mCurrentId;
	private final String mCurrentName;
	private final boolean mIsLocal;

	public PlayerAccountTransferEvent(LocalDateTime transferTime, @NotNull Player player, UUID oldId, String oldName) {
		mTransferTime = transferTime;
		mCurrentId = player.getUniqueId();
		mCurrentName = player.getName();
		mOldId = oldId;
		mOldName = oldName;
		mIsLocal = true;
	}

	public PlayerAccountTransferEvent(AccountTransferDetails accountTransferDetails) {
		mTransferTime = accountTransferDetails.transferTime();
		mCurrentId = accountTransferDetails.newId();
		mCurrentName = accountTransferDetails.newName();
		mOldId = accountTransferDetails.oldId();
		mOldName = accountTransferDetails.oldName();
		mIsLocal = false;
	}

	/**
	 * Gets the time of the player's transfer in UTC.
	 * This can be slightly in the past if transmitted over the network,
	 * or if an event handler takes too long on the main thread.
	 * @return The time of the transfer
	 */
	public LocalDateTime getTransferTime() {
		return mTransferTime;
	}

	/**
	 * Gets the player's previous UUID before they transferred their account
	 * @return The player's previous UUID
	 */
	public UUID getOldId() {
		return mOldId;
	}

	/**
	 * Gets the player's previous name before they transferred their account
	 * @return The player's previous name
	 */
	public String getOldName() {
		return mOldName;
	}

	/**
	 * Gets the player's current UUID since they transferred their account
	 * @return The player's current UUID
	 */
	public UUID getCurrentId() {
		return mCurrentId;
	}

	/**
	 * Gets the player's current name since they transferred their account
	 * @return The player's current name
	 */
	public String getCurrentName() {
		return mCurrentName;
	}

	/**
	 * Gets the player who transferred accounts if they are on this shard
	 * @return The player who transferred accounts, or null if they are on a different shard
	 */
	public @Nullable Player getPlayer() {
		return Bukkit.getPlayer(mCurrentId);
	}

	/**
	 * Checks if the account transfer was from this shard
	 * @return true if the player transfer was discovered from this shard
	 */
	public boolean isLocal() {
		return mIsLocal;
	}

	@Override
	public @NotNull HandlerList getHandlers() {
		return handlers;
	}

	public static HandlerList getHandlerList() {
		return handlers;
	}
}
