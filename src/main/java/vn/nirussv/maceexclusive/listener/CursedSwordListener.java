package vn.nirussv.maceexclusive.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import net.kyori.adventure.text.Component;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.curse.LockoutService;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.Random;

public final class CursedSwordListener implements Listener {

    private final LockoutService lockoutService;
    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;
    private final Random random = new Random();

    public CursedSwordListener(LockoutService lockoutService, ConfigManager configManager, ItemMatcher itemMatcher) {
        this.lockoutService = lockoutService;
        this.configManager = configManager;
        this.itemMatcher = itemMatcher;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCursedSwordHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType() == Material.AIR) return;

        if (itemMatcher.is(weapon, "cursed_sword")) {
            // Break the sword immediately on hit
            attacker.getInventory().setItemInMainHand(null);
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);

            // Heal attacker equal to damage dealt
            double damage = event.getFinalDamage();
            org.bukkit.attribute.AttributeInstance maxHealthAttr = attacker.getAttribute(org.bukkit.attribute.Attribute.GENERIC_MAX_HEALTH);
            double maxHealth = maxHealthAttr != null ? maxHealthAttr.getValue() : 20.0D;
            attacker.setHealth(Math.min(maxHealth, attacker.getHealth() + damage));

            // Roll for curse
            double roll = random.nextDouble();
            java.util.Map<String, String> placeholders = java.util.Map.of(
                "player", attacker.getName(),
                "victim", victim.getName(),
                "name", configManager.getItemConfig("cursed_sword") != null ? configManager.getItemConfig("cursed_sword").name() : "Cursed Sword"
            );

            if (roll < 0.50) { // 50% chance to curse victim
                lockoutService.applyCursed(victim.getUniqueId(), 1800L);
                victim.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER, 60, 2, false, false, true));
                victim.sendMessage(configManager.getMessage("cursed_sword.victim-cursed", placeholders));
                attacker.sendMessage(configManager.getMessage("cursed_sword.attacker-success", placeholders));
            } else { // 50% chance to curse attacker (backfire)
                lockoutService.applyCursed(attacker.getUniqueId(), 1800L);
                attacker.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.WITHER, 60, 2, false, false, true));
                attacker.sendMessage(configManager.getMessage("cursed_sword.attacker-backfire", placeholders));
                victim.sendMessage(configManager.getMessage("cursed_sword.victim-backfire", placeholders));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCursedPlayerDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        if (lockoutService.isCursed(victim)) {
            double roll = random.nextDouble();
            if (roll < 0.20) { // 20% chance to drop head
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta skullMeta = (SkullMeta) head.getItemMeta();
                if (skullMeta != null) {
                    skullMeta.setOwningPlayer(victim);
                    skullMeta.displayName(configManager.getMessage("cursed_sword.player-head-name", java.util.Map.of("player", victim.getName())));
                    head.setItemMeta(skullMeta);
                }
                victim.getWorld().dropItemNaturally(victim.getLocation(), head);

                // Remove curse immediately
                lockoutService.removeCursed(victim.getUniqueId());
                victim.sendMessage(configManager.getMessage("cursed_sword.curse-released"));
            }
        }
    }
}
