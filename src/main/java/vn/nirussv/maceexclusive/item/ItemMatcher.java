package vn.nirussv.maceexclusive.item;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;

import java.util.Optional;

public final class ItemMatcher {

    private final PdcKeys keys;
    private final ConfigManager configManager;

    public ItemMatcher(PdcKeys keys, ConfigManager configManager) {
        this.keys = keys;
        this.configManager = configManager;
    }

    public Optional<String> match(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        String itemId = pdc.get(keys.itemId(), PersistentDataType.STRING);
        if (itemId == null || itemId.isBlank()) return Optional.empty();
        ItemConfig itemConfig = configManager.getItemConfig(itemId);
        Material expected = itemConfig == null ? null : itemConfig.material();
        if (expected != null && item.getType() != expected) return Optional.empty();
        return Optional.of(itemId.toLowerCase());
    }

    public Optional<String> matchCore(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return Optional.empty();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return Optional.empty();
        String coreId = meta.getPersistentDataContainer().get(keys.coreId(), PersistentDataType.STRING);
        return coreId == null || coreId.isBlank() ? Optional.empty() : Optional.of(coreId.toLowerCase());
    }

    public boolean is(ItemStack item, String id) {
        return match(item).filter(match -> match.equalsIgnoreCase(id)).isPresent();
    }

    public boolean isCore(ItemStack item, String id) {
        return matchCore(item).filter(match -> match.equalsIgnoreCase(id)).isPresent();
    }
}
