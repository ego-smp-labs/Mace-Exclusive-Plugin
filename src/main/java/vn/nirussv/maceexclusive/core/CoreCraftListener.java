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
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.FreezeService;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.curse.LockoutService;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public final class CoreCraftListener implements Listener {

    private static final int FREEZE_TICKS = 100;

    private final ConfigManager configManager;
    private final CoreRegistry coreRegistry;
    private final CoreItemFactory coreItemFactory;
    private final ItemMatcher itemMatcher;
    private final FreezeService freezeService;
    private final LockoutService lockoutService;
    private final Random random = new Random();

    public CoreCraftListener(ConfigManager configManager, CoreRegistry coreRegistry, CoreItemFactory coreItemFactory, ItemMatcher itemMatcher, FreezeService freezeService, LockoutService lockoutService) {
        this.configManager = configManager;
        this.coreRegistry = coreRegistry;
        this.coreItemFactory = coreItemFactory;
        this.itemMatcher = itemMatcher;
        this.freezeService = freezeService;
        this.lockoutService = lockoutService;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCraftCore(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack result = event.getCurrentItem();
        if (result == null) return;
        if (isRuinedRestore(event.getInventory(), result)) return;
        Optional<String> craftedCore = itemMatcher.matchCore(result);
        if (craftedCore.isEmpty()) return;

        event.setCancelled(true);
        if (isUnsafeBulkCraft(event)) {
            player.sendMessage(configManager.getMessage("core.take-one-at-a-time"));
            return;
        }
        if (isLocked(player)) {
            player.sendMessage(configManager.getMessage("core.craft-locked"));
            return;
        }

        CoreConfig core = coreRegistry.find(craftedCore.get()).orElse(null);
        if (core == null) return;
        if (!hasEnoughXp(player, core.xpCost())) {
            player.sendMessage(configManager.getMessage("core.insufficient-xp"));
            return;
        }

        consumeIngredients(event.getInventory());
        chargeXp(player, core.xpCost());
        freezeService.freeze(player, FREEZE_TICKS);
        ItemStack output = random.nextDouble() < core.failureChance() ? fail(player) : coreItemFactory.create(core.id());
        give(player, output);

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

    private void consumeIngredients(CraftingInventory inventory) {
        ItemStack[] matrix = inventory.getMatrix();
        for (int i = 0; i < matrix.length; i++) {
            ItemStack item = matrix[i];
            if (item == null || item.getType().isAir()) continue;
            // Phase 2 recipes are shaped 3x3 with one unit per occupied slot.
            // TODO: add explicit per-ingredient amounts if CoreConfig grows amount support.
            item.setAmount(item.getAmount() - 1);
            if (item.getAmount() <= 0) matrix[i] = null;
        }
        inventory.setMatrix(matrix);
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }
}
