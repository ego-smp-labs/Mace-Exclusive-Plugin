package vn.nirussv.maceexclusive.core;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

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
    Map<Character, Material> ingredients
) {
    public static CoreConfig fromSection(String id, ConfigurationSection section) {
        Material material = resolveMaterial(section == null ? null : section.getString("material"));
        List<String> shape = section == null ? List.of() : List.copyOf(section.getStringList("recipe.shape"));
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
            readIngredients(section == null ? null : section.getConfigurationSection("recipe.ingredients"))
        );
    }

    private static Material resolveMaterial(String name) {
        Material configured = name == null ? null : Material.matchMaterial(name);
        return configured == null ? Material.HEAVY_CORE : configured;
    }

    private static Map<Character, Material> readIngredients(ConfigurationSection section) {
        if (section == null) return Collections.emptyMap();
        Map<Character, Material> ingredients = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) {
            if (key.isBlank()) continue;
            Material material = Material.matchMaterial(section.getString(key, ""));
            if (material != null) ingredients.put(key.charAt(0), material);
        }
        return Collections.unmodifiableMap(ingredients);
    }
}
