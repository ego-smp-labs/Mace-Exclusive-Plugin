package vn.nirussv.maceexclusive.listener;

import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.item.WeaponClass;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class EnchantPolicyListener implements Listener {

    private static final String ALL = "all";

    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;

    public EnchantPolicyListener(ConfigManager configManager, ItemMatcher itemMatcher) {
        this.configManager = configManager;
        this.itemMatcher = itemMatcher;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchantItem(EnchantItemEvent event) {
        ItemStack item = event.getItem();
        Optional<String> itemId = itemMatcher.match(item);
        if (itemId.isEmpty()) {
            return;
        }

        for (Enchantment enchantment : event.getEnchantsToAdd().keySet()) {
            if (!isAllowed(itemId.get(), enchantment)) {
                event.setCancelled(true);
                notifyBlocked(event.getEnchanter(), enchantment);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        ItemStack base = event.getInventory().getFirstItem();
        Optional<String> itemId = itemMatcher.match(base);
        if (itemId.isEmpty()) {
            return;
        }

        ItemStack result = event.getResult();
        if (result == null) {
            return;
        }

        Map<Enchantment, Integer> baseEnchants = base == null ? Map.of() : base.getEnchantments();
        for (Map.Entry<Enchantment, Integer> entry : result.getEnchantments().entrySet()) {
            Enchantment enchantment = entry.getKey();
            int baseLevel = baseEnchants.getOrDefault(enchantment, 0);
            if (entry.getValue() > baseLevel && !isAllowed(itemId.get(), enchantment)) {
                event.setResult(null);
                notifyViewers(event, enchantment);
                return;
            }
        }
    }

    private boolean isAllowed(String itemId, Enchantment enchantment) {
        ItemConfig itemConfig = configManager.getItemConfig(itemId);
        ItemConfig.EnchantPolicy policy = itemConfig == null ? null : itemConfig.enchantPolicy();
        String enchantmentName = normalize(enchantment);

        if (policy == null) {
            return defaultAllowed(itemId).contains(enchantmentName);
        }

        Set<String> denied = normalize(policy.denied());
        if (denied.contains(ALL) || denied.contains(enchantmentName)) {
            return false;
        }

        String mode = policy.mode() == null ? "allowlist" : policy.mode().toLowerCase(Locale.ROOT);
        if ("deny-all".equals(mode)) {
            return false;
        }
        if ("denylist".equals(mode)) {
            return true;
        }

        Set<String> allowed = normalize(policy.allowed());
        return allowed.contains(enchantmentName);
    }

    private Set<String> defaultAllowed(String itemId) {
        Set<String> allowed = new HashSet<>();
        allowed.add("mending");
        allowed.add("unbreaking");
        allowed.add("durability");
        ItemConfig itemConfig = configManager.getItemConfig(itemId);
        String materialName = itemConfig == null || itemConfig.material() == null ? "" : itemConfig.material().name();
        if (configManager.getWeaponClass(itemId) == WeaponClass.SPEAR || materialName.contains("SPEAR") || materialName.equals("TRIDENT")) {
            allowed.add("sharpness");
            allowed.add("damage_all");
        }
        return allowed;
    }

    private Set<String> normalize(Iterable<String> names) {
        Set<String> normalized = new HashSet<>();
        if (names == null) {
            return normalized;
        }
        for (String name : names) {
            if (name != null && !name.isBlank()) {
                normalized.add(normalize(name));
            }
        }
        return normalized;
    }

    private String normalize(Enchantment enchantment) {
        return enchantment.getKey().getKey().toLowerCase(Locale.ROOT);
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private void notifyViewers(PrepareAnvilEvent event, Enchantment enchantment) {
        for (HumanEntity viewer : event.getViewers()) {
            if (viewer instanceof Player player) {
                notifyBlocked(player, enchantment);
            }
        }
    }

    private void notifyBlocked(Player player, Enchantment enchantment) {
        player.sendActionBar(configManager.getMessage("ability.enchant-blocked", Map.of("enchant", normalize(enchantment))));
    }
}
