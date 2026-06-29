package vn.nirussv.maceexclusive.item;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;
import vn.nirussv.maceexclusive.config.ResourceBootstrap;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class ItemRegistry {

    private static final String WEAPONS_DIR = ResourceBootstrap.WEAPON_CONFIG_DIRECTORY;
    private static final String ITEMS_DIR = ResourceBootstrap.UTILITY_ITEM_CONFIG_DIRECTORY;
    private static final String YML = ".yml";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final Map<String, ItemDefinition> definitions = new LinkedHashMap<>();

    public ItemRegistry(MaceExclusivePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void reload() {
        definitions.clear();
        configManager.getItemConfigs().forEach(this::register);
        plugin.getLogger().info("Loaded " + definitions.size() + " dynamic exclusive item definitions.");
    }

    public Optional<ItemDefinition> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(definitions.get(id.toLowerCase()));
    }

    public Collection<ItemDefinition> all() { return Collections.unmodifiableCollection(definitions.values()); }
    public List<String> ids() { return new ArrayList<>(definitions.keySet()); }

    private void register(ItemConfig config) {
        String id = config.id().toLowerCase();
        Material material = config == null ? inferFallbackMaterial(id) : config.material();
        String name = config == null ? id : config.name();
        definitions.put(id, new ItemDefinition(id, material, name));
    }

    private Material inferFallbackMaterial(String id) {
        if (id.contains("spear")) {
            Material spear = Material.matchMaterial("NETHERITE_SPEAR");
            if (spear != null) return spear;
            plugin.getLogger().warning("NETHERITE_SPEAR is not available in this Paper API; using MACE fallback for " + id + ".");
        }
        if (id.contains("head")) {
            return Material.PLAYER_HEAD;
        }
        if (id.contains("sword")) {
            return Material.NETHERITE_SWORD;
        }
        return Material.MACE;
    }

    public YamlConfiguration loadWithDefaults(String id) {
        File weaponsFile = new File(new File(plugin.getDataFolder(), WEAPONS_DIR), id + YML);
        File itemsFile = new File(new File(plugin.getDataFolder(), ITEMS_DIR), id + YML);
        File file = weaponsFile.exists() ? weaponsFile : itemsFile;
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String defaultPath = weaponsFile.exists() ? WEAPONS_DIR + "/" + id + YML : ITEMS_DIR + "/" + id + YML;
        try (InputStream defaultStream = plugin.getResource(defaultPath)) {
            if (defaultStream != null) configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load defaults for item " + id + ": " + exception.getMessage());
        }
        return configuration;
    }
}
