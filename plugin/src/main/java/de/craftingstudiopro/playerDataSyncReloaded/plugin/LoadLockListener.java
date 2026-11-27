package de.craftingstudiopro.playerDataSyncReloaded.plugin;

import de.craftingstudiopro.playerDataSyncReloaded.PlayerDataSyncReloaded;
import de.craftingstudiopro.playerDataSyncReloaded.common.SyncManager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

/**
 * Until the stored record is applied, the player still has this server's local data from their last
 * visit. Dropping, using or losing those items before the load lands duplicates or loses them, so the
 * actions that move items are cancelled until then ({@code sync.lock_while_loading}). If the load fails
 * for good the lock is lifted; saves stay refused, so the stored record is still protected.
 */
public final class LoadLockListener implements Listener {
    private final PlayerDataSyncReloaded plugin;

    public LoadLockListener(PlayerDataSyncReloaded plugin) {
        this.plugin = plugin;
    }

    private boolean locked(Entity entity) {
        if (!(entity instanceof Player)) {
            return false;
        }
        SyncManager sync = plugin.getSyncManager();
        if (sync == null || !plugin.getConfig().getBoolean("sync.lock_while_loading", true)) {
            return false;
        }
        return sync.isWaitingForData(entity.getUniqueId());
    }

    private void cancelIfLocked(Entity entity, Cancellable event) {
        if (locked(entity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        cancelIfLocked(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        cancelIfLocked(event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        cancelIfLocked(event.getWhoClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        cancelIfLocked(event.getWhoClicked(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        cancelIfLocked(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        if (locked(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        cancelIfLocked(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        cancelIfLocked(event.getPlayer(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        cancelIfLocked(event.getPlayer(), event);
    }

    /** A death before the load would drop the stale inventory on the ground and then restore it. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        cancelIfLocked(event.getEntity(), event);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onHunger(FoodLevelChangeEvent event) {
        cancelIfLocked(event.getEntity(), event);
    }
}
