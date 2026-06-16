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
    EnchantPolicy enchantPolicy,
    boolean enchanted,
    String faction
) {

    public record RecipeConfig(
        boolean enabled,
        List<String> shape,
        Map<Character, String> ingredients,
        boolean specialCraftingEnabled,
        boolean glowAfterCraft,
        ConfigManager.CraftMessage startMessage,
        ConfigManager.CraftMessage completeMessage
    ) {
    }

    public record EnchantPolicy(
        String mode,
        List<String> allowed,
        List<String> denied
    ) {
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
        EnchantPolicy enchantPolicy = readEnchantPolicy(section == null ? null : section.getConfigurationSection("enchant-policy"));
        boolean enchanted = section != null && section.getBoolean("enchanted", false);
        String faction = section == null ? null : section.getString("faction");
        if (faction != null) faction = faction.trim().toLowerCase();
        if (faction != null && faction.isBlank()) faction = null;
        return new ItemConfig(id, enabled, material, name, lore, customModelData, recipe, effects, enchantPolicy, enchanted, faction);
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
            return new RecipeConfig(
                true,
                List.of(),
                Collections.emptyMap(),
                true,
                false,
                ConfigManager.CraftMessage.disabled("&7You begin crafting %name%..."),
                ConfigManager.CraftMessage.disabled("&aYou crafted %name%!")
            );
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
            Collections.unmodifiableMap(ingredients),
            section.getBoolean("special-crafting-enabled", true),
            section.getBoolean("glow-after-craft", false),
            readCraftMessage(section.getConfigurationSection("start-message"), "&7You begin crafting %name%..."),
            readCraftMessage(section.getConfigurationSection("complete-message"), "&aYou crafted %name%!")
        );
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

    private static EnchantPolicy readEnchantPolicy(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String mode = section.getString("mode", "allowlist").trim().toLowerCase();
        return new EnchantPolicy(
            mode.isBlank() ? "allowlist" : mode,
            List.copyOf(section.getStringList("allowed")),
            List.copyOf(section.getStringList("denied"))
        );
    }
}
