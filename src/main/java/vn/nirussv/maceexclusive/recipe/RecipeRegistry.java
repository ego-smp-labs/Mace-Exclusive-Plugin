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

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.entity.Player;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class RecipeRegistry implements Listener {

    private static final String RITUAL_CORE_RECYCLE_ID = "ritual_core_recycle";
    private static final List<String> RITUAL_CORE_RECYCLE_SHAPE = List.of("OOO", "ORO", "OOO");
    private static final Map<Character, String> RITUAL_CORE_RECYCLE_INGREDIENTS = Map.of(
        'O', "OBSIDIAN",
        'R', "ruined_core"
    );

    private final Map<UUID, Long> lastCraftTimes = new HashMap<>();

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final ItemRegistry itemRegistry;
    private final ExclusiveItemFactory itemFactory;
    private final CoreRegistry coreRegistry;
    private final CoreItemFactory coreItemFactory;
    private final ItemMatcher itemMatcher;

    public RecipeRegistry(MaceExclusivePlugin plugin, ConfigManager configManager, ItemRegistry itemRegistry, ExclusiveItemFactory itemFactory, CoreRegistry coreRegistry, CoreItemFactory coreItemFactory, ItemMatcher itemMatcher) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemRegistry = itemRegistry;
        this.itemFactory = itemFactory;
        this.coreRegistry = coreRegistry;
        this.coreItemFactory = coreItemFactory;
        this.itemMatcher = itemMatcher;
    }

    public void registerAll() {
        removeManagedRecipes();
        for (ItemDefinition definition : itemRegistry.all()) registerWeaponRecipe(definition);
        for (CoreConfig core : coreRegistry.all()) registerCoreRecipe(core);
        registerRuinedCoreRecycleRecipe();
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

    private void registerRuinedCoreRecycleRecipe() {
        ShapedRecipe recipe = new ShapedRecipe(new NamespacedKey(plugin, RITUAL_CORE_RECYCLE_ID + "_recipe"), coreItemFactory.create("ritual_core"));
        recipe.shape(RITUAL_CORE_RECYCLE_SHAPE.toArray(new String[0]));
        recipe.setIngredient('O', new RecipeChoice.MaterialChoice(Material.OBSIDIAN));
        recipe.setIngredient('R', new RecipeChoice.ExactChoice(coreItemFactory.create("ruined_core")));
        plugin.getServer().addRecipe(recipe);
    }

    private static final class IngredientRequirement {
        private final String itemKey;
        private final int amount;

        public IngredientRequirement(String itemKey, int amount) {
            this.itemKey = itemKey;
            this.amount = amount;
        }

        public String itemKey() { return itemKey; }
        public int amount() { return amount; }
    }

    private IngredientRequirement parseRequirement(String value) {
        if (value == null) {
            return new IngredientRequirement("", 1);
        }
        int colonIndex = value.indexOf(':');
        if (colonIndex == -1) {
            return new IngredientRequirement(value.trim(), 1);
        }
        String key = value.substring(0, colonIndex).trim();
        int amount = 1;
        try {
            amount = Integer.parseInt(value.substring(colonIndex + 1).trim());
        } catch (NumberFormatException ignored) {}
        return new IngredientRequirement(key, Math.max(1, amount));
    }

    private RecipeChoice resolveIngredientChoice(String value) {
        IngredientRequirement req = parseRequirement(value);
        String cleanValue = req.itemKey();
        if ("ANY_HEAD".equalsIgnoreCase(cleanValue)) {
            return new RecipeChoice.MaterialChoice(
                Material.SKELETON_SKULL,
                Material.WITHER_SKELETON_SKULL,
                Material.PLAYER_HEAD,
                Material.ZOMBIE_HEAD,
                Material.CREEPER_HEAD,
                Material.PIGLIN_HEAD,
                Material.DRAGON_HEAD
            );
        }
        if ("ANY_POISON_POTION".equalsIgnoreCase(cleanValue)) {
            return new RecipeChoice.MaterialChoice(
                Material.POTION,
                Material.SPLASH_POTION,
                Material.LINGERING_POTION
            );
        }
        Optional<CoreConfig> coreOpt = coreRegistry.find(cleanValue);
        if (coreOpt.isPresent()) {
            return new RecipeChoice.ExactChoice(coreItemFactory.create(coreOpt.get().id()));
        }
        Optional<ItemDefinition> itemOpt = itemRegistry.find(cleanValue);
        if (itemOpt.isPresent()) {
            return new RecipeChoice.ExactChoice(itemFactory.create(itemOpt.get().id()));
        }
        Material material = Material.matchMaterial(cleanValue);
        if (material != null) {
            if (material == Material.HEAVY_CORE) {
                return new RecipeChoice.ExactChoice(new ItemStack(Material.HEAVY_CORE));
            }
            if (material == Material.PLAYER_HEAD) {
                return new RecipeChoice.ExactChoice(new ItemStack(Material.PLAYER_HEAD));
            }
            return new RecipeChoice.MaterialChoice(material);
        }
        return null;
    }

    public int getRequiredAmount(Recipe recipe, int matrixIndex) {
        String customId = getCustomRecipeId(recipe);
        if (customId == null) return 0;

        List<String> shape = null;
        Map<Character, String> ingredients = null;

        ItemConfig itemConfig = configManager.getItemConfig(customId);
        if (itemConfig != null && itemConfig.recipe().enabled()) {
            shape = itemConfig.recipe().shape();
            ingredients = itemConfig.recipe().ingredients();
        } else {
            Optional<CoreConfig> coreOpt = coreRegistry.find(customId);
            if (coreOpt.isPresent() && coreOpt.get().craftable()) {
                shape = coreOpt.get().shape();
                ingredients = coreOpt.get().ingredients();
            }
        }
        if (RITUAL_CORE_RECYCLE_ID.equals(customId)) {
            shape = RITUAL_CORE_RECYCLE_SHAPE;
            ingredients = RITUAL_CORE_RECYCLE_INGREDIENTS;
        }

        if (shape == null || ingredients == null) return 0;
        return getRequiredAmount(shape, ingredients, matrixIndex);
    }

    public int getRequiredAmount(List<String> shape, Map<Character, String> ingredients, int matrixIndex) {
        int row = matrixIndex / 3;
        int col = matrixIndex % 3;
        if (row >= shape.size()) return 0;
        String rowStr = shape.get(row);
        if (col >= rowStr.length()) return 0;
        char symbol = rowStr.charAt(col);
        if (symbol == ' ') return 0;
        String ingredientVal = ingredients.get(symbol);
        if (ingredientVal == null) return 0;
        return parseRequirement(ingredientVal).amount();
    }

    private void validateCraftAmounts(PrepareItemCraftEvent event, List<String> shape, Map<Character, String> ingredients) {
        ItemStack[] matrix = event.getInventory().getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            int required = getRequiredAmount(shape, ingredients, i);
            if (required <= 1) continue;
            ItemStack item = matrix[i];
            if (item == null || item.getAmount() < required) {
                event.getInventory().setResult(null);
                return;
            }
        }
    }

    private String getCustomRecipeId(Recipe recipe) {
        if (!(recipe instanceof Keyed keyed)) return null;
        String namespace = plugin.getName().toLowerCase(Locale.ROOT);
        if (!keyed.getKey().getNamespace().equals(namespace)) return null;
        String key = keyed.getKey().getKey();
        if (key.endsWith("_recipe")) {
            return key.substring(0, key.length() - "_recipe".length());
        }
        return null;
    }

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        Recipe recipe = event.getRecipe();
        if (recipe == null) return;

        boolean isCursedSwordRecipe = false;
        if (recipe instanceof Keyed keyed) {
            String namespace = plugin.getName().toLowerCase(Locale.ROOT);
            if (keyed.getKey().getNamespace().equals(namespace) && keyed.getKey().getKey().equals("cursed_sword_recipe")) {
                isCursedSwordRecipe = true;
            }
        }

        ItemStack[] matrix = event.getInventory().getMatrix();

        if (isCursedSwordRecipe) {
            boolean hasPoison = false;
            for (ItemStack item : matrix) {
                if (item == null) continue;
                Material type = item.getType();
                if (type == Material.POTION || type == Material.SPLASH_POTION || type == Material.LINGERING_POTION) {
                    if (item.hasItemMeta() && item.getItemMeta() instanceof org.bukkit.inventory.meta.PotionMeta potionMeta) {
                        org.bukkit.potion.PotionType potionType = potionMeta.getBasePotionType();
                        if (potionType != null && potionType.name().contains("POISON")) {
                            hasPoison = true;
                        }
                    }
                }
            }
            if (!hasPoison) {
                event.getInventory().setResult(null);
                return;
            }
        }

        for (ItemStack item : matrix) {
            if (item == null || item.getType() == Material.AIR) continue;

            boolean isCustom = itemMatcher.match(item).isPresent() || itemMatcher.matchCore(item).isPresent();
            if (isCustom) {
                if (recipe instanceof Keyed keyed) {
                    String namespace = plugin.getName().toLowerCase(Locale.ROOT);
                    if (!keyed.getKey().getNamespace().equals(namespace)) {
                        event.getInventory().setResult(null);
                        return;
                    }
                } else {
                    event.getInventory().setResult(null);
                    return;
                }
            }
        }

        // Custom amounts validation
        String customId = getCustomRecipeId(recipe);
        if (customId != null) {
            List<String> shape = null;
            Map<Character, String> ingredients = null;

            ItemConfig itemConfig = configManager.getItemConfig(customId);
            if (itemConfig != null && itemConfig.recipe().enabled()) {
                shape = itemConfig.recipe().shape();
                ingredients = itemConfig.recipe().ingredients();
            } else {
                Optional<CoreConfig> coreOpt = coreRegistry.find(customId);
                if (coreOpt.isPresent() && coreOpt.get().craftable()) {
                    shape = coreOpt.get().shape();
                    ingredients = coreOpt.get().ingredients();
                }
            }
            if (RITUAL_CORE_RECYCLE_ID.equals(customId)) {
                shape = RITUAL_CORE_RECYCLE_SHAPE;
                ingredients = RITUAL_CORE_RECYCLE_INGREDIENTS;
            }

            if (shape != null && ingredients != null) {
                validateCraftAmounts(event, shape, ingredients);
            }
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftItem(CraftItemEvent event) {
        Recipe recipe = event.getRecipe();
        String customId = getCustomRecipeId(recipe);
        if (customId == null) return;

        if (!(event.getWhoClicked() instanceof Player player)) return;

        if (!RITUAL_CORE_RECYCLE_ID.equals(customId)) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("ritual-altar.use-table"));
            return;
        }

        // Rate limit crafting (500ms cooldown) to prevent auto-clicker duplication exploits
        long now = System.currentTimeMillis();
        long lastCraft = lastCraftTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastCraft < 500) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("core.take-one-at-a-time"));
            return;
        }
        lastCraftTimes.put(player.getUniqueId(), now);

        List<String> shape = null;
        Map<Character, String> ingredients = null;

        ItemConfig itemConfig = configManager.getItemConfig(customId);
        if (itemConfig != null && itemConfig.recipe().enabled()) {
            shape = itemConfig.recipe().shape();
            ingredients = itemConfig.recipe().ingredients();
        } else {
            Optional<CoreConfig> coreOpt = coreRegistry.find(customId);
            if (coreOpt.isPresent() && coreOpt.get().craftable()) {
                shape = coreOpt.get().shape();
                ingredients = coreOpt.get().ingredients();
            }
        }
        if (RITUAL_CORE_RECYCLE_ID.equals(customId)) {
            shape = RITUAL_CORE_RECYCLE_SHAPE;
            ingredients = RITUAL_CORE_RECYCLE_INGREDIENTS;
        }

        if (shape == null || ingredients == null) return;

        // Block all unsafe bulk crafting types (shift-click, swap offhand, etc.) for ALL custom items
        if (configManager.isCraftingShiftClickPrevented() && isUnsafeBulkCraft(event)) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("core.take-one-at-a-time"));
            return;
        }

        // Deduct extra amounts (required - 1)
        ItemStack[] matrix = event.getInventory().getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType().isAir()) continue;
            int required = getRequiredAmount(shape, ingredients, i);
            if (required > 1) {
                item.setAmount(item.getAmount() - (required - 1));
                if (item.getAmount() <= 0) {
                    matrix[i] = null;
                }
            }
        }
        event.getInventory().setMatrix(matrix);
    }

    private boolean isUnsafeBulkCraft(CraftItemEvent event) {
        ClickType click = event.getClick();
        return event.isShiftClick()
            || click == ClickType.NUMBER_KEY
            || click == ClickType.DOUBLE_CLICK
            || click == ClickType.SWAP_OFFHAND
            || click == ClickType.DROP
            || click == ClickType.CONTROL_DROP;
    }
}
