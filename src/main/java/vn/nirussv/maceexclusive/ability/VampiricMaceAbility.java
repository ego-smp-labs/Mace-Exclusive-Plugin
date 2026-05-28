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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;

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
            player.sendMessage("§cVui lòng nhìn thẳng vào một mục tiêu để hút máu!");
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

        long durationSeconds = configManager.getItemEffectInt("vampiric_mace", "effects.siphon.duration", 90);
        long cooldownSeconds = configManager.getItemEffectInt("vampiric_mace", "cooldowns.siphon", 75);

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

        NamespacedKey modifierKey = new NamespacedKey(plugin, "vampiric_siphon_" + uuid.toString());
        AttributeModifier modifier = new AttributeModifier(modifierKey, newModifierAmount, AttributeModifier.Operation.ADD_NUMBER);
        
        if (maxHealthAttr != null) {
            maxHealthAttr.addModifier(modifier);
            siphonModifiers.put(uuid, modifier);
        }

        // Apply penalty to victim
        if (target instanceof Player victim) {
            AttributeInstance victimMaxHealth = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (victimMaxHealth != null) {
                NamespacedKey victimKey = new NamespacedKey(plugin, "vampiric_siphoned_" + uuid.toString());
                AttributeModifier penaltyMod = new AttributeModifier(victimKey, -2.0D, AttributeModifier.Operation.ADD_NUMBER);
                victimMaxHealth.addModifier(penaltyMod);
                
                // Restore victim HP later
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    if (victim.isOnline()) {
                        AttributeInstance attr = victim.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                        if (attr != null) attr.removeModifier(penaltyMod);
                    }
                }, durationSeconds * 20L);
            }
        } else {
            target.damage(6.0D, player);
        }

        // Heal caster
        double healAmount = configManager.getItemEffectDouble("vampiric_mace", "effects.siphon.immediate_heal", 6.0D);
        player.setHealth(Math.min(player.getMaxHealth(), player.getHealth() + healAmount));

        cooldownService.setCooldown(player, id(), cooldownSeconds * 1000L);

        player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PHANTOM_BITE, 1.0f, 0.7f);
        net.kyori.adventure.text.Component msg = configManager.getItemMessage("vampiric_mace", "messages.skill-siphon");
        if (msg != null) player.sendMessage(msg);

        // Schedule remove caster boost
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
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

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        LivingEntity target = context.target();
        if (target == null) return;

        // Lifesteal: 12% damage
        double percent = configManager.getItemEffectDouble("vampiric_mace", "effects.lifesteal.percent", 0.12D);
        double maxHeal = configManager.getItemEffectDouble("vampiric_mace", "effects.lifesteal.max_heal", 2.0D);
        double damage = event.getFinalDamage();
        double heal = Math.min(maxHeal, damage * percent);
        
        attacker.setHealth(Math.min(attacker.getMaxHealth(), attacker.getHealth() + heal));
        attacker.getWorld().spawnParticle(org.bukkit.Particle.DAMAGE_INDICATOR, target.getLocation().add(0.0, 1.0, 0.0), 3);
        attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_HURT_SWEET_BERRY_BUSH, 0.6f, 1.5f);

        // Low health damage boost: +20% damage if health < 30%
        if (attacker.getHealth() / attacker.getMaxHealth() < 0.30) {
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
    }
}
