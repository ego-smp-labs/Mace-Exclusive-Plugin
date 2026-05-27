package vn.nirussv.maceexclusive.core;

import net.kyori.adventure.text.Component;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.PdcKeys;

import java.util.ArrayList;
import java.util.List;

public final class CoreItemFactory {

    private final CoreRegistry coreRegistry;
    private final ConfigManager configManager;
    private final PdcKeys keys;

    public CoreItemFactory(CoreRegistry coreRegistry, ConfigManager configManager, PdcKeys keys) {
        this.coreRegistry = coreRegistry;
        this.configManager = configManager;
        this.keys = keys;
    }

    public ItemStack create(String id) {
        CoreConfig core = coreRegistry.find(id).orElseThrow(() -> new IllegalArgumentException("Unknown core id: " + id));
        ItemStack item = new ItemStack(core.material());
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(configManager.deserialize(core.name()));
        if (!core.lore().isEmpty()) {
            List<Component> lore = new ArrayList<>();
            for (String line : core.lore()) lore.add(configManager.deserialize(line));
            meta.lore(lore);
        }
        if (core.customModelData() != null) meta.setCustomModelData(core.customModelData());
        meta.getPersistentDataContainer().set(keys.coreId(), PersistentDataType.STRING, core.id());
        item.setItemMeta(meta);
        return item;
    }
}
