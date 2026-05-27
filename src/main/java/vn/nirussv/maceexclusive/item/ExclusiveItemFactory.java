package vn.nirussv.maceexclusive.item;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;

import java.util.ArrayList;
import java.util.List;

public final class ExclusiveItemFactory {

    private final PdcKeys keys;
    private final ConfigManager configManager;
    private final ItemRegistry itemRegistry;

    public ExclusiveItemFactory(ConfigManager configManager, PdcKeys keys, ItemRegistry itemRegistry) {
        this.configManager = configManager;
        this.keys = keys;
        this.itemRegistry = itemRegistry;
    }

    public ItemStack create(String id) {
        ItemDefinition definition = itemRegistry.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown item id: " + id));
        ItemConfig itemConfig = configManager.getItemConfig(definition.id());
        ItemStack item = new ItemStack(itemConfig == null ? definition.material() : itemConfig.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        String name = itemConfig == null ? definition.name() : itemConfig.name();
        meta.displayName(configManager.deserialize(name));
        List<String> lore = itemConfig == null ? List.of() : itemConfig.lore();
        if (!lore.isEmpty()) {
            List<Component> componentLore = new ArrayList<>();
            for (String line : lore) componentLore.add(configManager.deserialize(line));
            meta.lore(componentLore);
        }
        if (itemConfig != null && itemConfig.customModelData() != null) meta.setCustomModelData(itemConfig.customModelData());
        meta.getPersistentDataContainer().set(keys.itemId(), PersistentDataType.STRING, definition.id());
        item.setItemMeta(meta);
        return item;
    }
}
