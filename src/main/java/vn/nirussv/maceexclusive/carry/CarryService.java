package vn.nirussv.maceexclusive.carry;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.mace.MaceManager;

import java.util.ArrayList;
import java.util.List;

/**
 * Inventory-facing facade over {@link WeaponCarryPolicy}. Resolves which exclusive weapons a
 * player currently holds (main inventory + offhand) and answers carry questions for listeners.
 */
public final class CarryService {

    private final MaceManager maceManager;
    private final WeaponCarryPolicy policy;

    public CarryService(MaceManager maceManager, WeaponCarryPolicy policy) {
        this.maceManager = maceManager;
        this.policy = policy;
    }

    public WeaponCarryPolicy policy() {
        return policy;
    }

    public boolean isExclusiveWeapon(ItemStack item) {
        return exclusiveId(item) != null;
    }

    public String exclusiveId(ItemStack item) {
        if (item == null) return null;
        String id = maceManager.getExclusiveItemKey(item).orElse(null);
        return policy.isExclusiveWeapon(id) ? id : null;
    }

    /** Collects every exclusive weapon id currently held (contents include hotbar; offhand added explicitly). */
    public List<String> heldExclusiveIds(Player player) {
        return heldExclusiveIds(player, true);
    }

    public List<String> heldExclusiveIds(Player player, boolean includeOffHand) {
        List<String> ids = new ArrayList<>();
        for (ItemStack item : player.getInventory().getContents()) {
            String id = exclusiveId(item);
            if (id != null) ids.add(id);
        }
        if (includeOffHand) {
            String offId = exclusiveId(player.getInventory().getItemInOffHand());
            if (offId != null) ids.add(offId);
        }
        return ids;
    }

    /**
     * Returns true when adding {@code incoming} (an exclusive weapon) to the player's current
     * holdings would still be a legal carry set. Excludes a single matching instance is not needed
     * here because callers pass the held set BEFORE the incoming item is added.
     */
    public boolean canAdd(Player player, ItemStack incoming, boolean includeOffHand) {
        String incomingId = exclusiveId(incoming);
        if (incomingId == null) return true;
        return policy.canCarryAdditional(heldExclusiveIds(player, includeOffHand), incomingId);
    }
}
