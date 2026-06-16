package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class GravityMaceAbility implements ActiveAbility, PassiveAbility, Listener {

    private static final String ID = "gravity_mace.gravity_well";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final List<Location> activeWells = new ArrayList<>();
    private final Random random = new Random();

    public GravityMaceAbility(MaceExclusivePlugin plugin, ConfigManager configManager, CooldownService cooldownService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cooldownService = cooldownService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String weaponId() {
        return "gravity_mace";
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        Player player = context.player();
        // Caster needs to look at a block or target
        Location targetLoc = getTargetLocation(player);
        if (targetLoc == null) {
            net.kyori.adventure.text.Component msg = configManager.getItemMessage("gravity_mace", "messages.no-target");
            if (msg != null) player.sendMessage(msg);
            return false;
        }
        return true;
    }

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        UUID uuid = player.getUniqueId();

        if (!cooldownService.checkAndNotify(player, id())) {
            return;
        }

        Location center = getTargetLocation(player);
        if (center == null) return;

        double pullRadius = configManager.getItemEffectDouble("gravity_mace", "effects.active.pull_radius", 8.0D);
        int durationSec = configManager.getItemEffectInt("gravity_mace", "effects.active.duration", 3);
        long cooldownSec = configManager.getItemEffectInt("gravity_mace", "cooldowns.gravity_well", 60);

        activeWells.add(center);
        cooldownService.setCooldown(player, id(), cooldownSec * 1000L);

        net.kyori.adventure.text.Component msg = configManager.getItemMessage("gravity_mace", "messages.skill-gravity-well");
        if (msg != null) player.sendMessage(msg);

        // Run gravity well at a bounded interval; avoid per-tick entity scans.
        new BukkitRunnable() {
            int ticks = 0;
            final int maxTicks = durationSec * 20;
            final int periodTicks = 5;
            final Set<UUID> pulledEntityIds = new HashSet<>();

            @Override
            public void run() {
                if (ticks >= maxTicks) {
                    this.cancel();
                    activeWells.remove(center);
                    triggerCollapse(player, center, pullRadius, pulledEntityIds.size());
                    return;
                }

                // Play swirling black hole particles
                center.getWorld().spawnParticle(Particle.PORTAL, center, 5, 0.5, 0.5, 0.5, 0.05);
                center.getWorld().spawnParticle(Particle.DRAGON_BREATH, center, 2, 0.3, 0.3, 0.3, 0.01);
                if (ticks % 10 == 0) {
                    center.getWorld().playSound(center, Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 0.5f);
                }

                // Pull entities
                for (Entity entity : center.getWorld().getNearbyEntities(center, pullRadius, pullRadius, pullRadius)) {
                    if (!(entity instanceof LivingEntity living) || living.equals(player)) continue;

                    Vector dir = center.toVector().subtract(living.getLocation().toVector());
                    double distSq = dir.lengthSquared();
                    if (distSq > 0.1) {
                        living.setVelocity(dir.normalize().multiply(0.2D));
                        pulledEntityIds.add(living.getUniqueId());
                    }

                    // Apply short Slowness between pull pulses; collapse handles damage once.
                    living.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, periodTicks + 10, 2, false, false, false));
                }

                ticks += periodTicks;
            }
        }.runTaskTimer(plugin, 0L, 5L);
    }

    private void triggerCollapse(Player caster, Location center, double radius, int pulledCount) {
        center.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        center.getWorld().spawnParticle(Particle.ELECTRIC_SPARK, center, 24, 0.5, 0.5, 0.5, 0.08);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.2f, 0.8f);

        List<LivingEntity> nearby = new ArrayList<>();
        for (Entity entity : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !living.equals(caster)) {
                nearby.add(living);
            }
        }

        for (LivingEntity living : nearby) {
            Vector dir = living.getLocation().toVector().subtract(center.toVector());
            if (dir.lengthSquared() > 0.09) {
                living.setVelocity(dir.normalize().multiply(0.8D).setY(0.4D));
            }
        }

        if (!caster.isOnline() || caster.isDead()) {
            return;
        }

        if (pulledCount <= 0) {
            caster.damage(3.0D);
            applyMiningFatigue(caster);
            cooldownService.setCooldown(caster, id(), 300_000L);
            return;
        }

        double capHearts = configManager.getItemEffectDouble("gravity_mace", "effects.active.max_absorption_hearts", 10.0D);
        double absorptionHp = Math.min(capHearts * 2.0D, pulledCount * 2.0D);
        if (caster.getAbsorptionAmount() < absorptionHp) {
            caster.setAbsorptionAmount(absorptionHp);
        }

        if (pulledCount > 6) {
            applyMiningFatigue(caster);
        }
    }

    private void applyMiningFatigue(Player player) {
        int durationTicks = configManager.getItemEffectInt("gravity_mace", "effects.active.mining_fatigue_duration", 240);
        int amplifier = configManager.getItemEffectInt("gravity_mace", "effects.active.mining_fatigue_amplifier", 1);
        player.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, durationTicks, amplifier, false, true, true));
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        LivingEntity target = context.target();
        if (target == null) return;

        // Pull target slightly closer on hit
        Vector dir = attacker.getLocation().toVector().subtract(target.getLocation().toVector());
        if (dir.lengthSquared() > 0.19) {
            target.setVelocity(dir.normalize().multiply(0.25D));
            attacker.getWorld().spawnParticle(Particle.CRIT, target.getLocation().add(0.0, 1.0, 0.0), 6);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !plugin.getMaceManager().getExclusiveItemKey(weapon).filter(id -> id.equals("gravity_mace")).isPresent()) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            double reduction = configManager.getItemEffectDouble("gravity_mace", "effects.fall_reduction", 0.70D);
            event.setDamage(event.getDamage() * (1.0D - reduction));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) {
            for (Location well : activeWells) {
                if (event.getFrom().getWorld().equals(well.getWorld()) && event.getFrom().distanceSquared(well) <= 64.0) {
                    event.setCancelled(true);
                    net.kyori.adventure.text.Component msg = configManager.getItemMessage("gravity_mace", "messages.teleport-blocked");
                    if (msg != null) event.getPlayer().sendMessage(msg);
                    event.getPlayer().playSound(event.getPlayer().getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.6f, 1.2f);
                }
            }
        }
    }

    private Location getTargetLocation(Player player) {
        LivingEntity lookTarget = player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 10.0, 0.6,
            entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(player.getUniqueId())) != null ?
            (LivingEntity) player.getWorld().rayTraceEntities(player.getEyeLocation(), player.getEyeLocation().getDirection(), 10.0, 0.6,
            entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(player.getUniqueId())).getHitEntity() : null;

        if (lookTarget != null) return lookTarget.getLocation();

        var blockTrace = player.getWorld().rayTraceBlocks(player.getEyeLocation(), player.getEyeLocation().getDirection(), 10.0, org.bukkit.FluidCollisionMode.NEVER, true);
        return blockTrace == null ? null : blockTrace.getHitPosition().toLocation(player.getWorld());
    }

    public void restoreAll() {
        activeWells.clear();
    }
}
