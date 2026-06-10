package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.task.InventoryShuffleTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class ChaosMaceAbility implements ActiveAbility, PassiveAbility {

    private static final String ID = "chaos_mace.rage";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final Random random = new Random();

    // In-memory states for rage points, rage state end times, charges left, and kills
    private final Map<UUID, Integer> ragePoints = new HashMap<>();
    private final Map<UUID, Long> rageEnds = new HashMap<>();
    private final Map<UUID, Integer> rageCharges = new HashMap<>();
    private final Map<UUID, Integer> rageKills = new HashMap<>();
    private final Map<UUID, Long> lunaticEnds = new HashMap<>();

    public ChaosMaceAbility(MaceExclusivePlugin plugin, ConfigManager configManager, CooldownService cooldownService) {
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
        return "chaos_mace";
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        Player player = context.player();
        UUID uuid = player.getUniqueId();
        
        // Cannot activate if already in rage state
        if (isRageActive(player)) {
            return false;
        }

        int points = ragePoints.getOrDefault(uuid, 0);
        if (points < 10) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cChưa đủ nộ! Tích nộ: " + points + "/10 (Tiêu diệt quái vật hoặc người chơi)."));
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

        // Activate Rage State
        long durationMillis = configManager.getItemEffectInt("chaos_mace", "effects.rage.duration", 120) * 1000L;
        long cooldownMillis = configManager.getItemEffectInt("chaos_mace", "effects.rage.cooldown", 300) * 1000L;

        rageEnds.put(uuid, System.currentTimeMillis() + durationMillis);
        rageCharges.put(uuid, 3);
        rageKills.put(uuid, 0);
        ragePoints.put(uuid, 0); // Clear points

        cooldownService.setCooldown(player, id(), cooldownMillis);

        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1.0f, 1.2f);
        net.kyori.adventure.text.Component msg = configManager.getItemMessage("chaos_mace", "messages.skill-rage-activate");
        if (msg != null) {
            player.sendMessage(msg);
        }

        // Schedule check for backfire at the end of the 2 minutes
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) {
                rageEnds.remove(uuid);
                rageCharges.remove(uuid);
                rageKills.remove(uuid);
                return;
            }
            Long ends = rageEnds.get(uuid);
            if (ends != null && System.currentTimeMillis() >= ends) {
                rageEnds.remove(uuid);
                rageCharges.remove(uuid);
                int kills = rageKills.getOrDefault(uuid, 0);
                rageKills.remove(uuid);

                if (kills == 0) {
                    player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&5[Chaos Mace] Phản vệ! Bạn không tiêu diệt được ai trong lúc nộ."));
                    int backfireDuration = configManager.getItemEffectInt("chaos_mace", "effects.curses.fail_kill_duration", 30);
                    inflictChaos(player, backfireDuration);
                }
            }
        }, durationMillis / 50L);
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        LivingEntity target = context.target();
        if (target == null) return;
        UUID uuid = attacker.getUniqueId();

        // 1. Check if first hold to apply curse
        checkFirstHold(attacker);

        // 2. Teleport target randomly
        teleportTarget(target);

        // 3. If in Rage State and has charges left, trigger skill
        if (isRageActive(attacker)) {
            int charges = rageCharges.getOrDefault(uuid, 0);
            if (charges > 0) {
                rageCharges.put(uuid, charges - 1);
                
                int skillDur = configManager.getItemEffectInt("chaos_mace", "effects.rage.skill_duration", 10);
                
                // Opponent gets Chaos effect for 10s
                if (target instanceof Player victim) {
                    inflictChaos(victim, skillDur);
                } else {
                    target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, skillDur * 20, 3));
                    target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, skillDur * 20, 0));
                }

                // Caster gets Lunatic buff for 10s (High health boost + damage boost)
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.HEALTH_BOOST, skillDur * 20, 4, false, false, true));
                attacker.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, skillDur * 20, 1, false, false, true));
                // Heal the extra health immediately
                double healAmount = 20.0;
                double maxHealth = attacker.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH) != null 
                    ? attacker.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH).getValue() 
                    : 20.0D;
                attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + healAmount));

                lunaticEnds.put(uuid, System.currentTimeMillis() + (skillDur * 1000L));

                net.kyori.adventure.text.Component msg = configManager.getItemMessage("chaos_mace", "messages.skill-lunatic-buff");
                if (msg != null) attacker.sendMessage(msg);
                
                net.kyori.adventure.text.Component infMsg = configManager.getItemMessage("chaos_mace", "messages.skill-chaos-inflicted");
                if (infMsg != null) attacker.sendMessage(infMsg);

                attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.8f, 1.5f);
            }
        }
    }

    @Override
    public void onDamaged(AbilityContext context, EntityDamageByEntityEvent event) {
        Player player = context.player();
        UUID uuid = player.getUniqueId();
        if (!(event.getDamager() instanceof Player attacker)) return;

        double chance = isLunaticActive(player) ? 1.0D : configManager.getItemEffectDouble("chaos_mace", "effects.passive.chaos_chance", 0.10D);
        if (random.nextDouble() < chance) {
            int duration = configManager.getItemEffectInt("chaos_mace", "effects.passive.chaos_duration", 1);
            inflictChaos(attacker, duration);
        }
    }

    @Override
    public void onDeath(AbilityContext context, EntityDeathEvent event) {
        LivingEntity deceased = event.getEntity();
        Player killer = deceased.getKiller();
        if (killer == null) return;
        
        // Ensure killer is holding Chaos Mace
        if (!"chaos_mace".equals(context.weaponId())) return;

        UUID uuid = killer.getUniqueId();

        if (isRageActive(killer)) {
            // Count kills during rage
            rageKills.put(uuid, rageKills.getOrDefault(uuid, 0) + 1);
        } else {
            // Roll for rage points
            double chance = (deceased instanceof Player) ? 1.0D : 0.20D;
            if (random.nextDouble() < chance) {
                int points = ragePoints.getOrDefault(uuid, 0);
                if (points < 10) {
                    points++;
                    ragePoints.put(uuid, points);
                    killer.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&5[Chaos Mace] Tích nộ: " + points + "/10"));
                    killer.playSound(killer.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 0.5f, 1.0f + (points * 0.1f));
                }
            }
        }
    }

    private boolean isRageActive(Player player) {
        Long ends = rageEnds.get(player.getUniqueId());
        return ends != null && System.currentTimeMillis() < ends;
    }

    private boolean isLunaticActive(Player player) {
        Long ends = lunaticEnds.get(player.getUniqueId());
        return ends != null && System.currentTimeMillis() < ends;
    }

    private void checkFirstHold(Player player) {
        NamespacedKey key = new NamespacedKey(plugin, "held_chaos_mace_before");
        if (!player.getPersistentDataContainer().has(key, PersistentDataType.BOOLEAN)) {
            player.getPersistentDataContainer().set(key, PersistentDataType.BOOLEAN, true);
            
            int firstHoldDuration = configManager.getItemEffectInt("chaos_mace", "effects.curses.first_hold_duration", 10);
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&5[Chaos Mace] Sức mạnh Hỗn Loạn đang ăn mòn tâm trí bạn!"));
            inflictChaos(player, firstHoldDuration);
        }
    }

    private void inflictChaos(Player player, int durationSeconds) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, durationSeconds * 20, 3));
        player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, durationSeconds * 20, 0));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, durationSeconds * 20, 0));

        new InventoryShuffleTask(player, durationSeconds, 10, configManager).runTaskTimer(plugin, 0L, 10L);
    }

    private void teleportTarget(LivingEntity target) {
        Location origin = target.getLocation();
        double radius = configManager.getItemEffectDouble("chaos_mace", "effects.teleport.range", 5.0D);
        double offsetX = (random.nextDouble() * 2 - 1) * radius;
        double offsetZ = (random.nextDouble() * 2 - 1) * radius;
        Location newLoc = origin.clone().add(offsetX, 0, offsetZ);
        
        // Find suitable block (ground level)
        newLoc.setY(origin.getWorld().getHighestBlockYAt(newLoc) + 1.0);
        target.teleport(newLoc);
    }
}
