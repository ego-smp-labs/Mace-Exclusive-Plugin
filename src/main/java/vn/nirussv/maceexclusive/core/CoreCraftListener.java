package vn.nirussv.maceexclusive.core;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.FreezeService;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.curse.LockoutService;
import vn.nirussv.maceexclusive.recipe.RecipeRegistry;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public final class CoreCraftListener implements Listener {

    private static final int FREEZE_TICKS = 100;

    private final Map<UUID, Long> lastCraftTimes = new HashMap<>();
    private final ConfigManager configManager;
    private final CoreRegistry coreRegistry;
    private final CoreItemFactory coreItemFactory;
    private final ItemMatcher itemMatcher;
    private final FreezeService freezeService;
    private final LockoutService lockoutService;
    private final RecipeRegistry recipeRegistry;
    private final Random random = new Random();

    public CoreCraftListener(ConfigManager configManager, CoreRegistry coreRegistry, CoreItemFactory coreItemFactory, ItemMatcher itemMatcher, FreezeService freezeService, LockoutService lockoutService, RecipeRegistry recipeRegistry) {
        this.configManager = configManager;
        this.coreRegistry = coreRegistry;
        this.coreItemFactory = coreItemFactory;
        this.itemMatcher = itemMatcher;
        this.freezeService = freezeService;
        this.lockoutService = lockoutService;
        this.recipeRegistry = recipeRegistry;
        this.random.setSeed(System.nanoTime()); // Clean code: properly seed random or use it safely
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftCore(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        if (result == null) return;
        if (isRuinedRestore(event.getInventory(), result)) return;
        Optional<String> craftedCore = itemMatcher.matchCore(result);
        if (craftedCore.isEmpty()) return;

        CoreConfig core = coreRegistry.find(craftedCore.get()).orElse(null);
        if (core == null) return;
        ConfigManager.CraftFeedback feedback = configManager.getCoreCraftFeedback(core);
        if (!feedback.specialCraftingEnabled()) return;

        event.setCancelled(true);

        // Rate limit crafting (500ms cooldown) to prevent auto-clicker duplication exploits
        long now = System.currentTimeMillis();
        long lastCraft = lastCraftTimes.getOrDefault(player.getUniqueId(), 0L);
        if (now - lastCraft < 500) {
            player.sendMessage(configManager.getMessage("core.take-one-at-a-time"));
            return;
        }
        lastCraftTimes.put(player.getUniqueId(), now);

        if (configManager.isCraftingShiftClickPrevented() && isUnsafeBulkCraft(event)) {
            player.sendMessage(configManager.getMessage("core.take-one-at-a-time"));
            return;
        }
        if (isLocked(player)) {
            player.sendMessage(configManager.getMessage("core.craft-locked"));
            return;
        }

        if (!hasEnoughXp(player, core.xpCost())) {
            player.sendMessage(configManager.getMessage("core.insufficient-xp"));
            return;
        }

        configManager.sendCraftStartMessage(player, core.id(), core.name(), feedback);
        consumeIngredients(event.getInventory(), event.getRecipe());
        chargeXp(player, core.xpCost());
        freezeService.freeze(player, FREEZE_TICKS);
        boolean failed = random.nextDouble() < core.failureChance();
        ItemStack output = failed ? fail(player) : coreItemFactory.create(core.id());
        give(player, output);
        if (!failed) {
            if (feedback.glowAfterCraft()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, true));
            }
            configManager.sendCraftCompleteMessage(player, core.id(), core.name(), feedback);
        }

        double damage = (5 + random.nextInt(5)) * 2.0D;
        player.damage(damage);
    }

    private boolean isUnsafeBulkCraft(CraftItemEvent event) {
        ClickType click = event.getClick();
        return event.isShiftClick() || click == ClickType.NUMBER_KEY || click == ClickType.DOUBLE_CLICK;
    }

    private boolean isRuinedRestore(CraftingInventory inventory, ItemStack result) {
        if (result.getType() != Material.HEAVY_CORE) return false;
        for (ItemStack item : inventory.getMatrix()) {
            if (itemMatcher.isCore(item, "ruined_core")) return true;
        }
        return false;
    }

    private boolean isLocked(Player player) {
        return lockoutService.isCursed(player);
    }

    private boolean hasEnoughXp(Player player, int xpCost) {
        return xpCost <= 0 || getTotalExperience(player) >= xpCost;
    }

    private int getTotalExperience(Player player) {
        int level = player.getLevel();
        int total = Math.round(player.getExp() * player.getExpToLevel());
        for (int current = 0; current < level; current++) {
            total += xpForLevel(current);
        }
        return total;
    }

    private int xpForLevel(int level) {
        if (level >= 30) return 9 * level - 158;
        if (level >= 15) return 5 * level - 38;
        return 2 * level + 7;
    }

    private void chargeXp(Player player, int xpCost) {
        if (xpCost > 0) player.giveExp(-xpCost);
    }

    private ItemStack fail(Player player) {
        lockoutService.applyCursed(player.getUniqueId(), configManager.getCoreCraftLockoutSeconds());
        player.sendMessage(configManager.getMessage("core.craft-failed"));
        return coreItemFactory.create("ruined_core");
    }

    private void consumeIngredients(CraftingInventory inventory, org.bukkit.inventory.Recipe recipe) {
        ItemStack[] matrix = inventory.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType().isAir()) continue;
            int required = recipeRegistry.getRequiredAmount(recipe, i);
            int toDeduct = Math.max(1, required);
            item.setAmount(item.getAmount() - toDeduct);
            if (item.getAmount() <= 0) matrix[i] = null;
        }
        inventory.setMatrix(matrix);
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }

}
