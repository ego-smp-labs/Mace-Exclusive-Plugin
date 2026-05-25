package vn.nirussv.maceexclusive.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import vn.nirussv.maceexclusive.ability.AbilityService;

public final class AbilityListener implements Listener {

    private final AbilityService abilityService;

    public AbilityListener(AbilityService abilityService) {
        this.abilityService = abilityService;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        abilityService.handleInteract(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        abilityService.handleAttack(event);
        abilityService.handleDamaged(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent event) {
        abilityService.handleDeath(event);
    }
}
