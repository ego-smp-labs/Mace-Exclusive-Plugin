package vn.nirussv.maceexclusive.forge;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.mace.MaceFactory;

import java.util.ArrayList;
import java.util.List;

public final class ForgeListener implements Listener {

    private final MaceExclusivePlugin plugin;
    private final ForgeService forgeService;
    private final MaceFactory maceFactory;

    public ForgeListener(MaceExclusivePlugin plugin, ForgeService forgeService, MaceFactory maceFactory) {
        this.plugin = plugin;
        this.forgeService = forgeService;
        this.maceFactory = maceFactory;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractForge(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        Block block = event.getClickedBlock();
        ItemStack item = event.getItem();
        if (block == null || item == null || maceFactory.getAwakeningResult(item).isEmpty()) {
            return;
        }

        if (forgeService.tryStart(player, block, item)) {
            consumeOne(item);
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDropUnawakened(PlayerDropItemEvent event) {
        Item dropped = event.getItemDrop();
        if (maceFactory.getAwakeningResult(dropped.getItemStack()).isEmpty()) {
            return;
        }

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (dropped.isDead()) {
                return;
            }

            Block forgeBlock = findForgeBlockBelow(dropped);
            if (forgeBlock == null) {
                return;
            }

            if (forgeService.tryStart(event.getPlayer(), forgeBlock, dropped.getItemStack())) {
                dropped.remove();
            }
        }, 20L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakForgeBlock(BlockBreakEvent event) {
        if (!forgeService.isForgeBlock(event.getBlock())) {
            return;
        }

        event.setCancelled(true);
        event.getPlayer().sendMessage("This weapon is still awakening.");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        abortExplodedForgeBlocks(event.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        abortExplodedForgeBlocks(event.blockList());
    }

    private void abortExplodedForgeBlocks(List<Block> blocks) {
        List<Block> forgeBlocks = new ArrayList<>();
        for (Block block : blocks) {
            if (forgeService.isForgeBlock(block)) {
                forgeBlocks.add(block);
            }
        }

        for (Block forgeBlock : forgeBlocks) {
            blocks.remove(forgeBlock);
            forgeService.abort(forgeBlock, true, true);
        }
    }

    private Block findForgeBlockBelow(Item dropped) {
        Block current = dropped.getLocation().getBlock();
        if (forgeService.isValidForgeBase(current)) {
            return current;
        }

        Block below = current.getRelative(0, -1, 0);
        if (forgeService.isValidForgeBase(below)) {
            return below;
        }

        Block twoBelow = current.getRelative(0, -2, 0);
        return forgeService.isValidForgeBase(twoBelow) ? twoBelow : null;
    }

    private void consumeOne(ItemStack item) {
        if (item.getAmount() <= 1) {
            item.setType(Material.AIR);
            item.setAmount(0);
            return;
        }
        item.setAmount(item.getAmount() - 1);
    }
}
