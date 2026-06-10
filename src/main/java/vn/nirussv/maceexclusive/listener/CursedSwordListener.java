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
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
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

            // Roll for curse
            double roll = random.nextDouble();
            if (roll < 0.50) { // 50% chance to curse victim
                lockoutService.applyCursed(victim.getUniqueId(), configManager.getCoreCraftLockoutSeconds());
                victim.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cBạn đã bị nguyền rủa từ thanh kiếm Cursed Sword!"));
                attacker.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aThành công! Bạn đã nguyền rủa đối thủ."));
            } else { // 50% chance to curse attacker
                lockoutService.applyCursed(attacker.getUniqueId(), configManager.getCoreCraftLockoutSeconds());
                attacker.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&cThất bại! Sức mạnh nguyền rủa phản phệ, bạn đã bị nguyền rủa!"));
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
                    skullMeta.displayName(Component.text("Đầu của " + victim.getName()));
                    head.setItemMeta(skullMeta);
                }
                victim.getWorld().dropItemNaturally(victim.getLocation(), head);

                // Remove curse immediately
                lockoutService.removeCursed(victim.getUniqueId());
                victim.sendMessage(LegacyComponentSerializer.legacyAmpersand().deserialize("&aLực nguyền rủa đã được giải thoát và đầu của bạn đã rơi ra!"));
            }
        }
    }
}
