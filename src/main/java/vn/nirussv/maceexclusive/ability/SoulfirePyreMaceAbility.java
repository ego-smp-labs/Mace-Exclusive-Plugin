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
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;

import java.util.UUID;

public final class SoulfirePyreMaceAbility implements ActiveAbility, PassiveAbility, Listener {

    private static final String ID = "soulfire_mace.fire_storm";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    public SoulfirePyreMaceAbility(MaceExclusivePlugin plugin, ConfigManager configManager, CooldownService cooldownService) {
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
        return "soulfire_mace";
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        Player player = context.player();
        double cost = configManager.getItemEffectDouble("soulfire_mace", "effects.active.hp_cost", 4.0D);
        if (player.getHealth() <= cost) {
            player.sendMessage("§cKhông đủ máu để triệu hồi bão lửa linh hồn!");
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

        double radius = configManager.getItemEffectDouble("soulfire_mace", "effects.active.radius", 5.0D);
        int durationSec = configManager.getItemEffectInt("soulfire_mace", "effects.active.duration", 5);
        double cost = configManager.getItemEffectDouble("soulfire_mace", "effects.active.hp_cost", 4.0D);
        long cooldownSec = configManager.getItemEffectInt("soulfire_mace", "cooldowns.fire_storm", 40);

        // Deduct HP cost
        player.setHealth(Math.max(1.0D, player.getHealth() - cost));

        Location center = player.getLocation();
        cooldownService.setCooldown(player, id(), cooldownSec * 1000L);

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.7f);
        net.kyori.adventure.text.Component msg = configManager.getItemMessage("soulfire_mace", "messages.skill-fire-storm");
        if (msg != null) player.sendMessage(msg);

        // Run fire storm task
        new BukkitRunnable() {
            int secondsElapsed = 0;

            @Override
            public void run() {
                if (secondsElapsed >= durationSec) {
                    this.cancel();
                    return;
                }

                // Visual fire storm: circle of particles
                for (int i = 0; i < 30; i++) {
                    double angle = (Math.PI * 2.0D * i) / 30.0D;
                    double x = Math.cos(angle) * radius;
                    double z = Math.sin(angle) * radius;
                    Location particleLoc = center.clone().add(x, 0.25D, z);
                    particleLoc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, particleLoc, 1, 0.1, 0.1, 0.1, 0.0);
                    if (i % 6 == 0) {
                        center.getWorld().spawnParticle(Particle.SOUL, center.clone().add(x / 2.0, 0.5D, z / 2.0), 1, 0.0, 0.0, 0.0, 0.01);
                    }
                }
                center.getWorld().playSound(center, Sound.BLOCK_CAMPFIRE_CRACKLE, 0.6f, 0.5f);

                // Damage enemies inside
                double dmg = configManager.getItemEffectDouble("soulfire_mace", "effects.active.damage_per_second", 3.0D);
                for (Entity entity : center.getWorld().getNearbyEntities(center, radius, 3.0D, radius)) {
                    if (entity instanceof LivingEntity living && !living.equals(player)) {
                        living.damage(dmg, player);
                        living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, false, false));
                        living.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, living.getLocation().add(0.0, 1.0, 0.0), 5);
                    }
                }

                secondsElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        LivingEntity target = context.target();
        if (target == null) return;

        // Soul Fire on Hit: 3s tick
        new BukkitRunnable() {
            int count = 0;

            @Override
            public void run() {
                if (count >= 3 || target.isDead() || !target.isValid()) {
                    this.cancel();
                    return;
                }
                target.damage(2.0D, attacker);
                target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, target.getLocation().add(0.0, 1.0, 0.0), 8, 0.3, 0.5, 0.3, 0.01);
                count++;
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFireDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !plugin.getMaceManager().getExclusiveItemKey(weapon).filter(id -> id.equals("soulfire_mace")).isPresent()) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK || 
            cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            event.setCancelled(true);
        }
    }
}
