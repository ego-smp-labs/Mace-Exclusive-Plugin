package vn.nirussv.maceexclusive.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record ItemConfig(
    String id,
    boolean enabled,
    Material material,
    String name,
    List<String> lore,
    Integer customModelData,
    RecipeConfig recipe,
    ConfigurationSection effects,
    boolean enchanted,
    String faction
) {

    public record RecipeConfig(boolean enabled, List<String> shape, Map<Character, String> ingredients) {
    }

    static ItemConfig fromSection(String id, ConfigurationSection section, Material fallbackMaterial, String fallbackName) {
        boolean enabled = section == null || section.getBoolean("enabled", true);
        Material material = resolveMaterial(section, fallbackMaterial);
        String name = section == null ? fallbackName : section.getString("name", fallbackName);
        List<String> lore = section == null ? List.of() : List.copyOf(section.getStringList("lore"));
        Integer customModelData = section != null && section.contains("custom-model-data")
            ? section.getInt("custom-model-data")
            : null;
        RecipeConfig recipe = readRecipe(section == null ? null : section.getConfigurationSection("recipe"));
        ConfigurationSection effects = section == null ? null : section.getConfigurationSection("effects");
        boolean enchanted = section != null && section.getBoolean("enchanted", false);
        String faction = section == null ? null : section.getString("faction");
        if (faction != null) faction = faction.trim().toLowerCase();
        if (faction != null && faction.isBlank()) faction = null;
        return new ItemConfig(id, enabled, material, name, lore, customModelData, recipe, effects, enchanted, faction);
    }

    private static Material resolveMaterial(ConfigurationSection section, Material fallbackMaterial) {
        if (section == null) {
            return fallbackMaterial;
        }
        String materialName = section.getString("material");
        Material configured = materialName == null ? null : Material.matchMaterial(materialName);
        return configured != null ? configured : fallbackMaterial;
    }

    private static RecipeConfig readRecipe(ConfigurationSection section) {
        if (section == null) {
            return new RecipeConfig(true, List.of(), Collections.emptyMap());
        }

        Map<Character, String> ingredients = new LinkedHashMap<>();
        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredients");
        if (ingredientSection != null) {
            for (String key : ingredientSection.getKeys(false)) {
                if (key.isBlank()) {
                    continue;
                }
                String ingredientName = ingredientSection.getString(key);
                if (ingredientName != null && !ingredientName.isBlank()) {
                    ingredients.put(key.charAt(0), ingredientName);
                }
            }
        }

        return new RecipeConfig(
            section.getBoolean("enabled", true),
            List.copyOf(section.getStringList("shape")),
            Collections.unmodifiableMap(ingredients)
        );
    }
}
