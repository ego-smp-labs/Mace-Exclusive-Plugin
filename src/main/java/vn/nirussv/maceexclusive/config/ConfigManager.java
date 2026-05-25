package vn.nirussv.maceexclusive.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private static final String ITEM_CONFIG_DIRECTORY = "items";
    private static final String YAML_EXTENSION = ".yml";

    private final MaceExclusivePlugin plugin;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private FileConfiguration langConfig;
    private PerformanceConfig performanceConfig;
    private final Map<String, ItemConfig> itemConfigs = new HashMap<>();
    private final Map<String, YamlConfiguration> itemFiles = new HashMap<>();
    private final Map<String, String> messageCache = new HashMap<>();

    public ConfigManager(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
        reload(); // Load text immediately
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
        
        InputStream defStream = plugin.getResource(fileName);
        if (defStream != null) {
            langConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
        }
        
        messageCache.clear();
    }

    private void ensureLanguageResource(String fileName) {
        File langFile = new File(plugin.getDataFolder(), fileName);
        if (!langFile.exists()) {
            plugin.saveResource(fileName, false);
        }
    }

    private void loadTypedConfigs() {
        performanceConfig = PerformanceConfig.fromConfig(plugin.getConfig());
        itemConfigs.clear();
        itemFiles.clear();
        ensureItemConfigDirectory();
        for (ExclusiveItemId itemId : ExclusiveItemId.values()) {
            ConfigurationSection section = getItemSection(itemId.id(), itemId.legacyConfigPath());
            itemConfigs.put(itemId.id(), ItemConfig.fromSection(itemId.id(), section, itemId.material(), itemId.fallbackName()));
        }
    }

    private void ensureItemConfigDirectory() {
        File itemDirectory = getItemConfigDirectory();
        if (itemDirectory.exists()) {
            if (!itemDirectory.isDirectory()) {
                plugin.getLogger().warning("Weapon config path exists but is not a directory: " + itemDirectory.getPath());
            }
            return;
        }

        if (!itemDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create weapon config directory: " + itemDirectory.getPath());
        }
    }

    private File getItemConfigDirectory() {
        return new File(plugin.getDataFolder(), ITEM_CONFIG_DIRECTORY);
    }

    private YamlConfiguration loadItemFile(String id) {
        if (itemFiles.containsKey(id)) {
            return itemFiles.get(id);
        }

        ensureItemResource(id);

        File file = new File(getItemConfigDirectory(), id + YAML_EXTENSION);
        if (!file.exists()) {
            return null;
        }

        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        String resourcePath = itemResourcePath(id);
        try (InputStream defaultStream = plugin.getResource(resourcePath)) {
            if (defaultStream != null) {
                configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load default weapon config for " + id + ": " + exception.getMessage());
        }

        itemFiles.put(id, configuration);
        return configuration;
    }

    private void ensureItemResource(String id) {
        File file = new File(getItemConfigDirectory(), id + YAML_EXTENSION);
        if (file.exists()) {
            return;
        }

        String resourcePath = itemResourcePath(id);
        if (!resourceExists(resourcePath)) {
            return;
        }

        plugin.saveResource(resourcePath, false);
    }

    private boolean resourceExists(String resourcePath) {
        try (InputStream inputStream = plugin.getResource(resourcePath)) {
            return inputStream != null;
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not inspect resource " + resourcePath + ": " + exception.getMessage());
            return false;
        }
    }

    private String itemResourcePath(String id) {
        return ITEM_CONFIG_DIRECTORY + "/" + id + YAML_EXTENSION;
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
        String prefix = getRawMessage("prefix");
        return toComponent(prefix + getRawMessage(key));
    }
    
    public Component getPrefixedMessage(String key, Map<String, String> placeholders) {
        String prefix = getRawMessage("prefix");
        String msg = getRawMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return toComponent(prefix + msg);
    }

    private Component toComponent(String legacyText) {
        return legacySerializer.deserialize(legacyText);
    }

    public Component deserialize(String legacyText) {
        return toComponent(legacyText == null ? "" : legacyText);
    }

    public ItemConfig getItemConfig(String id) {
        if (itemConfigs.isEmpty()) {
            loadTypedConfigs();
        }
        return itemConfigs.get(id);
    }

    public ItemConfig getItemConfig(ExclusiveItemId id) {
        return getItemConfig(id.id());
    }

    public PerformanceConfig getPerformanceConfig() {
        if (performanceConfig == null) {
            loadTypedConfigs();
        }
        return performanceConfig;
    }

    public ConfigurationSection getItemSection(String id, String legacyPath) {
        YamlConfiguration itemFile = loadItemFile(id);
        if (itemFile != null) {
            return itemFile;
        }
        return plugin.getConfig().getConfigurationSection(legacyPath);
    }

    public boolean isCraftingShiftClickPrevented() {
        return plugin.getConfig().getBoolean("crafting.prevent-shift-click", true);
    }

    public boolean shouldRemoveVanillaMaceRecipe() {
        return plugin.getConfig().getBoolean("crafting.remove-vanilla-mace-recipe", true);
    }

    public Material getForgeBlockMaterial() {
        String configured = plugin.getConfig().getString("forge.block", "LODESTONE");
        Material material = configured == null ? null : Material.matchMaterial(configured);
        return material == null ? Material.LODESTONE : material;
    }

    public long getForgeDurationSeconds() {
        return Math.max(1L, plugin.getConfig().getLong("forge.duration-seconds", 300L));
    }

    public float getForgeAbortExplosionPower() {
        return (float) Math.max(0.0D, plugin.getConfig().getDouble("forge.abort-explosion-power", 1.5D));
    }

    public boolean isItemEnabled(ExclusiveItemId id) {
        ItemConfig weaponConfig = getItemConfig(id);
        return weaponConfig == null || weaponConfig.enabled();
    }

    public boolean isSingletonItemsEnabled() {
        return plugin.getConfig().getBoolean("settings.singleton-weapons", true);
    }

    public boolean isSingletonItem(ExclusiveItemId id) {
        if (!isSingletonItemsEnabled()) {
            return false;
        }
        ConfigurationSection section = getItemSection(id.id(), id.legacyConfigPath());
        return section == null || section.getBoolean("singleton", true);
    }

    public boolean isStrictContainerBlock() {
        return plugin.getConfig().getBoolean("settings.strict-container-block", true);
    }

    public boolean getItemEffectBoolean(String itemId, String path, boolean fallback) {
        ConfigurationSection effects = getItemConfig(itemId) == null ? null : getItemConfig(itemId).effects();
        return effects == null ? fallback : effects.getBoolean(path, fallback);
    }

    public int getItemEffectInt(String itemId, String path, int fallback) {
        ConfigurationSection effects = getItemConfig(itemId) == null ? null : getItemConfig(itemId).effects();
        return effects == null ? fallback : effects.getInt(path, fallback);
    }

    public double getItemEffectDouble(String itemId, String path, double fallback) {
        ConfigurationSection effects = getItemConfig(itemId) == null ? null : getItemConfig(itemId).effects();
        return effects == null ? fallback : effects.getDouble(path, fallback);
    }

    public double getItemCurseDouble(String itemId, String path, double fallback) {
        ConfigurationSection itemSection = getItemSection(itemId, "items." + itemId);
        ConfigurationSection curse = itemSection == null ? null : itemSection.getConfigurationSection("curse");
        if (curse != null && curse.contains(path)) {
            return curse.getDouble(path, fallback);
        }

        ItemConfig weaponConfig = getItemConfig(itemId);
        ConfigurationSection effects = weaponConfig == null ? null : weaponConfig.effects();
        ConfigurationSection effectCurse = effects == null ? null : effects.getConfigurationSection("curse");
        return effectCurse == null ? fallback : effectCurse.getDouble(path, fallback);
    }

    public int getEnvironmentCurseIntervalTicks() {
        return Math.max(1, plugin.getConfig().getInt("performance.environment-curse-interval-ticks", 20));
    }
     
    public boolean isDropAllowed() {
        return plugin.getConfig().getBoolean("settings.allow-drop", true);
    }
    
    public boolean isStrictMode() {
        return plugin.getConfig().getBoolean("settings.strict-mode", false);
    }
    
    public boolean isVerboseLogging() {
        return plugin.getConfig().getBoolean("settings.verbose", false);
    }

    /**
     * If true AND strict-mode is enabled, trying to store a mace
     * will drop it at the player's feet instead of just cancelling.
     */
    public boolean isStrictModeDrop() {
        return plugin.getConfig().getBoolean("settings.strict-mode-drop", false);
    }

    /**
     * If true, prevents hoppers and droppers from moving registered maces.
     */
    public boolean isPreventHopperPickup() {
        return plugin.getConfig().getBoolean("settings.prevent-hopper-pickup", true);
    }
}
