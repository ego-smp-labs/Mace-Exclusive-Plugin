package vn.nirussv.maceexclusive.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ConfigManager {

    private static final String ITEM_CONFIG_DIRECTORY = "items";
    private static final String YAML_EXTENSION = ".yml";

    private final MaceExclusivePlugin plugin;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private FileConfiguration langConfig;
    private PerformanceConfig performanceConfig;
    private final Map<String, ItemConfig> itemConfigs = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> itemFiles = new HashMap<>();
    private final Map<String, String> messageCache = new HashMap<>();

    public ConfigManager(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        plugin.reloadConfig();
        loadLanguage();
        loadTypedConfigs();
    }

    private void loadLanguage() {
        ensureLanguageResource("lang_en.yml");
        ensureLanguageResource("lang_vi.yml");
        String langCode = plugin.getConfig().getString("settings.language", "en").toLowerCase();
        if (!langCode.equals("en") && !langCode.equals("vi")) {
            plugin.getLogger().warning("Unsupported language '" + langCode + "', falling back to en.");
            langCode = "en";
        }
        String fileName = "lang_" + langCode + ".yml";
        File langFile = new File(plugin.getDataFolder(), fileName);
        langConfig = YamlConfiguration.loadConfiguration(langFile);
        try (InputStream defStream = plugin.getResource(fileName)) {
            if (defStream != null) {
                langConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load language defaults: " + exception.getMessage());
        }
        messageCache.clear();
    }

    private void ensureLanguageResource(String fileName) {
        ResourceBootstrap.ensure(plugin, fileName);
    }

    private void loadTypedConfigs() {
        performanceConfig = PerformanceConfig.fromConfig(plugin.getConfig());
        itemConfigs.clear();
        itemFiles.clear();
        ensureBundledYamlCopied(ITEM_CONFIG_DIRECTORY);
        File directory = getItemConfigDirectory();
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create weapon config directory: " + directory.getPath());
            return;
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(YAML_EXTENSION));
        if (files == null) {
            return;
        }
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - YAML_EXTENSION.length()).toLowerCase();
            ConfigurationSection section = loadItemFile(id);
            Material fallback = resolveWeaponFallbackMaterial(id);
            itemConfigs.put(id, ItemConfig.fromSection(id, section, fallback, "&f" + id));
        }
    }

    private File getItemConfigDirectory() {
        return new File(plugin.getDataFolder(), ITEM_CONFIG_DIRECTORY);
    }

    private Material resolveWeaponFallbackMaterial(String id) {
        if (id.contains("spear")) {
            Material spear = Material.matchMaterial("NETHERITE_SPEAR");
            if (spear != null) return spear;
            plugin.getLogger().warning("NETHERITE_SPEAR is not available in this Paper API; using MACE fallback for " + id + ".");
        }
        return Material.MACE;
    }

    public YamlConfiguration loadItemFile(String id) {
        if (itemFiles.containsKey(id)) {
            return itemFiles.get(id);
        }
        File file = new File(getItemConfigDirectory(), id + YAML_EXTENSION);
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        try (InputStream defaultStream = plugin.getResource(itemResourcePath(id))) {
            if (defaultStream != null) {
                configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load default weapon config for " + id + ": " + exception.getMessage());
        }
        itemFiles.put(id, configuration);
        return configuration;
    }

    private String itemResourcePath(String id) {
        return ITEM_CONFIG_DIRECTORY + "/" + id + YAML_EXTENSION;
    }

    public Collection<ItemConfig> getItemConfigs() {
        if (itemConfigs.isEmpty()) {
            loadTypedConfigs();
        }
        return List.copyOf(itemConfigs.values());
    }

    public String getRawMessage(String key) {
        if (langConfig == null) loadLanguage();
        return messageCache.computeIfAbsent(key, missingKey -> langConfig.getString(missingKey, "Missing key: " + missingKey));
    }

    public Component getMessage(String key) {
        return toComponent(getRawMessage(key));
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        String msg = getRawMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return toComponent(msg);
    }

    public Component getPrefixedMessage(String key) {
        return toComponent(getRawMessage("prefix") + getRawMessage(key));
    }

    public Component getPrefixedMessage(String key, Map<String, String> placeholders) {
        String msg = getRawMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return toComponent(getRawMessage("prefix") + msg);
    }

    private Component toComponent(String legacyText) {
        return legacySerializer.deserialize(legacyText == null ? "" : legacyText);
    }

    public Component deserialize(String legacyText) {
        return toComponent(legacyText == null ? "" : legacyText);
    }

    public ItemConfig getItemConfig(String id) {
        if (itemConfigs.isEmpty()) {
            loadTypedConfigs();
        }
        return itemConfigs.get(id == null ? null : id.toLowerCase());
    }

    public PerformanceConfig getPerformanceConfig() {
        if (performanceConfig == null) {
            loadTypedConfigs();
        }
        return performanceConfig;
    }

    public ConfigurationSection getItemSection(String id, String ignoredLegacyPath) {
        ConfigurationSection section = loadItemFile(id);
        return section != null ? section : plugin.getConfig().getConfigurationSection("items." + id);
    }

    public boolean isCraftingShiftClickPrevented() { return plugin.getConfig().getBoolean("crafting.prevent-shift-click", true); }
    public boolean shouldRemoveVanillaMaceRecipe() { return plugin.getConfig().getBoolean("crafting.remove-vanilla-mace-recipe", true); }
    public Material getForgeBlockMaterial() { Material material = Material.matchMaterial(plugin.getConfig().getString("forge.block", "LODESTONE")); return material == null ? Material.LODESTONE : material; }
    public long getForgeDurationSeconds() { return Math.max(1L, plugin.getConfig().getLong("forge.duration-seconds", 300L)); }
    public long getPreforgeChargeSeconds() { return Math.max(1L, plugin.getConfig().getLong("forge.preforge-charge-seconds", 3L)); }
    public float getPreforgeExplosionPower() { return (float) Math.max(0.0D, plugin.getConfig().getDouble("forge.preforge-explosion-power", 2.0D)); }
    public float getCompletionExplosionPower() { return (float) Math.max(0.0D, plugin.getConfig().getDouble("forge.completion-explosion-power", 4.0D)); }
    public float getForgeAbortExplosionPower() { return (float) Math.max(0.0D, plugin.getConfig().getDouble("forge.abort-explosion-power", 1.5D)); }
    public double getCoreFailureChance() { return Math.max(0.0D, Math.min(1.0D, plugin.getConfig().getDouble("forge.core-failure-chance", 0.30D))); }
    public long getCoreCraftLockoutSeconds() { return Math.max(1L, plugin.getConfig().getLong("forge.craft-lockout-seconds", 900L)); }

    public boolean isItemEnabled(String id) { ItemConfig weaponConfig = getItemConfig(id); return weaponConfig == null || weaponConfig.enabled(); }
    public boolean isSingletonItemsEnabled() { return plugin.getConfig().getBoolean("settings.singleton-weapons", true); }
    public boolean isSingletonItem(String id) { if (!isSingletonItemsEnabled()) return false; ConfigurationSection section = getItemSection(id, "items." + id); return section == null || section.getBoolean("singleton", true); }
    public boolean isStrictContainerBlock() { return plugin.getConfig().getBoolean("settings.strict-container-block", true); }

    public boolean getItemEffectBoolean(String itemId, String path, boolean fallback) { ConfigurationSection effects = getItemConfig(itemId) == null ? null : getItemConfig(itemId).effects(); return effects == null ? fallback : effects.getBoolean(normalizeEffectPath(path), fallback); }
    public int getItemEffectInt(String itemId, String path, int fallback) { ConfigurationSection effects = getItemConfig(itemId) == null ? null : getItemConfig(itemId).effects(); return effects == null ? fallback : effects.getInt(normalizeEffectPath(path), fallback); }
    public double getItemEffectDouble(String itemId, String path, double fallback) { ConfigurationSection effects = getItemConfig(itemId) == null ? null : getItemConfig(itemId).effects(); return effects == null ? fallback : effects.getDouble(normalizeEffectPath(path), fallback); }

    private String normalizeEffectPath(String path) {
        if (path == null) return "";
        return path.startsWith("effects.") ? path.substring("effects.".length()) : path;
    }

    public double getItemCurseDouble(String itemId, String path, double fallback) {
        ConfigurationSection itemSection = getItemSection(itemId, "items." + itemId);
        ConfigurationSection curse = itemSection == null ? null : itemSection.getConfigurationSection("curse");
        if (curse != null && curse.contains(path)) return curse.getDouble(path, fallback);
        ItemConfig weaponConfig = getItemConfig(itemId);
        ConfigurationSection effects = weaponConfig == null ? null : weaponConfig.effects();
        ConfigurationSection effectCurse = effects == null ? null : effects.getConfigurationSection("curse");
        return effectCurse == null ? fallback : effectCurse.getDouble(path, fallback);
    }

    public int getEnvironmentCurseIntervalTicks() { return Math.max(1, plugin.getConfig().getInt("performance.environment-curse-interval-ticks", 20)); }

    public Component getItemMessage(String itemId, String path) { return getItemMessage(itemId, path, Map.of()); }
    public Component getItemMessage(String itemId, String path, Map<String, String> placeholders) {
        ConfigurationSection itemSection = getItemSection(itemId, "items." + itemId);
        if (itemSection == null || !itemSection.contains(path)) return null;
        String msg = itemSection.getString(path);
        if (msg == null || msg.isBlank()) return null;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        return toComponent(msg);
    }

    public boolean isDropAllowed() { return plugin.getConfig().getBoolean("settings.allow-drop", true); }
    public boolean isStrictMode() { return plugin.getConfig().getBoolean("settings.strict-mode", false); }
    public boolean isVerboseLogging() { return plugin.getConfig().getBoolean("settings.verbose", false); }
    public boolean isStrictModeDrop() { return plugin.getConfig().getBoolean("settings.strict-mode-drop", false); }
    public boolean isPreventHopperPickup() { return plugin.getConfig().getBoolean("settings.prevent-hopper-pickup", true); }

    private void ensureBundledYamlCopied(String resourceDirectory) {
        File dir = new File(plugin.getDataFolder(), resourceDirectory);
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("Could not create config directory: " + dir.getPath());
        List<String> explicit = ITEM_CONFIG_DIRECTORY.equals(resourceDirectory) ? ResourceBootstrap.ITEM_RESOURCES : List.of();
        for (String resource : explicit) {
            ResourceBootstrap.ensure(plugin, resource);
        }
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
            File[] files = dir.listFiles((ignored, name) -> name.endsWith(YAML_EXTENSION));
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
                    if (!entry.isDirectory() && entry.getName().startsWith(prefix) && entry.getName().endsWith(YAML_EXTENSION)) result.add(entry.getName());
                }
                return result;
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan bundled " + resourceDirectory + " configs: " + exception.getMessage());
            return List.of();
        }
    }
}
