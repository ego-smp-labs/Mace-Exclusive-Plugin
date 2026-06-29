package vn.nirussv.maceexclusive.ritual;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.forge.ForgeService;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;

public final class RitualAltarListener implements Listener {

    private final RitualAltarService altarService;
    private final MaceExclusivePlugin plugin;
    private final ForgeService forgeService;
    private final ItemMatcher itemMatcher;
    private final ConfigManager configManager;

    public RitualAltarListener(RitualAltarService altarService, MaceExclusivePlugin plugin, ForgeService forgeService, ItemMatcher itemMatcher, ConfigManager configManager) {
        this.altarService = altarService;
        this.plugin = plugin;
        this.forgeService = forgeService;
        this.itemMatcher = itemMatcher;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (block == null || block.getType() != Material.CRAFTING_TABLE) return;
        if (forgeService.isForgeBlock(block)) return;
        Player player = event.getPlayer();
        if (tryTransform(event, player, block)) return;
        if (!altarService.isAltar(block)) return;
        event.setCancelled(true);
        altarService.openAltar(player, block);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (!altarService.isAltar(event.getBlock())) return;
        event.setDropItems(false);
        event.setCancelled(true);
        event.getBlock().setType(Material.AIR, false);
        altarService.breakAltar(event.getBlock());
        event.getPlayer().sendMessage(configManager.getMessage("ritual-altar.broken"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof RitualAltarMenu menu)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (menu.isLocked()) {
            event.setCancelled(true);
            return;
        }
        int slot = event.getRawSlot();
        if (slot == RitualAltarMenu.CRAFT_BUTTON_SLOT) { craft(event, player, menu); return; }
        if (isUnsafeClick(event) || menu.isProtectedSlot(slot)) event.setCancelled(true);
        if (slot == 4 && !isCorePlacement(event)) event.setCancelled(true);
        if (menu.isMatrixSlot(slot) && slot != 4 && isCoreCursor(event)) event.setCancelled(true);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> altarService.updateMenu(menu), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getInventory().getHolder() instanceof RitualAltarMenu menu)) return;
        for (int slot : event.getRawSlots()) {
            boolean draggingCore = itemMatcher.matchCore(event.getOldCursor()).isPresent();
            if (menu.isProtectedSlot(slot) || (slot == 4 && !draggingCore) || (menu.isMatrixSlot(slot) && slot != 4 && draggingCore)) {
                event.setCancelled(true);
                return;
            }
        }
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> altarService.updateMenu(menu), 1L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof RitualAltarMenu)) return;
        if (event.getPlayer() instanceof Player player) altarService.returnMenuItems(player, event.getInventory());
    }

    private boolean tryTransform(PlayerInteractEvent event, Player player, Block block) {
        if (altarService.isAltar(block) || !player.isSneaking() || !holdsRitualCore(player)) return false;
        event.setCancelled(true);
        altarService.transformToAltar(player, block);
        return true;
    }

    private void craft(InventoryClickEvent event, Player player, RitualAltarMenu menu) {
        event.setCancelled(true);
        Block altarBlock = altarService.blockFrom(menu.altarLocation()).orElse(null);
        if (altarBlock == null || !altarService.attemptCraft(player, altarBlock, menu)) return;
        altarService.updateMenu(menu);
    }

    private boolean holdsRitualCore(Player player) {
        return itemMatcher.isCore(player.getInventory().getItemInMainHand(), "ritual_core")
            || itemMatcher.isCore(player.getInventory().getItemInOffHand(), "ritual_core");
    }

    private boolean isUnsafeClick(InventoryClickEvent event) {
        ClickType click = event.getClick();
        return event.isShiftClick() || click == ClickType.NUMBER_KEY || click == ClickType.DOUBLE_CLICK || click == ClickType.SWAP_OFFHAND;
    }

    private boolean isCorePlacement(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (itemMatcher.matchCore(cursor).isPresent()) return true;
        ItemStack current = event.getCurrentItem();
        return current == null || current.getType().isAir() || itemMatcher.matchCore(current).isPresent();
    }

    private boolean isCoreCursor(InventoryClickEvent event) {
        return itemMatcher.matchCore(event.getCursor()).isPresent();
    }
}
