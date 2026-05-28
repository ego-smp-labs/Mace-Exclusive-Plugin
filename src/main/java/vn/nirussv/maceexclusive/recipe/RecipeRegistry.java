package vn.nirussv.maceexclusive.recipe;

import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;
import vn.nirussv.maceexclusive.core.CoreConfig;
import vn.nirussv.maceexclusive.core.CoreItemFactory;
import vn.nirussv.maceexclusive.core.CoreRegistry;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ItemDefinition;
import vn.nirussv.maceexclusive.item.ItemRegistry;

import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class RecipeRegistry {

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final ItemRegistry itemRegistry;
    private final ExclusiveItemFactory itemFactory;
    private final CoreRegistry coreRegistry;
    private final CoreItemFactory coreItemFactory;

    public RecipeRegistry(MaceExclusivePlugin plugin, ConfigManager configManager, ItemRegistry itemRegistry, ExclusiveItemFactory itemFactory, CoreRegistry coreRegistry, CoreItemFactory coreItemFactory) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
        this.coreRegistry = coreRegistry;
        this.coreItemFactory = coreItemFactory;
    }

    public void registerAll() {
        removeManagedRecipes();
        for (ItemDefinition definition : itemRegistry.all()) registerWeaponRecipe(definition);
        for (CoreConfig core : coreRegistry.all()) registerCoreRecipe(core);
    }

    public void removeManagedRecipes() {
        String namespace = plugin.getName().toLowerCase(Locale.ROOT);
        Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (!(recipe instanceof Keyed keyed)) continue;
            if (keyed.getKey().getNamespace().equals(namespace)) iterator.remove();
        }
    }

    public void removeVanillaMaceRecipe() {
        Iterator<Recipe> iterator = plugin.getServer().recipeIterator();
        while (iterator.hasNext()) {
            Recipe recipe = iterator.next();
            if (recipe.getResult().getType() == Material.MACE && recipe instanceof ShapedRecipe shapedRecipe && shapedRecipe.getKey().getNamespace().equals("minecraft")) iterator.remove();
        }
    }

    private void registerWeaponRecipe(ItemDefinition definition) {
        ItemConfig itemConfig = configManager.getItemConfig(definition.id());
        if (itemConfig == null || !itemConfig.enabled() || !itemConfig.recipe().enabled() || itemConfig.recipe().shape().size() != 3) return;
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, definition.id() + "_recipe"), itemFactory.create(definition.id()));
        recipe.shape(itemConfig.recipe().shape().toArray(new String[0]));
        for (Map.Entry<Character, String> ingredient : itemConfig.recipe().ingredients().entrySet()) {
            RecipeChoice choice = resolveIngredientChoice(ingredient.getValue());
            if (choice != null) {
                recipe.setIngredient(ingredient.getKey(), choice);
            } else {
                plugin.getLogger().warning("Failed to resolve recipe ingredient for " + definition.id() + ": " + ingredient.getValue());
            }
        }
        plugin.getServer().addRecipe(recipe);
    }

    private void registerCoreRecipe(CoreConfig core) {
        if (!core.enabled() || !core.craftable() || core.shape().size() != 3) return;
        ItemStack result = "ruined_core".equals(core.id()) ? new ItemStack(Material.HEAVY_CORE) : coreItemFactory.create(core.id());
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, core.id() + "_recipe"), result);
        recipe.shape(core.shape().toArray(new String[0]));
        for (Map.Entry<Character, String> ingredient : core.ingredients().entrySet()) {
            if ("ruined_core".equals(core.id()) && ingredient.getKey() == 'R') {
                recipe.setIngredient('R', new RecipeChoice.ExactChoice(coreItemFactory.create("ruined_core")));
                continue;
            }
            RecipeChoice choice = resolveIngredientChoice(ingredient.getValue());
            if (choice != null) {
                recipe.setIngredient(ingredient.getKey(), choice);
            } else {
                plugin.getLogger().warning("Failed to resolve recipe ingredient for core " + core.id() + ": " + ingredient.getValue());
            }
        }
        plugin.getServer().addRecipe(recipe);
    }

    private RecipeChoice resolveIngredientChoice(String value) {
        Optional<CoreConfig> coreOpt = coreRegistry.find(value);
        if (coreOpt.isPresent()) {
            return new RecipeChoice.ExactChoice(coreItemFactory.create(coreOpt.get().id()));
        }
        Optional<ItemDefinition> itemOpt = itemRegistry.find(value);
        if (itemOpt.isPresent()) {
            return new RecipeChoice.ExactChoice(itemFactory.create(itemOpt.get().id()));
        }
        Material material = Material.matchMaterial(value);
        if (material != null) {
            return new RecipeChoice.MaterialChoice(material);
        }
        return null;
    }
}
