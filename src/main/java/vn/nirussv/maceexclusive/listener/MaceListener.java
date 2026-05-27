package vn.nirussv.maceexclusive.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.mace.MaceManager;

public class MaceListener implements Listener {

    private final MaceManager maceManager;

    public MaceListener(MaceManager maceManager) {
        this.maceManager = maceManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent event) {
        ItemStack item = event.getItem().getItemStack();
        String id = maceManager.getExclusiveItemKey(item).orElse(null);
        if (id == null) return;
        Player player = event.getPlayer();
        if (maceManager.claimIfAllowed(item, player)) maceManager.onPlayerBecameHolder(player, player.getLocation(), id);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        String cursorId = maceManager.getExclusiveItemKey(event.getCursor()).orElse(null);
        if (cursorId != null && maceManager.claimIfAllowed(event.getCursor(), player)) maceManager.onPlayerBecameHolder(player, player.getLocation(), cursorId);
        String currentId = maceManager.getExclusiveItemKey(event.getCurrentItem()).orElse(null);
        if (currentId != null && maceManager.claimIfAllowed(event.getCurrentItem(), player)) maceManager.onPlayerBecameHolder(player, player.getLocation(), currentId);
    }
}
