package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;

public final class AbilityContext {

    private final Player player;
    private final Location location;
    private final ItemStack weapon;
    private final ExclusiveItemId weaponId;
    private final LivingEntity target;
    private final Entity source;

    public AbilityContext(Player player, Location location, ItemStack weapon, ExclusiveItemId weaponId,
                          LivingEntity target, Entity source) {
        this.player = player;
        this.location = location;
        this.weapon = weapon;
        this.weaponId = weaponId;
        this.target = target;
        this.source = source;
    }

    public Player player() {
        return player;
    }

    public Location location() {
        return location;
    }

    public ItemStack weapon() {
        return weapon;
    }

    public ExclusiveItemId weaponId() {
        return weaponId;
    }

    public LivingEntity target() {
        return target;
    }

    public Entity source() {
        return source;
    }
}
