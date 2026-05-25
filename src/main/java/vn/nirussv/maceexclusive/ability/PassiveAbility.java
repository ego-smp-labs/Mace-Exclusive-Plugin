package vn.nirussv.maceexclusive.ability;

import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;

public interface PassiveAbility {

    String id();

    ExclusiveItemId weaponId();

    default void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
    }

    default void onDamaged(AbilityContext context, EntityDamageByEntityEvent event) {
    }

    default void onDeath(AbilityContext context, EntityDeathEvent event) {
    }
}
