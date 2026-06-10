package vn.nirussv.maceexclusive.item;

import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class ItemRegistry {

    private static final String ITEMS_DIR = "items";
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
        ensureBundledYamlCopied(ITEMS_DIR);
        File directory = new File(plugin.getDataFolder(), ITEMS_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create item config directory: " + directory.getPath());
            return;
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(YML));
        if (files == null) return;
        for (File file : files) register(file);
        plugin.getLogger().info("Loaded " + definitions.size() + " dynamic exclusive item definitions.");
    }

    public Optional<ItemDefinition> find(String id) {
        if (id == null || id.isBlank()) return Optional.empty();
        return Optional.ofNullable(definitions.get(id.toLowerCase()));
    }

    public Collection<ItemDefinition> all() { return Collections.unmodifiableCollection(definitions.values()); }
    public List<String> ids() { return new ArrayList<>(definitions.keySet()); }

    private void register(File file) {
        String id = file.getName().substring(0, file.getName().length() - YML.length()).toLowerCase();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ItemConfig config = configManager.getItemConfig(id);
        Material material = config == null ? inferFallbackMaterial(id) : config.material();
        String name = yaml.getString("name", id);
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

    private void ensureBundledYamlCopied(String resourceDirectory) {
        for (String resource : bundledYamlResources(resourceDirectory)) {
            File target = new File(plugin.getDataFolder(), resource);
            if (!target.exists()) plugin.saveResource(resource, false);
        }
    }

    private List<String> bundledYamlResources(String resourceDirectory) {
        URL url = plugin.getClass().getClassLoader().getResource(resourceDirectory);
        if (url == null) return List.of();
        if ("file".equals(url.getProtocol())) {
            File dir = new File(url.getPath());
            File[] files = dir.listFiles((ignored, name) -> name.endsWith(YML));
            if (files == null) return List.of();
            List<String> result = new ArrayList<>();
            for (File file : files) result.add(resourceDirectory + "/" + file.getName());
            return result;
        }
        if (!"jar".equals(url.getProtocol())) return List.of();
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                List<String> result = new ArrayList<>();
                String prefix = resourceDirectory + "/";
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().startsWith(prefix) && entry.getName().endsWith(YML)) result.add(entry.getName());
                }
                return result;
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan bundled " + resourceDirectory + " configs: " + exception.getMessage());
            return List.of();
        }
    }

    public YamlConfiguration loadWithDefaults(String id) {
        File file = new File(new File(plugin.getDataFolder(), ITEMS_DIR), id + YML);
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        try (InputStream defaultStream = plugin.getResource(ITEMS_DIR + "/" + id + YML)) {
            if (defaultStream != null) configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load defaults for item " + id + ": " + exception.getMessage());
        }
        return configuration;
    }
}
