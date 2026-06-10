package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.FluidCollisionMode;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;

import java.util.UUID;

public final class SonicWardenMaceAbility implements ActiveAbility, PassiveAbility, Listener {

    private static final String ID = "sonic_mace.sonic_boom";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public SonicWardenMaceAbility(MaceExclusivePlugin plugin, ConfigManager configManager, CooldownService cooldownService) {
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
        return "sonic_mace";
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        Player player = context.player();
        double cost = configManager.getItemEffectDouble("sonic_mace", "active.hp_cost", 4.0D);
        if (player.getHealth() <= cost) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cKhông đủ máu để giải phóng sóng siêu thanh!"));
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

        double range = configManager.getItemEffectDouble("sonic_mace", "effects.active.range", 12.0D);
        double damage = configManager.getItemEffectDouble("sonic_mace", "effects.active.damage", 14.0D);
        double knockback = configManager.getItemEffectDouble("sonic_mace", "effects.active.knockback", 2.0D);
        double cost = configManager.getItemEffectDouble("sonic_mace", "effects.active.hp_cost", 4.0D);
        long cooldownSec = configManager.getItemEffectInt("sonic_mace", "cooldowns.sonic_boom", 35);

        // Cost HP
        player.setHealth(Math.max(1.0D, player.getHealth() - cost));

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
            pLoc.getWorld().spawnParticle(Particle.SONIC_BOOM, pLoc, 1, 0.0, 0.0, 0.0, 0.0);
        }
        if (hit != null) {
            hit.damage(damage, player);
            hit.setVelocity(dir.clone().multiply(knockback).setY(0.35));
        }

        cooldownService.setCooldown(player, id(), cooldownSec * 1000L);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.0f, 1.0f);
        net.kyori.adventure.text.Component msg = configManager.getItemMessage("sonic_mace", "messages.skill-sonic-boom");
        if (msg != null) player.sendMessage(msg);

        // Curse: active triggers Blindness 1.5s (30 ticks) and Slowness II 2s (40 ticks)
        int blindDur = configManager.getItemEffectInt("sonic_mace", "effects.curses.blindness_duration", 30);
        int slowDur = configManager.getItemEffectInt("sonic_mace", "effects.curses.slowness_duration", 40);
        int slowAmp = configManager.getItemEffectInt("sonic_mace", "effects.curses.slowness_amplifier", 1);
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, blindDur, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, slowDur, slowAmp));
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        double fallDistance = attacker.getFallDistance();
        if (fallDistance >= 4.0F) {
            double bonusPer4 = configManager.getItemEffectDouble("sonic_mace", "effects.passive.damage_per_4_blocks", 1.5D);
            double cap = configManager.getItemEffectDouble("sonic_mace", "effects.passive.max_bonus_damage", 6.0D);
            double bonus = Math.min(cap, (fallDistance / 4.0F) * bonusPer4);
            event.setDamage(event.getDamage() + bonus);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onSculkSensor(BlockReceiveGameEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack weapon = player.getInventory().getItemInMainHand();
            if (weapon != null && plugin.getMaceManager().getExclusiveItemKey(weapon).filter(id -> id.equals("sonic_mace")).isPresent()) {
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
}
