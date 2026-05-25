package vn.nirussv.maceexclusive.listener;

import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vn.nirussv.maceexclusive.projectile.SpearProjectileService;

public final class SpearListener implements Listener {

    private final SpearProjectileService spearProjectileService;

    public SpearListener(SpearProjectileService spearProjectileService) {
        this.spearProjectileService = spearProjectileService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        spearProjectileService.handleLaunch(event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onProjectileHit(ProjectileHitEvent event) {
        spearProjectileService.handleHit(event);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        spearProjectileService.recoverOutstanding(event.getPlayer());
    }
}
