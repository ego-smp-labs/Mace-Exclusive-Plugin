package vn.nirussv.maceexclusive.projectile;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Trident;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.projectiles.ProjectileSource;
import org.bukkit.scheduler.BukkitTask;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.FreezeService;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class SpearProjectileService {

    private static final String PROJECTILE_METADATA = "mace_exclusive_chronos_spear";
    private static final int TARGET_FREEZE_TICKS = 45;
    private static final int BACKFIRE_FREEZE_TICKS = 25;
    private static final int MISS_TIMEOUT_TICKS = 100;

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;
    private final FreezeService freezeService;
    private final NamespacedKey markerKey;
    private final NamespacedKey shooterKey;
    private final Map<UUID, TrackedSpear> trackedSpears = new HashMap<>();
    private final Map<UUID, BukkitTask> trailTasks = new HashMap<>();

    public SpearProjectileService(
        MaceExclusivePlugin plugin,
        ConfigManager configManager,
        ItemMatcher itemMatcher,
        FreezeService freezeService
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemMatcher = itemMatcher;
        this.freezeService = freezeService;
        this.markerKey = new NamespacedKey(plugin, "chronos_anchor_spear_projectile");
        this.shooterKey = new NamespacedKey(plugin, "chronos_anchor_spear_shooter");
    }

    public void handleLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) {
            return;
        }

        ProjectileSource source = trident.getShooter();
        if (!(source instanceof Player shooter)) {
            return;
        }

        if (!configManager.isItemEnabled(ExclusiveItemId.CHRONOS_ANCHOR_SPEAR)) {
            return;
        }

        Optional<ItemStack> spearItem = findChronosSpearItem(trident, shooter);
        if (spearItem.isEmpty()) {
            return;
        }

        ItemStack storedItem = spearItem.get().clone();
        storedItem.setAmount(1);

        trident.getPersistentDataContainer().set(markerKey, PersistentDataType.BYTE, (byte) 1);
        trident.getPersistentDataContainer().set(shooterKey, PersistentDataType.STRING, shooter.getUniqueId().toString());
        trident.setMetadata(PROJECTILE_METADATA, new FixedMetadataValue(plugin, shooter.getUniqueId().toString()));
        trident.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);

        TrackedSpear tracked = new TrackedSpear(trident.getUniqueId(), shooter.getUniqueId(), storedItem);
        trackedSpears.put(trident.getUniqueId(), tracked);
        startTrailTask(trident);
    }

    public void handleHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof Trident trident)) {
            return;
        }

        TrackedSpear tracked = trackedSpears.get(trident.getUniqueId());
        if (tracked == null && !isMarkedChronosProjectile(trident)) {
            return;
        }
        if (tracked == null) {
            trident.remove();
            return;
        }

        Entity hitEntity = event.getHitEntity();
        Player shooter = Bukkit.getPlayer(tracked.shooterId());
        if (hitEntity instanceof LivingEntity target && !target.getUniqueId().equals(tracked.shooterId())) {
            freezeService.freeze(target, TARGET_FREEZE_TICKS);
            playFreezeImpact(target.getLocation());
            complete(trident, tracked, false);
            return;
        }

        if (shooter != null && shooter.isOnline()) {
            freezeService.freeze(shooter, BACKFIRE_FREEZE_TICKS);
            shooter.playSound(shooter.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 0.65f);
        }
        complete(trident, tracked, false);
    }

    public void recoverOutstanding(Player player) {
        Iterator<Map.Entry<UUID, TrackedSpear>> iterator = trackedSpears.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, TrackedSpear> entry = iterator.next();
            TrackedSpear tracked = entry.getValue();
            if (!tracked.shooterId().equals(player.getUniqueId())) {
                continue;
            }
            tracked.markCompleted();
            iterator.remove();
            cancelTrailTask(tracked.projectileId());
            Entity projectile = Bukkit.getEntity(tracked.projectileId());
            if (projectile != null) {
                projectile.remove();
            }
            giveBack(player, tracked.spearItem());
        }
    }

    public void shutdown() {
        for (BukkitTask task : trailTasks.values()) {
            task.cancel();
        }
        trailTasks.clear();

        for (TrackedSpear tracked : trackedSpears.values()) {
            Player shooter = Bukkit.getPlayer(tracked.shooterId());
            Entity projectile = Bukkit.getEntity(tracked.projectileId());
            if (projectile != null) {
                projectile.remove();
            }
            if (shooter != null && shooter.isOnline()) {
                giveBack(shooter, tracked.spearItem());
            }
        }
        trackedSpears.clear();
    }

    private Optional<ItemStack> findChronosSpearItem(Trident trident, Player shooter) {
        ItemStack projectileItem = trident.getItemStack();
        if (itemMatcher.is(projectileItem, ExclusiveItemId.CHRONOS_ANCHOR_SPEAR)) {
            return Optional.of(projectileItem);
        }

        ItemStack mainHand = shooter.getInventory().getItemInMainHand();
        if (itemMatcher.is(mainHand, ExclusiveItemId.CHRONOS_ANCHOR_SPEAR)) {
            return Optional.of(mainHand);
        }

        ItemStack offHand = shooter.getInventory().getItemInOffHand();
        if (itemMatcher.is(offHand, ExclusiveItemId.CHRONOS_ANCHOR_SPEAR)) {
            return Optional.of(offHand);
        }
        return Optional.empty();
    }

    private boolean isMarkedChronosProjectile(Trident trident) {
        return trident.hasMetadata(PROJECTILE_METADATA)
            || trident.getPersistentDataContainer().has(markerKey, PersistentDataType.BYTE);
    }

    private void startTrailTask(Trident trident) {
        UUID projectileId = trident.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            TrackedSpear tracked = trackedSpears.get(projectileId);
            Entity entity = Bukkit.getEntity(projectileId);
            if (tracked == null || tracked.completed() || !(entity instanceof Trident active) || active.isDead() || !active.isValid()) {
                cancelTrailTask(projectileId);
                return;
            }

            long elapsedTicks = (System.currentTimeMillis() - tracked.launchTimeMillis()) / 50L;
            if (elapsedTicks >= MISS_TIMEOUT_TICKS) {
                Player shooter = Bukkit.getPlayer(tracked.shooterId());
                if (shooter != null && shooter.isOnline()) {
                    freezeService.freeze(shooter, BACKFIRE_FREEZE_TICKS);
                    shooter.playSound(shooter.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 0.8f, 0.65f);
                }
                complete(active, tracked, false);
                return;
            }

            Location location = active.getLocation();
            active.getWorld().spawnParticle(Particle.END_ROD, location, 2, 0.05, 0.05, 0.05, 0.0);
            active.getWorld().spawnParticle(Particle.ENCHANT, location, 1, 0.08, 0.08, 0.08, 0.0);
        }, 1L, 2L);
        trailTasks.put(projectileId, task);
    }

    private void complete(Trident trident, TrackedSpear tracked, boolean skipReturn) {
        if (tracked.completed()) {
            return;
        }
        tracked.markCompleted();
        trackedSpears.remove(tracked.projectileId());
        cancelTrailTask(tracked.projectileId());
        trident.remove();

        if (!skipReturn) {
            Player shooter = Bukkit.getPlayer(tracked.shooterId());
            if (shooter != null && shooter.isOnline()) {
                giveBack(shooter, tracked.spearItem());
            }
        }
    }

    private void giveBack(Player player, ItemStack item) {
        Map<Integer, ItemStack> overflow = player.getInventory().addItem(item.clone());
        for (ItemStack extra : overflow.values()) {
            player.getWorld().dropItem(player.getLocation(), extra, dropped -> dropped.setOwner(player.getUniqueId()));
        }
    }

    private void cancelTrailTask(UUID projectileId) {
        BukkitTask task = trailTasks.remove(projectileId);
        if (task != null) {
            task.cancel();
        }
    }

    private void playFreezeImpact(Location location) {
        location.getWorld().spawnParticle(Particle.END_ROD, location.add(0.0, 1.0, 0.0), 24, 0.45, 0.5, 0.45, 0.02);
        location.getWorld().spawnParticle(Particle.ENCHANT, location, 32, 0.7, 0.7, 0.7, 0.1);
        location.getWorld().playSound(location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, SoundCategory.PLAYERS, 1.0f, 1.35f);
    }
}
