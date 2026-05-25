package vn.nirussv.maceexclusive.item;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.WeaponConfig;

import java.util.ArrayList;
import java.util.List;

public final class ExclusiveItemFactory {

    private final PdcKeys keys;
    private final ConfigManager configManager;

    public ExclusiveItemFactory(ConfigManager configManager, PdcKeys keys) {
        this.configManager = configManager;
        this.keys = keys;
    }

    public ItemStack create(ExclusiveItemId id) {
        WeaponConfig weaponConfig = configManager.getWeaponConfig(id);

        ItemStack item = new ItemStack(weaponConfig == null ? id.material() : weaponConfig.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return item;
        }

        String name = weaponConfig == null ? id.fallbackName() : weaponConfig.name();
        meta.displayName(configManager.deserialize(name));

        List<String> lore = weaponConfig == null ? List.of() : weaponConfig.lore();
        if (!lore.isEmpty()) {
            List<Component> componentLore = new ArrayList<>();
            for (String line : lore) {
                componentLore.add(configManager.deserialize(line));
            }
            meta.lore(componentLore);
        } else if (id == ExclusiveItemId.CHRONOS_ANCHOR_SPEAR) {
            meta.lore(List.of(configManager.deserialize("&7A time-anchored spear.")));
        }

        if (weaponConfig != null && weaponConfig.customModelData() != null) {
            meta.setCustomModelData(weaponConfig.customModelData());
        }

        meta.getPersistentDataContainer().set(keys.itemId(), PersistentDataType.STRING, id.id());
        item.setItemMeta(meta);
        return item;
    }
}
