package com.playmonumenta.redissync;

import com.playmonumenta.redissync.utils.MMLog;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityCombustByEntityEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryInteractEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerArmorStandManipulateEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.projectiles.ProjectileSource;

/**
 * Freezes a player whose session is locked: nothing they do, or that is done to them, would be in
 * the save their data is moving with. Cancels the common interactions; it cannot cover everything
 * other plugins might do.
 */
public class LockedPlayerListener implements Listener {
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerInteractEvent(PlayerInteractEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void blockPlaceEvent(BlockPlaceEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerInteractEntityEvent(PlayerInteractEntityEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerArmorStandManipulateEvent(PlayerArmorStandManipulateEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerDropItemEvent(PlayerDropItemEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerSwapHandItemsEvent(PlayerSwapHandItemsEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerFishEvent(PlayerFishEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerItemConsumeEvent(PlayerItemConsumeEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerItemDamageEvent(PlayerItemDamageEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerBedEnterEvent(PlayerBedEnterEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void playerGameModeChangeEvent(PlayerGameModeChangeEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void blockBreakEvent(BlockBreakEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entityPickupItemEvent(EntityPickupItemEvent event) {
		cancelEventIfLocked(event.getEntity(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void inventoryClickEvent(InventoryClickEvent event) {
		cancelEventIfLocked(event.getWhoClicked(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void inventoryDragEvent(InventoryDragEvent event) {
		cancelEventIfLocked(event.getWhoClicked(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void inventoryOpenEvent(InventoryOpenEvent event) {
		cancelEventIfLocked(event.getPlayer(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void inventoryInteractEvent(InventoryInteractEvent event) {
		cancelEventIfLocked(event.getWhoClicked(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entityCombustByEntityEvent(EntityCombustByEntityEvent event) {
		cancelEventIfLocked(event.getEntity(), event);
		cancelEventIfLocked(event.getCombuster(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entityDamageByEntityEvent(EntityDamageByEntityEvent event) {
		cancelEventIfLocked(event.getEntity(), event);
		cancelEventIfLocked(event.getDamager(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entityDamageEvent(EntityDamageEvent event) {
		cancelEventIfLocked(event.getEntity(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void hangingBreakByEntityEvent(HangingBreakByEntityEvent event) {
		cancelEventIfLocked(event.getRemover(), event);
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void projectileLaunchEvent(ProjectileLaunchEvent event) {
		ProjectileSource shooter = event.getEntity().getShooter();
		if (shooter instanceof Player) {
			cancelEventIfLocked((Player) shooter, event);
		}
	}

	/* Prevent shoulder entities of locked players (i.e. parrots) from being duplicated into the world via timing exploit */
	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void entitySpawnEvent(EntitySpawnEvent event) {
		if (PlayerSessions.isLockedShoulderEntity(event.getEntity().getUniqueId())) {
			MMLog.debug(() -> "Refused to spawn shoulder entity id: " + event.getEntity().getType() + " uuid: " + event.getEntity().getUniqueId());
			event.setCancelled(true);
		}
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void potionSplashEvent(PotionSplashEvent event) {
		event.getAffectedEntities().removeIf(entity -> (entity instanceof Player && PlayerSessions.isLocked((Player) entity)));
	}

	@EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
	public void areaEffectCloudApplyEvent(AreaEffectCloudApplyEvent event) {
		event.getAffectedEntities().removeIf(entity -> (entity instanceof Player && PlayerSessions.isLocked((Player) entity)));
	}

	private void cancelEventIfLocked(Entity entity, Cancellable event) {
		if (entity instanceof Player player && PlayerSessions.isLocked(player)) {
			event.setCancelled(true);
		}
	}
}
