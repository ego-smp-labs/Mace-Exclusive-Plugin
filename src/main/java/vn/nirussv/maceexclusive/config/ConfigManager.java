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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class ConfigManager {

    private final MaceExclusivePlugin plugin;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private FileConfiguration langConfig;
    private PerformanceConfig performanceConfig;
    private final Map<String, WeaponConfig> weaponConfigs = new HashMap<>();
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
        weaponConfigs.clear();
        for (ExclusiveItemId itemId : ExclusiveItemId.values()) {
            ConfigurationSection section = getWeaponSection(itemId.id(), itemId.legacyConfigPath());
            weaponConfigs.put(itemId.id(), WeaponConfig.fromSection(itemId.id(), section, itemId.material(), itemId.fallbackName()));
        }
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

    public WeaponConfig getWeaponConfig(String id) {
        if (weaponConfigs.isEmpty()) {
            loadTypedConfigs();
        }
        return weaponConfigs.get(id);
    }

    public WeaponConfig getWeaponConfig(ExclusiveItemId id) {
        return getWeaponConfig(id.id());
    }

    public PerformanceConfig getPerformanceConfig() {
        if (performanceConfig == null) {
            loadTypedConfigs();
        }
        return performanceConfig;
    }

    public ConfigurationSection getWeaponSection(String id, String legacyPath) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("weapons." + id);
        if (section != null) {
            return section;
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

    public boolean isWeaponEnabled(ExclusiveItemId id) {
        WeaponConfig weaponConfig = getWeaponConfig(id);
        return weaponConfig == null || weaponConfig.enabled();
    }

    public boolean isSingletonWeaponsEnabled() {
        return plugin.getConfig().getBoolean("settings.singleton-weapons", true);
    }

    public boolean isSingletonWeapon(ExclusiveItemId id) {
        if (!isSingletonWeaponsEnabled()) {
            return false;
        }
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("weapons." + id.id());
        if (section == null) {
            section = getWeaponSection(id.id(), id.legacyConfigPath());
        }
        return section == null || section.getBoolean("singleton", true);
    }

    public boolean isStrictContainerBlock() {
        return plugin.getConfig().getBoolean("settings.strict-container-block", true);
    }

    public boolean getWeaponEffectBoolean(String weaponId, String path, boolean fallback) {
        ConfigurationSection effects = getWeaponConfig(weaponId) == null ? null : getWeaponConfig(weaponId).effects();
        return effects == null ? fallback : effects.getBoolean(path, fallback);
    }

    public int getWeaponEffectInt(String weaponId, String path, int fallback) {
        ConfigurationSection effects = getWeaponConfig(weaponId) == null ? null : getWeaponConfig(weaponId).effects();
        return effects == null ? fallback : effects.getInt(path, fallback);
    }

    public double getWeaponEffectDouble(String weaponId, String path, double fallback) {
        ConfigurationSection effects = getWeaponConfig(weaponId) == null ? null : getWeaponConfig(weaponId).effects();
        return effects == null ? fallback : effects.getDouble(path, fallback);
    }

    public double getWeaponCurseDouble(String weaponId, String path, double fallback) {
        ConfigurationSection curse = plugin.getConfig().getConfigurationSection("weapons." + weaponId + ".curse");
        if (curse != null && curse.contains(path)) {
            return curse.getDouble(path, fallback);
        }

        WeaponConfig weaponConfig = getWeaponConfig(weaponId);
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
