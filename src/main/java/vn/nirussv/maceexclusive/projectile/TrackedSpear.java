package vn.nirussv.maceexclusive.projectile;

import org.bukkit.inventory.ItemStack;

import java.util.UUID;

public final class TrackedSpear {

    private final UUID projectileId;
    private final UUID shooterId;
    private final ItemStack spearItem;
    private final long launchTimeMillis;
    private boolean completed;

    public TrackedSpear(UUID projectileId, UUID shooterId, ItemStack spearItem) {
        this.projectileId = projectileId;
        this.shooterId = shooterId;
        this.spearItem = spearItem.clone();
        this.launchTimeMillis = System.currentTimeMillis();
    }

    public UUID projectileId() {
        return projectileId;
    }

    public UUID shooterId() {
        return shooterId;
    }

    public ItemStack spearItem() {
        return spearItem.clone();
    }

    public long launchTimeMillis() {
        return launchTimeMillis;
    }

    public boolean completed() {
        return completed;
    }

    public void markCompleted() {
        this.completed = true;
    }
}
