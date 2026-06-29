package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.FluidCollisionMode;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.SafeParticleSpawner;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class SonicWardenSpearAbility implements ActiveAbility, PassiveAbility, Listener {

    private static final String ID = "sonic_spear.sonic_boom";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final Map<UUID, TimedSonicAttacker> recentSonicDamage = new HashMap<>();

    public SonicWardenSpearAbility(MaceExclusivePlugin plugin, ConfigManager configManager, CooldownService cooldownService) {
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
        return "sonic_spear";
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        return true;
    }

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        UUID uuid = player.getUniqueId();

        if (!cooldownService.checkAndNotify(player, id())) {
            return;
        }

        double range = configManager.getItemEffectDouble("sonic_spear", "effects.active.range", 12.0D);
        double damage = configManager.getItemEffectDouble("sonic_spear", "effects.active.damage", 14.0D);
        double knockback = configManager.getItemEffectDouble("sonic_spear", "effects.active.knockback", 2.0D);
        long cooldownSec = configManager.getItemEffectInt("sonic_spear", "cooldowns.sonic_boom", 35);

        // Fire Sonic Boom: one entity ray trace plus optional block occlusion check.
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        RayTraceResult entityTrace = player.getWorld().rayTraceEntities(
            eye,
            dir,
            range,
            0.8D,
            entity -> entity instanceof LivingEntity && !entity.equals(player)
        );
        RayTraceResult blockTrace = player.getWorld().rayTraceBlocks(eye, dir, range, FluidCollisionMode.NEVER, true);
        double beamDistance = range;
        LivingEntity hit = null;
        if (entityTrace != null && entityTrace.getHitEntity() instanceof LivingEntity living) {
            double entityDistance = entityTrace.getHitPosition().distance(eye.toVector());
            double blockDistance = blockTrace == null ? Double.MAX_VALUE : blockTrace.getHitPosition().distance(eye.toVector());
            if (entityDistance <= blockDistance + 0.05D) {
                hit = living;
                beamDistance = entityDistance;
            } else {
                beamDistance = Math.min(beamDistance, blockDistance);
            }
        } else if (blockTrace != null) {
            beamDistance = blockTrace.getHitPosition().distance(eye.toVector());
        }

        // Spawn capped Sonic particles along trace line.
        for (double d = 0.75D; d <= beamDistance; d += 1.0D) {
            Location pLoc = eye.clone().add(dir.clone().multiply(d));
            SafeParticleSpawner.spawn(pLoc.getWorld(), Particle.SONIC_BOOM, pLoc, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (hit != null) {
            recentSonicDamage.put(hit.getUniqueId(), new TimedSonicAttacker(player.getUniqueId(), System.currentTimeMillis() + 10_000L));
            hit.damage(damage, player);
            hit.setVelocity(dir.clone().multiply(knockback).setY(0.35));
        }

        cooldownService.setCooldown(player, id(), cooldownSec * 1000L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
        net.kyori.adventure.text.Component msg = configManager.getItemMessage("sonic_spear", "messages.skill-sonic-boom");
        if (msg != null) player.sendMessage(msg);

        int darknessDur = configManager.getItemEffectInt("sonic_spear", "effects.curses.darkness_duration", 30);
        int slowDur = configManager.getItemEffectInt("sonic_spear", "effects.curses.slowness_duration", 40);
        int slowAmp = configManager.getItemEffectInt("sonic_spear", "effects.curses.slowness_amplifier", 1);
        
        player.addPotionEffect(new PotionEffect(resolveDarknessEffect(), darknessDur, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDur, slowAmp));
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        recentSonicDamage.put(event.getEntity().getUniqueId(), new TimedSonicAttacker(attacker.getUniqueId(), System.currentTimeMillis() + 10_000L));
        double fallDistance = attacker.getFallDistance();
        if (fallDistance >= 4.0F) {
            double bonusPer4 = configManager.getItemEffectDouble("sonic_spear", "effects.passive.damage_per_4_blocks", 1.5D);
            double cap = configManager.getItemEffectDouble("sonic_spear", "effects.passive.max_bonus_damage", 6.0D);
            double bonus = Math.min(cap, (fallDistance / 4.0F) * bonusPer4);
            event.setDamage(event.getDamage() + bonus);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity target)) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (isHoldingSonicSpear(attacker)) {
            recentSonicDamage.put(target.getUniqueId(), new TimedSonicAttacker(attacker.getUniqueId(), System.currentTimeMillis() + 10_000L));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityDeath(EntityDeathEvent event) {
        Player owner = resolveSonicKiller(event);
        if (owner == null || owner.getWorld() != event.getEntity().getWorld()) return;
        double chance = configManager.getItemEffectDouble("sonic_spear", "effects.kill_proc.sculk_shrine_chance", 0.10D);
        if (Math.random() > chance) return;
        createSculkShrine(event.getEntity().getLocation(), owner.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onWardenTarget(EntityTargetLivingEntityEvent event) {
        if (event.getEntityType() != EntityType.WARDEN || !(event.getTarget() instanceof Player target)) return;
        if (isOwnedWardenTargetingOwner(event.getEntity(), target.getUniqueId())) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSculkSensor(BlockReceiveGameEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (weapon != null && plugin.getMaceManager().getExclusiveItemKey(weapon).filter(id -> id.equals("sonic_spear")).isPresent()) {
                Material blockDown = player.getLocation().getBlock().getRelative(0, -1, 0).getType();
                if (blockDown == Material.SCULK || blockDown == Material.SCULK_VEIN) {
                    event.setCancelled(true);
                }
            }
        }
    }

    public void applyPassiveTick(Player player) {
        // Run in periodic check if held
        Material blockDown = player.getLocation().getBlock().getRelative(0, -1, 0).getType();
        if (blockDown == Material.SCULK || blockDown == Material.SCULK_VEIN) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 40, 0, false, false, false));
        }
    }

    private Player resolveSonicKiller(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null && isHoldingSonicSpear(killer)) {
            return killer;
        }
        TimedSonicAttacker recent = recentSonicDamage.remove(event.getEntity().getUniqueId());
        if (recent == null || recent.expiresAtMillis() < System.currentTimeMillis()) {
            return null;
        }
        Player player = plugin.getServer().getPlayer(recent.ownerId());
        return player != null && player.isOnline() ? player : null;
    }

    private boolean isHoldingSonicSpear(Player player) {
        return isSonicSpear(player.getInventory().getItemInMainHand())
            || isSonicSpear(player.getInventory().getItemInOffHand());
    }

    private boolean isSonicSpear(ItemStack item) {
        return item != null && plugin.getMaceManager().getExclusiveItemKey(item).filter(id -> id.equals("sonic_spear")).isPresent();
    }

    private PotionEffectType resolveDarknessEffect() {
        PotionEffectType darkness = PotionEffectType.getByName("DARKNESS");
        return darkness == null ? PotionEffectType.BLINDNESS : darkness;
    }

    private void createSculkShrine(Location deathLocation, UUID ownerId) {
        Location base = deathLocation.getBlock().getLocation();
        placeIfSafe(base, Material.SCULK);
        placeIfSafe(base.clone().add(1, 0, 0), Material.SCULK);
        placeIfSafe(base.clone().add(-1, 0, 0), Material.SCULK);
        placeIfSafe(base.clone().add(0, 0, 1), Material.SCULK);
        placeIfSafe(base.clone().add(0, 0, -1), Material.SCULK);
        placeIfSafe(base.clone().add(1, 0, 1), Material.SCULK_VEIN);
        placeIfSafe(base.clone().add(-1, 0, -1), Material.SCULK_VEIN);
        placeIfSafe(base.clone().add(0, 1, 0), Material.SCULK_CATALYST);
        placeIfSafe(base.clone().add(0, 1, 1), Material.SCULK_SHRIEKER);
        tagNearbyWardens(base, ownerId);
        SafeParticleSpawner.spawn(base.getWorld(), Particle.SCULK_SOUL, base.clone().add(0.5, 1.0, 0.5), 36, 1.2, 0.8, 1.2, 0.05);
        base.getWorld().playSound(base, Sound.BLOCK_SCULK_SHRIEKER_SHRIEK, 0.8f, 0.8f);
    }

    private void placeIfSafe(Location location, Material material) {
        Block block = location.getBlock();
        if (!isReplaceable(block.getType())) return;
        block.setType(material, false);
    }

    private boolean isReplaceable(Material material) {
        return material.isAir()
            || material == Material.SHORT_GRASS
            || material == Material.TALL_GRASS
            || material == Material.FERN
            || material == Material.LARGE_FERN
            || material == Material.SEAGRASS
            || material == Material.SNOW
            || material == Material.VINE
            || material == Material.GLOW_LICHEN;
    }

    private void tagNearbyWardens(Location origin, UUID ownerId) {
        for (Entity entity : origin.getWorld().getNearbyEntities(origin, 12.0D, 8.0D, 12.0D)) {
            if (entity.getType() == EntityType.WARDEN) {
                entity.setMetadata("mace_exclusive_warden_spear_owner", new FixedMetadataValue(plugin, ownerId.toString()));
            }
        }
    }

    private boolean isOwnedWardenTargetingOwner(Entity warden, UUID targetId) {
        return warden.getMetadata("mace_exclusive_warden_spear_owner").stream()
            .filter(value -> value.getOwningPlugin() == plugin)
            .map(value -> value.asString())
            .anyMatch(owner -> owner.equals(targetId.toString()));
    }

    private record TimedSonicAttacker(UUID ownerId, long expiresAtMillis) { }
}
