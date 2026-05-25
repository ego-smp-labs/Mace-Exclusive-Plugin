package vn.nirussv.maceexclusive.config;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record WeaponConfig(
    String id,
    boolean enabled,
    Material material,
    String name,
    List<String> lore,
    Integer customModelData,
    RecipeConfig recipe,
    ConfigurationSection effects
) {

    public record RecipeConfig(boolean enabled, List<String> shape, Map<Character, Material> ingredients) {
    }

    static WeaponConfig fromSection(String id, ConfigurationSection section, Material fallbackMaterial, String fallbackName) {
        boolean enabled = section == null || section.getBoolean("enabled", true);
        Material material = resolveMaterial(section, fallbackMaterial);
        String name = section == null ? fallbackName : section.getString("name", fallbackName);
        List<String> lore = section == null ? List.of() : List.copyOf(section.getStringList("lore"));
        Integer customModelData = section != null && section.contains("custom-model-data")
            ? section.getInt("custom-model-data")
            : null;
        RecipeConfig recipe = readRecipe(section == null ? null : section.getConfigurationSection("recipe"));
        ConfigurationSection effects = section == null ? null : section.getConfigurationSection("effects");
        return new WeaponConfig(id, enabled, material, name, lore, customModelData, recipe, effects);
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

        Map<Character, Material> ingredients = new LinkedHashMap<>();
        ConfigurationSection ingredientSection = section.getConfigurationSection("ingredients");
        if (ingredientSection != null) {
            for (String key : ingredientSection.getKeys(false)) {
                if (key.isBlank()) {
                    continue;
                }
                String materialName = ingredientSection.getString(key);
                Material material = materialName == null ? null : Material.matchMaterial(materialName);
                if (material != null) {
                    ingredients.put(key.charAt(0), material);
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
