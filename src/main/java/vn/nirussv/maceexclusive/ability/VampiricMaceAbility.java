package vn.nirussv.maceexclusive.ability;

import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.util.Scheduler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;

public final class VampiricMaceAbility implements ActiveAbility, PassiveAbility, Listener {

    private static final String ID = "vampiric_mace.siphon";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;

    // Track active siphon boosts: Caster UUID -> modifier
    private final Map<UUID, AttributeModifier> siphonModifiers = new HashMap<>();
    private final Map<String, Long> siphonExpiries = new HashMap<>();

    public VampiricMaceAbility(MaceExclusivePlugin plugin, ConfigManager configManager, CooldownService cooldownService) {
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
        return "vampiric_mace";
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        Player player = context.player();
        LivingEntity target = context.target();
        if (target == null) {
            net.kyori.adventure.text.Component msg = configManager.getItemMessage("vampiric_mace", "messages.no-target");
            if (msg != null) player.sendMessage(msg);
            return false;
        }
        return true;
    }

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        LivingEntity target = context.target();
        UUID uuid = player.getUniqueId();

        if (!cooldownService.checkAndNotify(player, id())) {
            return;
        }

        long durationSeconds = configManager.getItemEffectInt("vampiric_mace", "siphon.duration", 90);
        long cooldownSeconds = configManager.getItemEffectInt("vampiric_mace", "cooldowns.siphon", 75);
        long expiresAt = System.currentTimeMillis() + durationSeconds * 1000L;

        // Max HP siphon: decrease victim, increase caster
        // Cap caster gain: +4.0 Max HP (2 hearts)
        double currentModifier = 0.0D;
        AttributeInstance maxHealthAttr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealthAttr != null) {
            // Check existing modifier
            AttributeModifier existing = siphonModifiers.get(uuid);
            if (existing != null) {
                currentModifier = existing.getAmount();
                maxHealthAttr.removeModifier(existing);
            }
        }

        double gain = 2.0D; // 1 heart
        double newModifierAmount = Math.min(4.0D, currentModifier + gain);

        NamespacedKey modifierKey = new NamespacedKey(plugin, "vampiric_siphon");
        AttributeModifier modifier = new AttributeModifier(modifierKey, newModifierAmount, AttributeModifier.Operation.ADD_NUMBER);
        
        if (maxHealthAttr != null) {
            maxHealthAttr.addModifier(modifier);
            siphonModifiers.put(uuid, modifier);
            siphonExpiries.put(casterExpiryKey(uuid), expiresAt);
        }

        // Apply direct siphon damage and penalty to victim.
        target.damage(configManager.getItemEffectDouble("vampiric_mace", "siphon.damage", 6.0D), player);
        if (target instanceof Player victim) {
            AttributeInstance victimMaxHealth = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (victimMaxHealth != null) {
                NamespacedKey victimKey = new NamespacedKey(plugin, "vampiric_siphoned_" + uuid.toString());
                AttributeModifier penaltyMod = new AttributeModifier(victimKey, -2.0D, AttributeModifier.Operation.ADD_NUMBER);
                for (AttributeModifier existing : java.util.List.copyOf(victimMaxHealth.getModifiers())) {
                    if (victimKey.equals(existing.getKey())) victimMaxHealth.removeModifier(existing);
                }
                victimMaxHealth.addModifier(penaltyMod);
                String expiryKey = victimExpiryKey(victim.getUniqueId(), uuid);
                siphonExpiries.put(expiryKey, expiresAt);
                 
                // Restore victim HP later
                Scheduler.runEntityTaskLater(plugin, victim, () -> {
                    if (siphonExpiries.getOrDefault(expiryKey, 0L) > System.currentTimeMillis()) return;
                    siphonExpiries.remove(expiryKey);
                    if (victim.isOnline()) {
                        AttributeInstance attr = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        if (attr != null) {
                            for (AttributeModifier existing : java.util.List.copyOf(attr.getModifiers())) {
                                if (victimKey.equals(existing.getKey())) attr.removeModifier(existing);
                            }
                        }
                    }
                }, durationSeconds * 20L);
            }
        }

        // Heal caster
        double healAmount = configManager.getItemEffectDouble("vampiric_mace", "siphon.immediate_heal", 6.0D);
        double maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null 
            ? player.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() 
            : 20.0D;
        player.setHealth(Math.min(maxHealth, player.getHealth() + healAmount));

        cooldownService.setCooldown(player, id(), cooldownSeconds * 1000L);

        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.7f);
        net.kyori.adventure.text.Component msg = configManager.getItemMessage("vampiric_mace", "messages.skill-siphon");
        if (msg != null) player.sendMessage(msg);

        // Schedule remove caster boost
        Scheduler.runEntityTaskLater(plugin, player, () -> {
            if (siphonExpiries.getOrDefault(casterExpiryKey(uuid), 0L) > System.currentTimeMillis()) return;
            siphonExpiries.remove(casterExpiryKey(uuid));
            if (player.isOnline()) {
                AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) {
                    AttributeModifier existing = siphonModifiers.remove(uuid);
                    if (existing != null) attr.removeModifier(existing);
                }
            } else {
                siphonModifiers.remove(uuid);
            }
        }, durationSeconds * 20L);
    }

    private String casterExpiryKey(UUID caster) {
        return "caster:" + caster;
    }

    private String victimExpiryKey(UUID victim, UUID caster) {
        return "victim:" + victim + ":" + caster;
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        LivingEntity target = context.target();
        if (target == null) return;

        // Lifesteal: 12% damage
        double percent = configManager.getItemEffectDouble("vampiric_mace", "lifesteal.percent", 0.12D);
        double maxHeal = configManager.getItemEffectDouble("vampiric_mace", "lifesteal.max_heal", 2.0D);
        double damage = event.getFinalDamage();
        double heal = Math.min(maxHeal, damage * percent);
        
        double maxHealthAttr = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null 
            ? attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() 
            : 20.0D;
        attacker.setHealth(Math.min(maxHealthAttr, attacker.getHealth() + heal));
        attacker.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, target.getLocation().add(0.0, 1.0, 0.0), 3);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.6f, 1.5f);

        // Low health damage boost: +20% damage if health < 30%
        double maxHealthVal = attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH) != null 
            ? attacker.getAttribute(Attribute.GENERIC_MAX_HEALTH).getValue() 
            : 20.0D;
        if (attacker.getHealth() / maxHealthVal < 0.30) {
            event.setDamage(event.getDamage() * 1.20D);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageCurse(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !plugin.getMaceManager().getExclusiveItemKey(weapon).filter(id -> id.equals("vampiric_mace")).isPresent()) return;

        EntityDamageEvent.DamageCause cause = event.getCause();
        if (cause == EntityDamageEvent.DamageCause.FIRE || cause == EntityDamageEvent.DamageCause.FIRE_TICK || 
            cause == EntityDamageEvent.DamageCause.LAVA || cause == EntityDamageEvent.DamageCause.FALL || 
            cause == EntityDamageEvent.DamageCause.PROJECTILE) {
            event.setDamage(event.getDamage() * 1.10D);
        }
    }

    public void restoreAll() {
        for (UUID uuid : new HashSet<>(siphonModifiers.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (attr != null) {
                    AttributeModifier mod = siphonModifiers.remove(uuid);
                    if (mod != null) attr.removeModifier(mod);
                }
            }
        }
        siphonModifiers.clear();
        siphonExpiries.clear();
        for (Player player : plugin.getServer().getOnlinePlayers()) cleanupVampiricModifiers(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cleanupVampiricModifiers(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        cleanupVampiricModifiers(event.getEntity());
    }

    private void cleanupVampiricModifiers(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attr == null) return;
        for (AttributeModifier existing : java.util.List.copyOf(attr.getModifiers())) {
            NamespacedKey key = existing.getKey();
            if (!key.getNamespace().equals(plugin.getName().toLowerCase(java.util.Locale.ROOT))) continue;
            if (key.getKey().equals("vampiric_siphon") || key.getKey().startsWith("vampiric_siphoned_")) {
                attr.removeModifier(existing);
            }
        }
        UUID uuid = player.getUniqueId();
        siphonModifiers.remove(uuid);
        siphonExpiries.entrySet().removeIf(entry -> entry.getKey().contains(uuid.toString()));
    }
}
