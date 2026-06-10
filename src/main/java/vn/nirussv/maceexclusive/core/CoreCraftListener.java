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

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public final class CoreCraftListener implements Listener {

    private static final int FREEZE_TICKS = 60;

    private final ConfigManager configManager;
    private final CoreRegistry coreRegistry;
    private final CoreItemFactory coreItemFactory;
    private final ItemMatcher itemMatcher;
    private final FreezeService freezeService;
    private final Random random = new Random();
    private final Map<UUID, Long> lockouts = new HashMap<>();

    public CoreCraftListener(ConfigManager configManager, CoreRegistry coreRegistry, CoreItemFactory coreItemFactory, ItemMatcher itemMatcher, FreezeService freezeService) {
        this.configManager = configManager;
        this.coreRegistry = coreRegistry;
        this.coreItemFactory = coreItemFactory;
        this.itemMatcher = itemMatcher;
        this.freezeService = freezeService;
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
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cHãy lấy core từng cái một."));
            return;
        }
        if (isLocked(player)) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cBạn đang bị lockout chế tạo core."));
            return;
        }

        CoreConfig core = coreRegistry.find(craftedCore.get()).orElse(null);
        if (core == null) return;
        if (!hasEnoughXp(player, core.xpCost())) {
            player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&cKhông đủ XP để chế tạo core này."));
            return;
        }

        consumeIngredients(event.getInventory());
        chargeXp(player, core.xpCost());
        freezeService.freeze(player, FREEZE_TICKS);
        ItemStack output = random.nextDouble() < core.failureChance() ? fail(player) : coreItemFactory.create(core.id());
        give(player, output);
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
        Long endsAt = lockouts.get(player.getUniqueId());
        if (endsAt == null) return false;
        if (System.currentTimeMillis() < endsAt) return true;
        lockouts.remove(player.getUniqueId());
        return false;
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
        lockouts.put(player.getUniqueId(), System.currentTimeMillis() + configManager.getCoreCraftLockoutSeconds() * 1000L);
        player.sendMessage(net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize("&7Core craft failed; lockout applied."));
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
