package vn.nirussv.maceexclusive.forge;

import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.SmithItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.util.Scheduler;

public final class NetheriteForgeListener implements Listener {

    private final NetheriteForgeService netheriteForgeService;
    private final ConfigManager configManager;

    public NetheriteForgeListener(NetheriteForgeService netheriteForgeService, ConfigManager configManager) {
        this.netheriteForgeService = netheriteForgeService;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmithNetherite(SmithItemEvent event) {
        if (!configManager.isTimedForgeEnabled()) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        SmithingInventory inventory = event.getInventory();
        ItemStack result = inventory.getResult();
        if (result == null || result.getType().isAir()) return;
        ConfigManager.TimedForgeItemSettings settings = configManager.getTimedForgeSettings(result.getType());
        if (!settings.enabled()) return;
        Block workstation = netheriteForgeService.resolveSmithingTable(inventory);
        if (workstation == null) return;
        ItemStack expectedResult = result.clone();
        Scheduler.runEntityTaskLater(netheriteForgeService.plugin(), player, () ->
            netheriteForgeService.tryStartAfterCraft(player, workstation, expectedResult, settings), 1L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onResume(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block block = event.getClickedBlock();
        if (!netheriteForgeService.isForgeBlock(block)) return;
        event.setCancelled(true);
        netheriteForgeService.resume(event.getPlayer(), block);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakForgeBlock(BlockBreakEvent event) {
        if (!netheriteForgeService.isForgeBlock(event.getBlock())) return;
        netheriteForgeService.abort(event.getBlock(), true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupVisualItem(EntityPickupItemEvent event) {
        Item item = event.getItem();
        if (!netheriteForgeService.isVisualItem(item)) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInventoryPickupVisualItem(InventoryPickupItemEvent event) {
        Item item = event.getItem();
        if (!netheriteForgeService.isVisualItem(item)) return;
        event.setCancelled(true);
    }
}
