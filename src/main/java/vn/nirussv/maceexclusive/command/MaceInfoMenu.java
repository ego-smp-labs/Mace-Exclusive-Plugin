package vn.nirussv.maceexclusive.command;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;
import vn.nirussv.maceexclusive.core.CoreItemFactory;
import vn.nirussv.maceexclusive.core.CoreRegistry;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ItemDefinition;
import vn.nirussv.maceexclusive.item.ItemRegistry;
import vn.nirussv.maceexclusive.mace.MaceManager;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class MaceInfoMenu implements Listener {

    private static final int OVERVIEW_SIZE = 54;
    private static final int RECIPE_SIZE = 27;
    private static final int[] RECIPE_SLOTS = {1, 2, 3, 10, 11, 12, 19, 20, 21};
    private static final int RESULT_SLOT = 16;
    private static final int BACK_SLOT = 26;

    private final ConfigManager configManager;
    private final MaceManager maceManager;
    private final ExclusiveItemFactory itemFactory;
    private final ItemRegistry itemRegistry;
    private final CoreRegistry coreRegistry;
    private final CoreItemFactory coreItemFactory;
    private final NamespacedKey itemIdKey;
    private final NamespacedKey backKey;

    public MaceInfoMenu(MaceExclusivePlugin plugin, ConfigManager configManager, MaceManager maceManager, ExclusiveItemFactory itemFactory, ItemRegistry itemRegistry, CoreRegistry coreRegistry, CoreItemFactory coreItemFactory) {
        this.configManager = configManager;
        this.maceManager = maceManager;
        this.itemFactory = itemFactory;
        this.itemRegistry = itemRegistry;
        this.coreRegistry = coreRegistry;
        this.coreItemFactory = coreItemFactory;
        this.itemIdKey = new NamespacedKey(plugin, "info_item_id");
        this.backKey = new NamespacedKey(plugin, "info_back");
    }

    public void openOverview(Player player) {
        InfoHolder holder = new InfoHolder(MenuType.OVERVIEW, null);
        Inventory inventory = Bukkit.createInventory(holder, OVERVIEW_SIZE, configManager.getMessage("menu.overview-title"));
        holder.setInventory(inventory);

        int slot = 0;
        for (ItemDefinition definition : weaponDefinitions()) {
            if (slot >= inventory.getSize()) break;
            inventory.setItem(slot++, menuIcon(definition.id()));
        }

        player.openInventory(inventory);
    }

    private void openRecipe(Player player, String id) {
        InfoHolder holder = new InfoHolder(MenuType.RECIPE, id);
        Inventory inventory = Bukkit.createInventory(holder, RECIPE_SIZE, configManager.getMessage("menu.recipe-title", Map.of("id", id, "name", maceManager.displayName(id))));
        holder.setInventory(inventory);

        ItemConfig itemConfig = configManager.getItemConfig(id);
        if (itemConfig != null) {
            populateRecipe(inventory, itemConfig.recipe());
        }
        inventory.setItem(RESULT_SLOT, menuIcon(id));
        inventory.setItem(BACK_SLOT, backButton());

        player.openInventory(inventory);
    }

    private List<ItemDefinition> weaponDefinitions() {
        return itemRegistry.all().stream()
            .filter(definition -> {
                ItemConfig itemConfig = configManager.getItemConfig(definition.id());
                return itemConfig != null && itemConfig.enabled() && isWeaponLike(definition.id());
            })
            .sorted(Comparator.comparing(ItemDefinition::id))
            .toList();
    }

    private boolean isWeaponLike(String id) {
        return id.endsWith("_mace") || id.endsWith("_spear") || id.equals("cursed_sword");
    }

    private ItemStack menuIcon(String id) {
        ItemStack item = itemFactory.create(id).clone();
        String holder = maceManager.getHolderName(id);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        if (holder != null) {
            meta.addEnchant(Enchantment.MENDING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            lore.add(configManager.getMessage("menu.owner-line", Map.of("player", holder)));
        } else {
            lore.add(configManager.getMessage("menu.available-line"));
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private void populateRecipe(Inventory inventory, ItemConfig.RecipeConfig recipe) {
        if (recipe == null || !recipe.enabled()) return;
        List<String> shape = recipe.shape();
        Map<Character, String> ingredients = recipe.ingredients();
        for (int row = 0; row < Math.min(3, shape.size()); row++) {
            String rowText = shape.get(row);
            for (int col = 0; col < Math.min(3, rowText.length()); col++) {
                char symbol = rowText.charAt(col);
                if (symbol == ' ') continue;
                String value = ingredients.get(symbol);
                if (value == null) continue;
                final int slotIndex = RECIPE_SLOTS[row * 3 + col];
                resolveDisplayIngredient(value).ifPresent(item -> inventory.setItem(slotIndex, item));
            }
        }
    }

    private Optional<ItemStack> resolveDisplayIngredient(String value) {
        IngredientRequirement requirement = parseRequirement(value);
        String key = requirement.itemKey();
        if (key.isBlank()) return Optional.empty();

        if (key.equalsIgnoreCase("ANY_HEAD")) {
            return Optional.of(namedStack(Material.PLAYER_HEAD, requirement.amount(), "Any Head"));
        }
        if (key.equalsIgnoreCase("ANY_POISON_POTION")) {
            return Optional.of(namedStack(Material.POTION, requirement.amount(), "Any Poison Potion"));
        }

        if (itemRegistry.find(key).isPresent()) {
            ItemStack item = itemFactory.create(key).clone();
            item.setAmount(requirement.amount());
            return Optional.of(item);
        }
        if (coreRegistry.find(key).isPresent()) {
            ItemStack item = coreItemFactory.create(key).clone();
            item.setAmount(requirement.amount());
            return Optional.of(item);
        }

        Material material = Material.matchMaterial(key);
        if (material == null) return Optional.empty();
        return Optional.of(new ItemStack(material, requirement.amount()));
    }

    private IngredientRequirement parseRequirement(String value) {
        if (value == null) return new IngredientRequirement("", 1);
        String trimmed = value.trim();
        int colonIndex = trimmed.lastIndexOf(':');
        if (colonIndex <= 0 || colonIndex == trimmed.length() - 1) return new IngredientRequirement(trimmed, 1);
        try {
            int amount = Integer.parseInt(trimmed.substring(colonIndex + 1).trim());
            return new IngredientRequirement(trimmed.substring(0, colonIndex).trim(), Math.max(1, amount));
        } catch (NumberFormatException ignored) {
            return new IngredientRequirement(trimmed, 1);
        }
    }

    private ItemStack namedStack(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name));
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack backButton() {
        ItemStack item = namedStack(Material.ARROW, 1, "Back");
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.getPersistentDataContainer().set(backKey, PersistentDataType.BYTE, (byte) 1);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof InfoHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null) return;

        if (holder.type() == MenuType.RECIPE && meta.getPersistentDataContainer().has(backKey, PersistentDataType.BYTE)) {
            openOverview(player);
            return;
        }

        String id = meta.getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
        if (holder.type() == MenuType.OVERVIEW && id != null) {
            openRecipe(player, id.toLowerCase(Locale.ROOT));
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof InfoHolder) {
            event.setCancelled(true);
        }
    }

    private record IngredientRequirement(String itemKey, int amount) {
    }

    private enum MenuType {
        OVERVIEW,
        RECIPE
    }

    private static final class InfoHolder implements InventoryHolder {
        private final MenuType type;
        private final String itemId;
        private Inventory inventory;

        private InfoHolder(MenuType type, String itemId) {
            this.type = type;
            this.itemId = itemId;
        }

        private MenuType type() { return type; }

        @SuppressWarnings("unused")
        private String itemId() { return itemId; }

        private void setInventory(Inventory inventory) { this.inventory = inventory; }

        @Override
        public @NotNull Inventory getInventory() { return inventory; }
    }
}
