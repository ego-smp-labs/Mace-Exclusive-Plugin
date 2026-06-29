package vn.nirussv.maceexclusive.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.core.CoreConfig;
import vn.nirussv.maceexclusive.item.WeaponClass;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.JarURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class ConfigManager {

    public static final String WEAPON_CONFIG_DIRECTORY = ResourceBootstrap.WEAPON_CONFIG_DIRECTORY;
    public static final String UTILITY_ITEM_CONFIG_DIRECTORY = ResourceBootstrap.UTILITY_ITEM_CONFIG_DIRECTORY;
    public static final String CORE_CONFIG_DIRECTORY = ResourceBootstrap.CORE_CONFIG_DIRECTORY;
    public static final String LANG_DIRECTORY = ResourceBootstrap.LANG_DIRECTORY;
    private static final String YAML_EXTENSION = ".yml";
    private static final Set<Material> LEGACY_NETHERITE_FORGE_ITEMS = EnumSet.of(
        Material.NETHERITE_SWORD,
        Material.NETHERITE_PICKAXE,
        Material.NETHERITE_AXE,
        Material.NETHERITE_SHOVEL,
        Material.NETHERITE_HOE,
        Material.NETHERITE_HELMET,
        Material.NETHERITE_CHESTPLATE,
        Material.NETHERITE_LEGGINGS,
        Material.NETHERITE_BOOTS
    );

    private final MaceExclusivePlugin plugin;
    private final LegacyComponentSerializer legacySerializer = LegacyComponentSerializer.legacyAmpersand();
    private FileConfiguration itemsConfig;
    private FileConfiguration langConfig;
    private FileConfiguration discordConfig;
    private boolean discordConfigMissingBeforeLoad;
    private PerformanceConfig performanceConfig;
    private final Map<String, ItemConfig> itemConfigs = new LinkedHashMap<>();
    private final Map<String, YamlConfiguration> itemFiles = new HashMap<>();
    private final Map<String, File> itemConfigFiles = new LinkedHashMap<>();
    private final Map<String, String> itemDefaultResourcePaths = new HashMap<>();
    private final Map<String, String> messageCache = new HashMap<>();
    private final Map<String, Boolean> itemEffectBooleanCache = new HashMap<>();
    private final Map<String, Integer> itemEffectIntCache = new HashMap<>();
    private final Map<String, Double> itemEffectDoubleCache = new HashMap<>();
    private final Map<String, String> itemEffectStringCache = new HashMap<>();

    public ConfigManager(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        clearItemEffectCaches();
        plugin.reloadConfig();
        loadItemsConfig();
        loadLanguage();
        loadDiscord();
        loadTypedConfigs();
    }

    private void loadItemsConfig() {
        File itemsFile = new File(plugin.getDataFolder(), "items.yml");
        ResourceBootstrap.ensure(plugin, "items.yml");
        itemsConfig = YamlConfiguration.loadConfiguration(itemsFile);
        try (InputStream defStream = plugin.getResource("items.yml")) {
            if (defStream != null) {
                itemsConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load item defaults: " + exception.getMessage());
        }
    }

    private void loadDiscord() {
        File discordFile = new File(plugin.getDataFolder(), "discord.yml");
        discordConfigMissingBeforeLoad = !discordFile.exists();
        ResourceBootstrap.ensure(plugin, "discord.yml");
        discordConfig = YamlConfiguration.loadConfiguration(discordFile);
        try (InputStream defStream = plugin.getResource("discord.yml")) {
            if (defStream != null) {
                discordConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load Discord defaults: " + exception.getMessage());
        }
    }

    private void loadLanguage() {
        String configuredLanguage = plugin.getConfig().getString("settings.language", "en_US");
        String langCode = normalizeLanguage(configuredLanguage);
        if (!langCode.equals(configuredLanguage)) {
            plugin.getLogger().warning("Language setting '" + configuredLanguage + "' is a legacy alias or unsupported value; using '" + langCode + "'. Update settings.language when convenient.");
        }
        String fileName = LANG_DIRECTORY + "/" + langCode + YAML_EXTENSION;
        File preferredLangFile = new File(plugin.getDataFolder(), fileName);
        boolean preferredExistedBeforeBootstrap = preferredLangFile.exists();
        ensureLanguageResource("en_US");
        ensureLanguageResource("vi_VN");
        File langFile = new File(plugin.getDataFolder(), fileName);
        File legacyFile = legacyLanguageFile(configuredLanguage);
        if (!preferredExistedBeforeBootstrap && legacyFile != null && legacyFile.exists()) {
            plugin.getLogger().warning("Loading legacy language file " + legacyFile.getName() + "; new bundled language files are in lang/. User files are not deleted automatically.");
            langFile = legacyFile;
        }
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

    private String normalizeLanguage(String configuredLanguage) {
        String normalized = configuredLanguage == null ? "en_US" : configuredLanguage.trim();
        return switch (normalized.toLowerCase()) {
            case "vi", "vi_vn", "vi-vn" -> "vi_VN";
            case "en", "en_us", "en-us" -> "en_US";
            default -> {
                plugin.getLogger().warning("Unsupported language '" + normalized + "', falling back to en_US.");
                yield "en_US";
            }
        };
    }

    private File legacyLanguageFile(String configuredLanguage) {
        String normalized = configuredLanguage == null ? "" : configuredLanguage.trim().toLowerCase();
        if (normalized.startsWith("vi")) return new File(plugin.getDataFolder(), "lang_vi.yml");
        return new File(plugin.getDataFolder(), "lang_en.yml");
    }

    private void ensureLanguageResource(String locale) {
        ResourceBootstrap.ensure(plugin, LANG_DIRECTORY + "/" + locale + YAML_EXTENSION);
    }

    private void loadTypedConfigs() {
        performanceConfig = PerformanceConfig.fromConfig(plugin.getConfig());
        itemConfigs.clear();
        itemFiles.clear();
        itemConfigFiles.clear();
        itemDefaultResourcePaths.clear();
        clearItemEffectCaches();
        ensureBundledYamlCopied(WEAPON_CONFIG_DIRECTORY, ResourceBootstrap.WEAPON_RESOURCES);
        ensureBundledYamlCopied(UTILITY_ITEM_CONFIG_DIRECTORY, ResourceBootstrap.ITEM_RESOURCES);
        scanItemConfigDirectory(WEAPON_CONFIG_DIRECTORY, false);
        scanItemConfigDirectory(UTILITY_ITEM_CONFIG_DIRECTORY, true);
        for (String id : itemConfigFiles.keySet()) {
            ConfigurationSection section = loadItemFile(id);
            Material fallback = resolveWeaponFallbackMaterial(id);
            itemConfigs.put(id, ItemConfig.fromSection(id, section, fallback, "&f" + id));
        }
    }

    private void scanItemConfigDirectory(String directoryName, boolean legacyMixedDirectory) {
        File directory = new File(plugin.getDataFolder(), directoryName);
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create item config directory: " + directory.getPath());
            return;
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(YAML_EXTENSION));
        if (files == null) return;
        for (File file : files) {
            String id = file.getName().substring(0, file.getName().length() - YAML_EXTENSION.length()).toLowerCase();
            if (itemConfigFiles.containsKey(id)) {
                if (legacyMixedDirectory) {
                    plugin.getLogger().warning("Skipping legacy duplicate item config '" + id + "' from " + directoryName + "; new layout wins.");
                }
                continue;
            }
            itemConfigFiles.put(id, file);
            itemDefaultResourcePaths.put(id, directoryName + "/" + file.getName());
        }
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
        File file = itemConfigFiles.get(id);
        if (file == null) {
            return null;
        }
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration configuration = YamlConfiguration.loadConfiguration(file);
        try (InputStream defaultStream = plugin.getResource(itemDefaultResourcePaths.get(id))) {
            if (defaultStream != null) {
                configuration.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defaultStream, StandardCharsets.UTF_8)));
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not load default weapon config for " + id + ": " + exception.getMessage());
        }
        itemFiles.put(id, configuration);
        return configuration;
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
        java.util.Map<String, String> expanded = new java.util.HashMap<>(placeholders);
        if (expanded.containsKey("player") && !expanded.containsKey("user")) {
            expanded.put("user", expanded.get("player"));
        } else if (expanded.containsKey("user") && !expanded.containsKey("player")) {
            expanded.put("player", expanded.get("user"));
        }
        for (Map.Entry<String, String> entry : expanded.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return toComponent(msg);
    }

    public Component getPrefixedMessage(String key) {
        return toComponent(getRawMessage("prefix") + getRawMessage(key));
    }

    public Component getPrefixedMessage(String key, Map<String, String> placeholders) {
        String msg = getRawMessage(key);
        java.util.Map<String, String> expanded = new java.util.HashMap<>(placeholders);
        if (expanded.containsKey("player") && !expanded.containsKey("user")) {
            expanded.put("user", expanded.get("player"));
        } else if (expanded.containsKey("user") && !expanded.containsKey("player")) {
            expanded.put("player", expanded.get("user"));
        }
        for (Map.Entry<String, String> entry : expanded.entrySet()) {
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

    public boolean isWeaponItem(String id) {
        ItemConfig itemConfig = getItemConfig(id);
        return itemConfig != null && itemConfig.weaponClass().isWeapon();
    }

    public WeaponClass getWeaponClass(String id) {
        ItemConfig itemConfig = getItemConfig(id);
        return itemConfig == null ? WeaponClass.UNKNOWN : itemConfig.weaponClass();
    }

    public boolean isMaceItem(String id) {
        return getWeaponClass(id) == WeaponClass.MACE;
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

    public boolean isCraftingShiftClickPrevented() { return getItemsBoolean("items.crafting.prevent-shift-click", "crafting.prevent-shift-click", true); }
    public boolean shouldRemoveVanillaMaceRecipe() { return getItemsBoolean("items.crafting.remove-vanilla-mace-recipe", "crafting.remove-vanilla-mace-recipe", true); }
    public Material getForgeBlockMaterial() { Material material = Material.matchMaterial(plugin.getConfig().getString("forge.block", "LODESTONE")); return material == null ? Material.LODESTONE : material; }
    public long getForgeDurationSeconds() { return Math.max(1L, plugin.getConfig().getLong("forge.duration-seconds", 300L)); }
    public long getPreforgeChargeSeconds() { return Math.max(1L, plugin.getConfig().getLong("forge.preforge-charge-seconds", 3L)); }
    public float getPreforgeExplosionPower() { return (float) Math.max(0.0D, plugin.getConfig().getDouble("forge.preforge-explosion-power", 2.0D)); }
    public float getCompletionExplosionPower() { return (float) Math.max(0.0D, plugin.getConfig().getDouble("forge.completion-explosion-power", 4.0D)); }
    public float getForgeAbortExplosionPower() { return (float) Math.max(0.0D, plugin.getConfig().getDouble("forge.abort-explosion-power", 1.5D)); }
    public int getRitualAltarTransformCost() { return Math.max(1, plugin.getConfig().getInt("ritual-altar.transform-cost", 1)); }
    public double getRitualAltarWeaponFailChance() { return Math.max(0.0D, Math.min(1.0D, plugin.getConfig().getDouble("ritual-altar.weapon-fail-chance", 0.0D))); }
    public int getRitualAltarTransformSeconds() { return Math.max(1, plugin.getConfig().getInt("ritual-altar.transform-seconds", 4)); }
    public int getRitualAltarCraftSeconds() { return Math.max(1, plugin.getConfig().getInt("ritual-altar.ritual-core-table-craft-seconds", 10)); }
    public long getRitualAltarCooldownMillis() { return Math.max(0L, plugin.getConfig().getLong("ritual-altar.table-cooldown-seconds", 3600L)) * 1000L; }
    public boolean isRitualAltarSmokeEnabled() { return plugin.getConfig().getBoolean("ritual-altar.effects.smoke.enabled", true); }
    public String getRitualAltarSmokeParticle() { return plugin.getConfig().getString("ritual-altar.effects.smoke.particle", "SMOKE"); }
    public boolean isRitualAltarSoundEnabled() { return plugin.getConfig().getBoolean("ritual-altar.effects.sound.enabled", true); }
    public String getRitualAltarTransformSound() { return plugin.getConfig().getString("ritual-altar.effects.sound.name", "BLOCK_ENCHANTMENT_TABLE_USE"); }
    public boolean isRitualAltarLightningEnabled() { return plugin.getConfig().getBoolean("ritual-altar.effects.lightning", true); }
    public float getRitualAltarFailExplosionPower() { return (float) Math.max(0.0D, plugin.getConfig().getDouble("ritual-altar.fail-explosion-power", 1.5D)); }
    public int getRitualAltarCraftDamageMin() { return Math.max(0, plugin.getConfig().getInt("ritual-altar.craft-damage-min", 10)); }
    public int getRitualAltarCraftDamageMax() { return Math.max(getRitualAltarCraftDamageMin(), plugin.getConfig().getInt("ritual-altar.craft-damage-max", 18)); }
    public int getRitualAltarCraftFreezeSeconds() { return Math.max(0, plugin.getConfig().getInt("ritual-altar.craft-freeze-seconds", 5)); }
    public float getRitualAltarBreakExplosionPower() { return (float) Math.max(0.0D, plugin.getConfig().getDouble("ritual-altar.break-explosion-power", 1.5D)); }
    public double getCoreFailureChance() { return Math.max(0.0D, Math.min(1.0D, plugin.getConfig().getDouble("forge.core-failure-chance", 0.30D))); }
    public long getCoreCraftLockoutSeconds() { return Math.max(1L, plugin.getConfig().getLong("forge.craft-lockout-seconds", 900L)); }
    public boolean isTimedForgeEnabled() { return plugin.getConfig().getBoolean("timed-forge.enabled", plugin.getConfig().getBoolean("netherite-forge.enabled", true)); }
    public boolean isTimedForgeDefaultEnabled() { return plugin.getConfig().getBoolean("timed-forge.default-enabled", false); }
    public long getTimedForgeDefaultDurationSeconds() { return Math.max(1L, plugin.getConfig().getLong("timed-forge.default-duration-seconds", plugin.getConfig().getLong("netherite-forge.duration-seconds", 60L))); }
    public double getTimedForgeDefaultSuccessRate() { return clampSuccessRate(plugin.getConfig().getDouble("timed-forge.default-success-rate", 1.0D)); }
    public double getTimedForgeMaxDistance() { return Math.max(0.1D, plugin.getConfig().getDouble("timed-forge.max-distance", plugin.getConfig().getDouble("netherite-forge.max-distance", 4.0D))); }
    public boolean shouldFreezeTimedForgePlayer() { return plugin.getConfig().getBoolean("timed-forge.freeze-player", plugin.getConfig().getBoolean("netherite-forge.freeze-player", true)); }
    public boolean shouldRestoreWorkstationOnTimedForgeComplete() { return plugin.getConfig().getBoolean("timed-forge.restore-workstation-on-complete", plugin.getConfig().getBoolean("netherite-forge.restore-smithing-table-on-complete", true)); }
    public TimedForgeItemSettings getTimedForgeSettings(Material material) {
        if (material == null || material.isAir()) return new TimedForgeItemSettings(false, getTimedForgeDefaultDurationSeconds(), getTimedForgeDefaultSuccessRate());
        if (!plugin.getConfig().contains("timed-forge") && LEGACY_NETHERITE_FORGE_ITEMS.contains(material)) {
            return new TimedForgeItemSettings(isTimedForgeEnabled(), getTimedForgeDefaultDurationSeconds(), 1.0D);
        }
        String path = "timed-forge.items." + material.name();
        boolean enabled = plugin.getConfig().contains(path + ".enabled")
            ? plugin.getConfig().getBoolean(path + ".enabled")
            : isTimedForgeDefaultEnabled();
        long durationSeconds = Math.max(1L, plugin.getConfig().getLong(path + ".duration-seconds", getTimedForgeDefaultDurationSeconds()));
        double successRate = clampSuccessRate(plugin.getConfig().getDouble(path + ".success-rate", getTimedForgeDefaultSuccessRate()));
        return new TimedForgeItemSettings(enabled, durationSeconds, successRate);
    }

    private double clampSuccessRate(double successRate) {
        return Math.max(0.0D, Math.min(1.0D, successRate));
    }

    public boolean isNetheriteForgeEnabled() { return isTimedForgeEnabled(); }
    public long getNetheriteForgeDurationSeconds() { return getTimedForgeDefaultDurationSeconds(); }
    public double getNetheriteForgeMaxDistance() { return getTimedForgeMaxDistance(); }
    public boolean shouldFreezeNetheriteForgePlayer() { return shouldFreezeTimedForgePlayer(); }
    public boolean shouldRestoreSmithingTableOnNetheriteForgeComplete() { return shouldRestoreWorkstationOnTimedForgeComplete(); }

    public record TimedForgeItemSettings(boolean enabled, long durationSeconds, double successRate) {}

    public boolean isItemEnabled(String id) { ItemConfig weaponConfig = getItemConfig(id); return weaponConfig == null || weaponConfig.enabled(); }

    /**
     * Returns the configured faction for an item id, or null when none is declared.
     * Items without a faction are treated as belonging to their own isolated faction
     * by callers, so a null here means "no shared faction".
     */
    public String getFaction(String id) {
        ItemConfig weaponConfig = getItemConfig(id);
        return weaponConfig == null ? null : weaponConfig.faction();
    }
    public boolean isSingletonItemsEnabled() { return getItemsBoolean("items.singleton-weapons", "settings.singleton-weapons", true); }
    public boolean isSingletonItem(String id) { if (!isSingletonItemsEnabled()) return false; ConfigurationSection section = getItemSection(id, "items." + id); return section == null || section.getBoolean("singleton", true); }
    public boolean isStrictContainerBlock() { return getItemsBoolean("items.strict-container-block", "settings.strict-container-block", true); }

    public boolean isSpecialCraftingEnabledByDefault() { return getItemsBoolean("items.crafting.special-crafting-enabled", null, true); }
    public boolean isGlowAfterCraftEnabledByDefault() { return getItemsBoolean("items.crafting.default-glow-after-craft", null, false); }

    public CraftFeedback getItemCraftFeedback(String id) {
        ItemConfig itemConfig = getItemConfig(id);
        ItemConfig.RecipeConfig recipe = itemConfig == null ? null : itemConfig.recipe();
        ConfigurationSection itemSection = getItemSection(id, "items." + id);
        ConfigurationSection recipeSection = itemSection == null ? null : itemSection.getConfigurationSection("recipe");
        boolean special = recipeSection != null && recipeSection.contains("special-crafting-enabled", false)
            ? recipeSection.getBoolean("special-crafting-enabled")
            : isSpecialCraftingEnabledByDefault();
        boolean glow = recipeSection != null && recipeSection.contains("glow-after-craft", false)
            ? recipeSection.getBoolean("glow-after-craft")
            : isGlowAfterCraftEnabledByDefault();
        CraftMessage start = recipeSection != null && recipeSection.contains("start-message", false) && recipeSection.isConfigurationSection("start-message") && recipe != null
            ? recipe.startMessage()
            : getDefaultStartCraftMessage();
        CraftMessage complete = recipeSection != null && recipeSection.contains("complete-message", false) && recipeSection.isConfigurationSection("complete-message") && recipe != null
            ? recipe.completeMessage()
            : getDefaultCompleteCraftMessage();
        return new CraftFeedback(special, glow, start, complete);
    }

    public CraftFeedback getCoreCraftFeedback(CoreConfig core) {
        if (core == null) {
            return new CraftFeedback(isSpecialCraftingEnabledByDefault(), isGlowAfterCraftEnabledByDefault(), getDefaultStartCraftMessage(), getDefaultCompleteCraftMessage());
        }
        boolean special = core.specialCraftingConfigured() ? core.specialCraftingEnabled() : isSpecialCraftingEnabledByDefault();
        boolean glow = core.glowAfterCraftConfigured() ? core.glowAfterCraft() : isGlowAfterCraftEnabledByDefault();
        CraftMessage start = core.startMessageConfigured() ? core.startMessage() : getDefaultStartCraftMessage();
        CraftMessage complete = core.completeMessageConfigured() ? core.completeMessage() : getDefaultCompleteCraftMessage();
        return new CraftFeedback(special, glow, start, complete);
    }

    public void sendCraftStartMessage(Player player, String id, String displayName, CraftFeedback feedback) {
        sendCraftMessage(player, id, displayName, feedback == null ? null : feedback.startMessage());
    }

    public void sendCraftCompleteMessage(Player player, String id, String displayName, CraftFeedback feedback) {
        sendCraftMessage(player, id, displayName, feedback == null ? null : feedback.completeMessage());
    }

    private void sendCraftMessage(Player player, String id, String displayName, CraftMessage message) {
        if (player == null || message == null || !message.enabled()) return;
        String audience = normalizeAudience(message.audience());
        if ("none".equals(audience)) return;
        String text = message.text() == null ? "" : message.text();
        text = text.replace("%name%", displayName == null ? id : displayName)
            .replace("%player%", player.getName())
            .replace("%id%", id == null ? "" : id);
        Component component = toComponent(text);
        if ("broadcast".equals(audience)) {
            plugin.getServer().sendMessage(component);
        } else {
            player.sendMessage(component);
        }
    }

    private CraftMessage getDefaultStartCraftMessage() {
        ConfigurationSection section = itemsConfig == null ? null : itemsConfig.getConfigurationSection("items.crafting.default-start-message");
        return readCraftMessage(section, "&7You begin crafting %name%...");
    }

    private CraftMessage getDefaultCompleteCraftMessage() {
        ConfigurationSection section = itemsConfig == null ? null : itemsConfig.getConfigurationSection("items.crafting.default-complete-message");
        return readCraftMessage(section, "&aYou crafted %name%!");
    }

    private CraftMessage readCraftMessage(ConfigurationSection section, String fallbackText) {
        if (section == null) return CraftMessage.disabled(fallbackText);
        return new CraftMessage(section.getBoolean("enabled", false), section.getString("audience", "player"), section.getString("text", fallbackText));
    }

    private boolean getItemsBoolean(String path, String legacyPath, boolean fallback) {
        if (itemsConfig != null && itemsConfig.contains(path, true)) {
            return itemsConfig.getBoolean(path, fallback);
        }
        if (legacyPath != null) {
            return plugin.getConfig().getBoolean(legacyPath, fallback);
        }
        return fallback;
    }

    private String normalizeAudience(String audience) {
        if (audience == null) return "player";
        String normalized = audience.trim().toLowerCase();
        return switch (normalized) {
            case "broadcast", "none" -> normalized;
            default -> "player";
        };
    }

    public record CraftFeedback(boolean specialCraftingEnabled, boolean glowAfterCraft, CraftMessage startMessage, CraftMessage completeMessage) {}

    public record CraftMessage(boolean enabled, String audience, String text) {
        public static CraftMessage disabled(String text) {
            return new CraftMessage(false, "player", text);
        }
    }

    public boolean getItemEffectBoolean(String itemId, String path, boolean fallback) {
        String normalizedPath = normalizeEffectPath(path);
        return itemEffectBooleanCache.computeIfAbsent(itemEffectCacheKey(itemId, normalizedPath, Boolean.toString(fallback)), ignored -> {
            ItemConfig weaponConfig = getItemConfig(itemId);
            ConfigurationSection effects = weaponConfig == null ? null : weaponConfig.effects();
            return effects == null ? fallback : effects.getBoolean(normalizedPath, fallback);
        });
    }

    public int getItemEffectInt(String itemId, String path, int fallback) {
        String normalizedPath = normalizeEffectPath(path);
        return itemEffectIntCache.computeIfAbsent(itemEffectCacheKey(itemId, normalizedPath, Integer.toString(fallback)), ignored -> {
            ItemConfig weaponConfig = getItemConfig(itemId);
            ConfigurationSection effects = weaponConfig == null ? null : weaponConfig.effects();
            return effects == null ? fallback : effects.getInt(normalizedPath, fallback);
        });
    }

    public double getItemEffectDouble(String itemId, String path, double fallback) {
        String normalizedPath = normalizeEffectPath(path);
        return itemEffectDoubleCache.computeIfAbsent(itemEffectCacheKey(itemId, normalizedPath, Double.toString(fallback)), ignored -> {
            ItemConfig weaponConfig = getItemConfig(itemId);
            ConfigurationSection effects = weaponConfig == null ? null : weaponConfig.effects();
            return effects == null ? fallback : effects.getDouble(normalizedPath, fallback);
        });
    }

    public String getItemEffectString(String itemId, String path, String fallback) {
        String normalizedPath = normalizeEffectPath(path);
        return itemEffectStringCache.computeIfAbsent(itemEffectCacheKey(itemId, normalizedPath, fallback == null ? "" : fallback), ignored -> {
            ItemConfig weaponConfig = getItemConfig(itemId);
            ConfigurationSection effects = weaponConfig == null ? null : weaponConfig.effects();
            return effects == null ? fallback : effects.getString(normalizedPath, fallback);
        });
    }

    private String itemEffectCacheKey(String itemId, String normalizedPath, String fallback) {
        String id = itemId == null ? "" : itemId;
        return (itemId == null ? -1 : id.length()) + ":" + id + ":" + normalizedPath.length() + ":" + normalizedPath + ":" + fallback;
    }

    private void clearItemEffectCaches() {
        itemEffectBooleanCache.clear();
        itemEffectIntCache.clear();
        itemEffectDoubleCache.clear();
        itemEffectStringCache.clear();
    }

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

    public boolean getItemCurseBoolean(String itemId, String path, boolean fallback) {
        ConfigurationSection itemSection = getItemSection(itemId, "items." + itemId);
        ConfigurationSection curse = itemSection == null ? null : itemSection.getConfigurationSection("curse");
        if (curse != null && curse.contains(path)) return curse.getBoolean(path, fallback);
        ItemConfig weaponConfig = getItemConfig(itemId);
        ConfigurationSection effects = weaponConfig == null ? null : weaponConfig.effects();
        ConfigurationSection effectCurse = effects == null ? null : effects.getConfigurationSection("curse");
        return effectCurse == null ? fallback : effectCurse.getBoolean(path, fallback);
    }

    public int getEnvironmentCurseIntervalTicks() { return Math.max(1, plugin.getConfig().getInt("performance.environment-curse-interval-ticks", 20)); }

    public Component getItemMessage(String itemId, String path) { return getItemMessage(itemId, path, Map.of()); }
    public Component getItemMessage(String itemId, String path, Map<String, String> placeholders) {
        ConfigurationSection itemSection = getItemSection(itemId, "items." + itemId);
        if (itemSection == null || !itemSection.contains(path)) return null;
        String msg = itemSection.getString(path);
        if (msg == null || msg.isBlank()) return null;
        java.util.Map<String, String> expanded = new java.util.HashMap<>(placeholders);
        if (expanded.containsKey("player") && !expanded.containsKey("user")) {
            expanded.put("user", expanded.get("player"));
        } else if (expanded.containsKey("user") && !expanded.containsKey("player")) {
            expanded.put("player", expanded.get("user"));
        }
        for (Map.Entry<String, String> entry : expanded.entrySet()) msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        return toComponent(msg);
    }

    public boolean isDropAllowed() { return plugin.getConfig().getBoolean("settings.allow-drop", true); }
    public boolean isStrictMode() { return plugin.getConfig().getBoolean("settings.strict-mode", false); }
    public boolean isVerboseLogging() { return plugin.getConfig().getBoolean("settings.verbose", false); }
    public boolean isStrictModeDrop() { return plugin.getConfig().getBoolean("settings.strict-mode-drop", false); }
    public boolean isGraveCompatEnabled() { return plugin.getConfig().getBoolean("settings.grave-compat", true); }
    public boolean isPreventHopperPickup() { return plugin.getConfig().getBoolean("settings.prevent-hopper-pickup", true); }
    public boolean isGroundPickupRevealEnabled() { return plugin.getConfig().getBoolean("tracking.reveal-on-ground-pickup", true); }
    public int getTrackingDurationSeconds() { return Math.max(1, plugin.getConfig().getInt("tracking.duration-seconds", 300)); }
    public boolean isAbilityHudEnabled() { return plugin.getConfig().getBoolean("ability-hud.enabled", true); }
    public long getAbilityHudIntervalTicks() { return Math.max(1L, plugin.getConfig().getLong("ability-hud.interval-ticks", 10L)); }
    public boolean isCarryViolationCountdownEnabled() { return plugin.getConfig().getBoolean("carry-limit.enabled", true); }
    public int getCarryViolationCountdownSeconds() { return Math.max(1, plugin.getConfig().getInt("carry-limit.countdown-seconds", 3)); }
    public float getCarryViolationExplosionPower() { return (float) Math.max(0.0D, plugin.getConfig().getDouble("carry-limit.explosion-power", 2.0D)); }

    public boolean isDiscordWebhookEnabled() {
        return getDiscordBoolean("enabled", "discord.enabled", false);
    }

    public String getDiscordWebhookUrl() {
        return getDiscordString("webhook-url", "discord.webhook-url", "");
    }

    public String getDiscordEmbedTitle() {
        return getDiscordString("embed.title", null, "⚔️ ANCIENT ARTIFACT CLAIMED ⚔️");
    }

    public String getDiscordEmbedDescription() {
        return getDiscordString("embed.description", null, "**%player%** has %action% the legendary **%item%**!");
    }

    public String getDiscordEmbedFooter() {
        return getDiscordString("embed.footer", null, "Mace-Exclusive Integration Status");
    }

    public String getDiscordEmbedFieldName(String key) {
        String fallback = switch (key) {
            case "player-name" -> "👤 Player";
            case "method-name" -> "🔨 Method";
            case "location-name" -> "📍 Location";
            default -> key;
        };
        return getDiscordString("embed.fields." + key, null, fallback);
    }

    public String getDiscordEmbedLocationValue() {
        return getDiscordString("embed.fields.location-value", null, "World: `%world%`\nCoords: `%coords%`");
    }

    public int getDiscordColor(String itemId) {
        String normalizedItemId = itemId == null ? "" : itemId.toLowerCase();
        ConfigurationSection itemSection = getItemSection(normalizedItemId, "items." + normalizedItemId);
        if (itemSection != null && itemSection.contains("discord.color", true)) {
            return itemSection.getInt("discord.color");
        }
        String path = "colors." + normalizedItemId;
        if (isDiscordConfigLoaded() && discordConfig.contains(path, true)) {
            return discordConfig.getInt(path);
        }
        return isDiscordConfigLoaded() ? discordConfig.getInt("colors.default", 9807270) : 9807270;
    }

    public boolean isDiscordItemNotificationEnabled(String itemId) {
        String normalizedItemId = itemId == null ? "" : itemId.toLowerCase();
        ConfigurationSection itemSection = getItemSection(normalizedItemId, "items." + normalizedItemId);
        if (itemSection != null && itemSection.contains("discord.enabled", true)) {
            return itemSection.getBoolean("discord.enabled");
        }
        String path = "items." + normalizedItemId + ".enabled";
        if (isDiscordConfigLoaded() && discordConfig.contains(path, true)) {
            return discordConfig.getBoolean(path);
        }
        return getDiscordBoolean("default-item-enabled", null, true);
    }

    private boolean getDiscordBoolean(String path, String legacyPath, boolean fallback) {
        if (shouldUseLegacyDiscordPath(path, legacyPath)) {
            return plugin.getConfig().getBoolean(legacyPath, fallback);
        }
        return isDiscordConfigLoaded() ? discordConfig.getBoolean(path, fallback) : fallback;
    }

    private String getDiscordString(String path, String legacyPath, String fallback) {
        if (shouldUseLegacyDiscordPath(path, legacyPath)) {
            return plugin.getConfig().getString(legacyPath, fallback);
        }
        return isDiscordConfigLoaded() ? discordConfig.getString(path, fallback) : fallback;
    }

    private boolean shouldUseLegacyDiscordPath(String path, String legacyPath) {
        if (legacyPath == null || !plugin.getConfig().contains(legacyPath)) return false;
        return !isDiscordConfigLoaded() || discordConfigMissingBeforeLoad || !discordConfig.contains(path, true);
    }

    private boolean isDiscordConfigLoaded() {
        return discordConfig != null;
    }

    private void ensureBundledYamlCopied(String resourceDirectory, List<String> explicitResources) {
        File dir = new File(plugin.getDataFolder(), resourceDirectory);
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("Could not create config directory: " + dir.getPath());
        for (String resource : explicitResources) {
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
