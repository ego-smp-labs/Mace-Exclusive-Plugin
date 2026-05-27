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
import vn.nirussv.maceexclusive.mace.MaceManager;

import java.util.ArrayList;
import java.util.List;

public final class ForgeListener implements Listener {

    private final ForgeService forgeService;
    private final MaceManager maceManager;

    public ForgeListener(ForgeService forgeService, MaceManager maceManager) {
        this.forgeService = forgeService;
        this.maceManager = maceManager;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftWeapon(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        String itemId = maceManager.getExclusiveItemKey(result).orElse(null);
        if (itemId == null) return;
        event.setCancelled(true);
        if (isUnsafeBulkCraft(event)) {
            player.sendMessage("§cHãy lấy vũ khí từng cái một.");
            return;
        }
        if (forgeService.tryStartFromCraft(player, event.getInventory(), itemId)) {
            player.sendMessage("§aBàn chế tạo đã biến thành Lodestone. Quá trình đúc bắt đầu.");
            return;
        }
        player.sendMessage("§c" + forgeService.unavailableReason(itemId));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakForgeBlock(BlockBreakEvent event) {
        if (!forgeService.isForgeBlock(event.getBlock())) return;
        forgeService.abort(event.getBlock(), true);
        event.getPlayer().sendMessage("§cPhiên đúc đã bị hủy vì Lodestone bị phá.");
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
}
