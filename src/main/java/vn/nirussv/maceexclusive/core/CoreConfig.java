package vn.nirussv.maceexclusive.core;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import vn.nirussv.maceexclusive.config.ConfigManager;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CoreConfig(
    String id,
    boolean enabled,
    Material material,
    String name,
    List<String> lore,
    Integer customModelData,
    boolean craftable,
    double failureChance,
    int xpCost,
    List<String> shape,
    Map<Character, String> ingredients,
    boolean specialCraftingEnabled,
    boolean specialCraftingConfigured,
    boolean glowAfterCraft,
    boolean glowAfterCraftConfigured,
    ConfigManager.CraftMessage startMessage,
    boolean startMessageConfigured,
    ConfigManager.CraftMessage completeMessage,
    boolean completeMessageConfigured
) {
    public static CoreConfig fromSection(String id, ConfigurationSection section) {
        Material material = resolveMaterial(section == null ? null : section.getString("material"));
        List<String> shape = section == null ? List.of() : List.copyOf(section.getStringList("recipe.shape"));
        ConfigurationSection recipe = section == null ? null : section.getConfigurationSection("recipe");
        return new CoreConfig(
            id,
            section == null || section.getBoolean("enabled", true),
            material,
            section == null ? "&7" + id : section.getString("name", "&7" + id),
            section == null ? List.of() : List.copyOf(section.getStringList("lore")),
            section != null && section.contains("custom-model-data") ? section.getInt("custom-model-data") : null,
            section != null && section.getBoolean("recipe.enabled", false),
            section == null ? 0.30D : Math.max(0.0D, Math.min(1.0D, section.getDouble("failure-chance", 0.30D))),
            section == null ? 0 : Math.max(0, section.getInt("xp-cost", 0)),
            shape,
            readIngredients(section == null ? null : section.getConfigurationSection("recipe.ingredients")),
            recipe == null || recipe.getBoolean("special-crafting-enabled", true),
            recipe != null && recipe.contains("special-crafting-enabled"),
            recipe != null && recipe.getBoolean("glow-after-craft", false),
            recipe != null && recipe.contains("glow-after-craft"),
            readCraftMessage(recipe == null ? null : recipe.getConfigurationSection("start-message"), "&7You begin crafting %name%..."),
            recipe != null && recipe.isConfigurationSection("start-message"),
            readCraftMessage(recipe == null ? null : recipe.getConfigurationSection("complete-message"), "&aYou crafted %name%!"),
            recipe != null && recipe.isConfigurationSection("complete-message")
        );
    }

    private static Material resolveMaterial(String name) {
        Material configured = name == null ? null : Material.matchMaterial(name);
        return configured == null ? Material.HEAVY_CORE : configured;
    }

    private static Map<Character, String> readIngredients(ConfigurationSection section) {
        if (section == null) return Collections.emptyMap();
        Map<Character, String> ingredients = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (key.isBlank()) continue;
            String val = section.getString(key, "");
            if (!val.isBlank()) ingredients.put(key.charAt(0), val);
        }
        return Collections.unmodifiableMap(ingredients);
    }

    private static ConfigManager.CraftMessage readCraftMessage(ConfigurationSection section, String fallbackText) {
        if (section == null) {
            return ConfigManager.CraftMessage.disabled(fallbackText);
        }
        return new ConfigManager.CraftMessage(
            section.getBoolean("enabled", false),
            section.getString("audience", "player"),
            section.getString("text", fallbackText)
        );
    }
}
