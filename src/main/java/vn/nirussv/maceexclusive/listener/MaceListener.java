package vn.nirussv.maceexclusive.listener;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.mace.MaceManager.AcquisitionReason;
import vn.nirussv.maceexclusive.mace.MaceManager.ClaimResult;

public class MaceListener implements Listener {

    private final MaceManager maceManager;

    public MaceListener(MaceManager maceManager) {
        this.maceManager = maceManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        String id = maceManager.getExclusiveItemKey(item).orElse(null);
        if (id == null) return;
        ClaimResult result = maceManager.claimIfAllowed(item, player);
        if (result == ClaimResult.NEWLY_CLAIMED) {
            maceManager.notifyAcquisition(player, player.getLocation(), id, AcquisitionReason.PICKUP);
        }
    }
}
