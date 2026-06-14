package vn.nirussv.maceexclusive.forge;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;
import vn.nirussv.maceexclusive.mace.MaceManager;

import java.util.ArrayList;
import java.util.List;

public final class ForgeListener implements Listener {

    private final ForgeService forgeService;
    private final MaceManager maceManager;
    private final ConfigManager configManager;

    public ForgeListener(ForgeService forgeService, MaceManager maceManager, ConfigManager configManager) {
        this.forgeService = forgeService;
        this.maceManager = maceManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftWeapon(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        String itemId = maceManager.getExclusiveItemKey(result).orElse(null);
        if (itemId == null) return;
        ConfigManager.CraftFeedback feedback = configManager.getItemCraftFeedback(itemId);
        if (!feedback.specialCraftingEnabled()) return;
        event.setCancelled(true);
        if (configManager.isCraftingShiftClickPrevented() && isUnsafeBulkCraft(event)) {
            player.sendMessage(configManager.getMessage("forge.take-one-at-a-time"));
            return;
        }
        if (hasExclusiveWeapon(player)) {
            player.sendMessage(configManager.getPrefixedMessage("mace.cannot-carry-multiple"));
            return;
        }
        if (forgeService.tryStartFromCraft(player, event.getInventory(), itemId)) {
            ItemConfig itemConfig = configManager.getItemConfig(itemId);
            configManager.sendCraftStartMessage(player, itemId, itemConfig == null ? itemId : itemConfig.name(), feedback);
            player.sendMessage(configManager.getMessage("forge.ritual-started"));
            double damage = (5 + java.util.concurrent.ThreadLocalRandom.current().nextInt(5)) * 2.0D;
            player.damage(damage);
            return;
        }
        player.sendMessage(configManager.getMessage("forge.unavailable", java.util.Map.of("reason", forgeService.unavailableReason(itemId))));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakForgeBlock(BlockBreakEvent event) {
        if (!forgeService.isForgeBlock(event.getBlock())) return;
        forgeService.abort(event.getBlock(), true);
        event.getPlayer().sendMessage(configManager.getMessage("forge.lodestone-broken"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        abortExplodedForgeBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        abortExplodedForgeBlocks(event.blockList());
    }

    private boolean isUnsafeBulkCraft(CraftItemEvent event) {
        ClickType click = event.getClick();
        return event.isShiftClick() || click == ClickType.NUMBER_KEY || click == ClickType.DOUBLE_CLICK;
    }

    private void abortExplodedForgeBlocks(List<Block> blocks) {
        List<Block> forgeBlocks = new ArrayList<>();
        for (Block block : blocks) {
            if (forgeService.isForgeBlock(block)) forgeBlocks.add(block);
        }
        for (Block forgeBlock : forgeBlocks) forgeService.abort(forgeBlock, true);
    }

    private boolean hasExclusiveWeapon(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null) {
                java.util.Optional<String> idOpt = maceManager.getExclusiveItemKey(item);
                if (idOpt.isPresent()) {
                    String id = idOpt.get();
                    if (id.endsWith("_mace") || id.endsWith("_spear") || id.equals("cursed_sword")) {
                        return true;
                    }
                }
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null) {
            java.util.Optional<String> idOpt = maceManager.getExclusiveItemKey(offHand);
            if (idOpt.isPresent()) {
                String id = idOpt.get();
                if (id.endsWith("_mace") || id.endsWith("_spear") || id.equals("cursed_sword")) {
                    return true;
                }
            }
        }
        return false;
    }
}
