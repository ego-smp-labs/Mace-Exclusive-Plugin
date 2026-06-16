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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;

import java.util.UUID;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class SoulfirePyreMaceAbility implements ActiveAbility, PassiveAbility, Listener {

    private static final String ID = "soulfire_mace.fire_storm";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final Map<UUID, BurnState> soulBurns = new HashMap<>();
    private final Map<UUID, Long> activeAuraUntilMillis = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask soulBurnTask;

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
        return true;
    }

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        UUID uuid = player.getUniqueId();

        if (!cooldownService.checkAndNotify(player, id())) {
            return;
        }

        double radius = configManager.getItemEffectDouble("soulfire_mace", "effects.active.radius", 10.0D);
        int durationSec = configManager.getItemEffectInt("soulfire_mace", "effects.active.duration", 20);
        long cooldownSec = configManager.getItemEffectInt("soulfire_mace", "cooldowns.fire_storm", 40);
        double lavaHeal = configManager.getItemEffectDouble("soulfire_mace", "effects.active.lava_heal_per_pulse", 1.0D);

        cooldownService.setCooldown(player, id(), cooldownSec * 1000L);
        activeAuraUntilMillis.put(uuid, System.currentTimeMillis() + (durationSec * 1000L));

        player.getWorld().playSound(player.getLocation(), Sound.ITEM_FIRECHARGE_USE, 1.0f, 0.7f);
        net.kyori.adventure.text.Component msg = configManager.getItemMessage("soulfire_mace", "messages.skill-fire-storm");
        if (msg != null) player.sendMessage(msg);

        new BukkitRunnable() {
            int secondsElapsed = 0;

            @Override
            public void run() {
                if (secondsElapsed >= durationSec || !player.isOnline() || player.isDead()) {
                    activeAuraUntilMillis.remove(uuid);
                    this.cancel();
                    return;
                }

                Location center = player.getLocation();
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

                double dmg = configManager.getItemEffectDouble("soulfire_mace", "effects.active.damage_per_second", 3.0D);
                for (Entity entity : center.getWorld().getNearbyEntities(center, radius, 3.0D, radius)) {
                    if (entity instanceof LivingEntity living && !living.equals(player)) {
                        living.damage(dmg, player);
                        living.setFireTicks(Math.max(living.getFireTicks(), 80));
                        living.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 0, false, false, false));
                        living.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, living.getLocation().add(0.0, 1.0, 0.0), 5);
                    }
                }

                if (lavaHeal > 0.0D && isInLava(player)) {
                    double maxHealth = player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) == null
                        ? 20.0D
                        : player.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue();
                    player.setHealth(Math.min(maxHealth, player.getHealth() + lavaHeal));
                    player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0.0, 1.2D, 0.0), 2, 0.25, 0.2, 0.25, 0.0);
                }

                secondsElapsed++;
            }
        }.runTaskTimer(plugin, 0L, 20L);
    }

    private boolean isInLava(Player player) {
        return player.getLocation().getBlock().getType() == org.bukkit.Material.LAVA
            || player.getEyeLocation().getBlock().getType() == org.bukkit.Material.LAVA;
    }

    public boolean isAuraActive(Player player) {
        return activeAuraUntilMillis.getOrDefault(player.getUniqueId(), 0L) > System.currentTimeMillis();
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        LivingEntity target = context.target();
        if (target == null) return;

        // Soul Fire on Hit: refresh a bounded shared task instead of spawning one task per hit.
        soulBurns.put(target.getUniqueId(), new BurnState(target, attacker, 3));
        ensureSoulBurnTask();
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerDeath(PlayerDeathEvent event) {
        activeAuraUntilMillis.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        activeAuraUntilMillis.remove(event.getPlayer().getUniqueId());
    }

    private void ensureSoulBurnTask() {
        if (soulBurnTask != null) return;
        soulBurnTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            Iterator<Map.Entry<UUID, BurnState>> iterator = soulBurns.entrySet().iterator();
            while (iterator.hasNext()) {
                BurnState state = iterator.next().getValue();
                if (state.remainingTicks <= 0 || state.target.isDead() || !state.target.isValid() || !state.attacker.isOnline()) {
                    iterator.remove();
                    continue;
                }
                state.target.damage(2.0D, state.attacker);
                state.target.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, state.target.getLocation().add(0.0, 1.0, 0.0), 5, 0.3, 0.5, 0.3, 0.01);
                state.remainingTicks--;
            }
            if (soulBurns.isEmpty() && soulBurnTask != null) {
                soulBurnTask.cancel();
                soulBurnTask = null;
            }
        }, 20L, 20L);
    }

    private static final class BurnState {
        private final LivingEntity target;
        private final Player attacker;
        private int remainingTicks;

        private BurnState(LivingEntity target, Player attacker, int remainingTicks) {
            this.target = target;
            this.attacker = attacker;
            this.remainingTicks = remainingTicks;
        }
    }
}
