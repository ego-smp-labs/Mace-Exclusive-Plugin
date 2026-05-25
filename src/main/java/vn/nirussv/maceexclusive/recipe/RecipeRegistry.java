package vn.nirussv.maceexclusive.recipe;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;
import vn.nirussv.maceexclusive.mace.MaceFactory;
import vn.nirussv.maceexclusive.mace.MaceType;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

public final class RecipeRegistry {

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final MaceFactory maceFactory;

    public RecipeRegistry(MaceExclusivePlugin plugin, ConfigManager configManager, MaceFactory maceFactory) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.maceFactory = maceFactory;
    }

    public void registerAll() {
        registerMaceRecipe(MaceType.POWER, "exclusive_mace_recipe");
        registerMaceRecipe(MaceType.CHAOS, "chaos_mace_recipe");
    }

    public void removeVanillaMaceRecipe() {
        Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe.getResult().getType() == Material.MACE
                && recipe instanceof ShapedRecipe shapedRecipe
                && shapedRecipe.getKey().getNamespace().equals("minecraft")) {
                iterator.remove();
                plugin.getLogger().info("Removed vanilla Mace recipe.");
            }
        }
    }

    private void registerMaceRecipe(MaceType type, String recipeKey) {
        ExclusiveItemId itemId = type.getExclusiveItemId();
        ItemConfig weaponConfig = configManager.getItemConfig(itemId);
        if (weaponConfig == null || !weaponConfig.enabled() || !weaponConfig.recipe().enabled()) {
            return;
        }

        NamespacedKey key = new NamespacedKey(plugin, recipeKey);
        ItemStack result = maceFactory.createUnawakenedWeapon(type);
        ShapedRecipe recipe = new ShapedRecipe(key, result);

        List<String> shape = weaponConfig.recipe().shape();
        if (shape.size() != 3) {
            applyFallbackRecipe(type, recipe);
        } else {
            recipe.shape(shape.toArray(new String[0]));
            for (Map.Entry<Character, Material> ingredient : weaponConfig.recipe().ingredients().entrySet()) {
                recipe.setIngredient(ingredient.getKey(), ingredient.getValue());
            }
        }

        plugin.getServer().addRecipe(recipe);
        plugin.getLogger().info("Registered " + type.name() + " awakening recipe: " + key);
    }

    private void applyFallbackRecipe(MaceType type, ShapedRecipe recipe) {
        if (type == MaceType.POWER) {
            recipe.shape(" H ", " I ", " B ");
            recipe.setIngredient('H', Material.HEAVY_CORE);
            recipe.setIngredient('I', Material.NETHERITE_INGOT);
            recipe.setIngredient('B', Material.BREEZE_ROD);
            return;
        }

        recipe.shape("NHN", "HMH", "NWN");
        recipe.setIngredient('N', Material.NETHER_STAR);
        recipe.setIngredient('H', Material.HEAVY_CORE);
        recipe.setIngredient('M', Material.MACE);
        recipe.setIngredient('W', Material.WITHER_ROSE);
    }
}
