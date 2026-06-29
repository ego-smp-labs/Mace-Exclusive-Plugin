package vn.nirussv.maceexclusive.effect;

import com.destroystokyo.paper.event.player.PlayerJumpEvent;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class FreezeService implements Listener {

    private final MaceExclusivePlugin plugin;
    private final Map<UUID, FreezeState> frozenEntities = new HashMap<>();
    private BukkitTask lockTask;

    public FreezeService(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
    }

    public void freeze(LivingEntity entity, int durationTicks) {
        if (entity == null || entity.isDead() || durationTicks <= 0) {
            return;
        }

        long expiresAt = System.currentTimeMillis() + (durationTicks * 50L);
        FreezeState current = frozenEntities.get(entity.getUniqueId());
        Location anchor = current == null ? entity.getLocation().clone() : current.anchor();
        frozenEntities.put(entity.getUniqueId(), new FreezeState(entity.getUniqueId(), anchor, Math.max(expiresAt, current == null ? 0L : current.expiresAtMillis())));

        entity.setVelocity(new Vector(0.0, 0.0, 0.0));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationTicks + 10, 10, false, false, true));
        entity.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST, durationTicks + 10, 200, false, false, false));
        SafeParticleSpawner.spawn(entity.getWorld(), Particle.END_ROD, entity.getLocation().add(0.0, 1.0, 0.0), 18, 0.35, 0.5, 0.35, 0.02);
        entity.getWorld().playSound(entity.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 0.8f);
        ensureLockTask();
    }

    public boolean isFrozen(Entity entity) {
        return entity != null && frozenEntities.containsKey(entity.getUniqueId());
    }

    public void shutdown() {
        if (lockTask != null) {
            lockTask.cancel();
            lockTask = null;
        }
        frozenEntities.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        FreezeState state = frozenEntities.get(event.getPlayer().getUniqueId());
        if (state == null || expired(state)) {
            frozenEntities.remove(event.getPlayer().getUniqueId());
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ())) {
            return;
        }

        Location locked = from.clone();
        locked.setYaw(to.getYaw());
        locked.setPitch(to.getPitch());
        event.setTo(locked);
        event.getPlayer().setVelocity(new Vector(0.0, 0.0, 0.0));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onJump(PlayerJumpEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
            event.getPlayer().setVelocity(new Vector(0.0, 0.0, 0.0));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSneak(PlayerToggleSneakEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        if (isFrozen(event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        frozenEntities.remove(event.getPlayer().getUniqueId());
    }

    private void ensureLockTask() {
        if (lockTask != null) {
            return;
        }
        lockTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (frozenEntities.isEmpty()) {
                lockTask.cancel();
                lockTask = null;
                return;
            }

            Iterator<Map.Entry<UUID, FreezeState>> iterator = frozenEntities.entrySet().iterator();
            while (iterator.hasNext()) {
                FreezeState state = iterator.next().getValue();
                if (expired(state)) {
                    iterator.remove();
                    continue;
                }

                Entity entity = Bukkit.getEntity(state.entityId());
                if (!(entity instanceof LivingEntity living) || living.isDead() || !living.isValid()) {
                    iterator.remove();
                    continue;
                }

                living.setVelocity(new Vector(0.0, 0.0, 0.0));
                if (living instanceof Player player) {
                    Location current = player.getLocation();
                    if (current.getWorld().equals(state.anchor().getWorld()) && current.distanceSquared(state.anchor()) > 0.09) {
                        Location locked = state.anchor().clone();
                        locked.setYaw(current.getYaw());
                        locked.setPitch(current.getPitch());
                        player.teleport(locked);
                    }
                }
            }
        }, 1L, 1L);
    }

    private boolean expired(FreezeState state) {
        return System.currentTimeMillis() >= state.expiresAtMillis();
    }

    private record FreezeState(UUID entityId, Location anchor, long expiresAtMillis) {
    }
}
