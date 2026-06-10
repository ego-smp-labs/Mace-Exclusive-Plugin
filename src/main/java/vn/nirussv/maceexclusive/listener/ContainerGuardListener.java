package vn.nirussv.maceexclusive.listener;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.mace.MaceManager;
import org.bukkit.event.player.PlayerDropItemEvent;

public final class ContainerGuardListener implements Listener {

    private final MaceManager maceManager;
    private final ConfigManager configManager;

    public ContainerGuardListener(MaceManager maceManager, ConfigManager configManager) {
        this.maceManager = maceManager;
        this.configManager = configManager;
    }



    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        if (shouldGuard() && maceManager.isExclusiveItem(event.getResult())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryMoveItem(InventoryMoveItemEvent event) {
        if (shouldGuard() && maceManager.isExclusiveItem(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickupItem(InventoryPickupItemEvent event) {
        if (shouldGuard() && maceManager.isExclusiveItem(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDispense(BlockDispenseEvent event) {
        if (shouldGuard() && maceManager.isExclusiveItem(event.getItem())) {
            Material blockType = event.getBlock().getType();
            if (blockType == Material.DISPENSER || blockType == Material.DROPPER) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!shouldGuard() || !(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();
        ItemStack hotbar = event.getClick().isKeyboardClick() && event.getHotbarButton() >= 0
            ? player.getInventory().getItem(event.getHotbarButton())
            : null;

        if (!containsExclusive(current, cursor, hotbar)) {
            return;
        }
        if (maceManager.isOwnedByAnother(current, player)
            || maceManager.isOwnedByAnother(cursor, player)
            || maceManager.isOwnedByAnother(hotbar, player)) {
            event.setCancelled(true);
            return;
        }

        Inventory topInventory = event.getView().getTopInventory();
        if (isAllowedTopInventory(topInventory.getType())) {
            return;
        }

        if (event.isShiftClick()
            || event.getClickedInventory() == topInventory
            || maceManager.isExclusiveItem(cursor)
            || maceManager.isExclusiveItem(hotbar)) {
            
            event.setCancelled(true);

            if (configManager.isStrictModeDrop()) {
                ItemStack exclusiveItem = null;
                if (maceManager.isExclusiveItem(cursor)) {
                    exclusiveItem = cursor.clone();
                    event.setCursor(null);
                } else if (event.isShiftClick() && maceManager.isExclusiveItem(current)) {
                    exclusiveItem = current.clone();
                    event.setCurrentItem(null);
                } else if (maceManager.isExclusiveItem(current) && event.getClickedInventory() == topInventory) {
                    exclusiveItem = current.clone();
                    event.setCurrentItem(null);
                } else if (maceManager.isExclusiveItem(hotbar)) {
                    exclusiveItem = hotbar.clone();
                    player.getInventory().setItem(event.getHotbarButton(), null);
                }

                if (exclusiveItem != null) {
                    player.getWorld().dropItemNaturally(player.getLocation(), exclusiveItem);
                    player.getWorld().createExplosion(player.getLocation(), 2.0F, false, true);
                    player.sendMessage(configManager.getPrefixedMessage("mace.strict-mode-drop"));
                }
            } else {
                player.sendMessage(configManager.getPrefixedMessage("mace.cannot-move"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        if (maceManager.isExclusiveItem(item)) {
            Player player = event.getPlayer();
            if (!configManager.isDropAllowed()) {
                event.setCancelled(true);
                player.sendMessage(configManager.getPrefixedMessage("mace.cannot-drop"));
                return;
            }
            if (configManager.isStrictMode()) {
                org.bukkit.entity.Item itemDrop = event.getItemDrop();
                itemDrop.getWorld().createExplosion(itemDrop.getLocation(), 2.0F, false, true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!shouldGuard() || !maceManager.isExclusiveItem(event.getOldCursor())) {
            return;
        }
        if (isAllowedTopInventory(event.getView().getTopInventory().getType())) {
            return;
        }
        int topSize = event.getView().getTopInventory().getSize();
        for (int slot : event.getRawSlots()) {
            if (slot < topSize) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(configManager.getPrefixedMessage("mace.cannot-move"));
                return;
            }
        }
    }

    private boolean shouldGuard() {
        return configManager.isStrictContainerBlock();
    }

    private boolean containsExclusive(ItemStack... items) {
        for (ItemStack item : items) {
            if (maceManager.isExclusiveItem(item)) {
                return true;
            }
        }
        return false;
    }

    private boolean isAllowedTopInventory(InventoryType type) {
        return type == InventoryType.CRAFTING
            || type == InventoryType.WORKBENCH
            || type == InventoryType.ANVIL
            || type == InventoryType.ENCHANTING
            || type == InventoryType.PLAYER;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.Item item) {
            java.util.Optional<String> id = maceManager.getExclusiveItemKey(item.getItemStack());
            if (id.isPresent()) {
                if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                    return; // Let the item be destroyed by the void. Admins will reset it manually if needed.
                }
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onItemDespawn(ItemDespawnEvent event) {
        if (maceManager.isExclusiveItem(event.getEntity().getItemStack())) {
            event.setCancelled(true);
        }
    }
}
