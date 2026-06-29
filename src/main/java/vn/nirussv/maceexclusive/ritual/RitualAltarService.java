package vn.nirussv.maceexclusive.ritual;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;
import vn.nirussv.maceexclusive.core.CoreConfig;
import vn.nirussv.maceexclusive.core.CoreItemFactory;
import vn.nirussv.maceexclusive.core.CoreRegistry;
import vn.nirussv.maceexclusive.effect.FreezeService;
import vn.nirussv.maceexclusive.effect.SafeParticleSpawner;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ItemDefinition;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.item.ItemRegistry;
import vn.nirussv.maceexclusive.recipe.RecipeRegistry;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class RitualAltarService {

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CoreItemFactory coreItemFactory;
    private final ExclusiveItemFactory itemFactory;
    private final ItemRegistry itemRegistry;
    private final CoreRegistry coreRegistry;
    private final ItemMatcher itemMatcher;
    private final FreezeService freezeService;
    private final RecipeRegistry recipeRegistry;
    private final RitualAltarStore store;
    private final Random random = new Random();
    private final Map<BlockKey, RitualAltarSession> sessions = new HashMap<>();
    private final Map<Inventory, ActiveCraft> activeCrafts = new HashMap<>();
    private final Set<BlockKey> transforming = new HashSet<>();

    public RitualAltarService(MaceExclusivePlugin plugin, ConfigManager configManager, CoreItemFactory coreItemFactory, ExclusiveItemFactory itemFactory, ItemRegistry itemRegistry, CoreRegistry coreRegistry, ItemMatcher itemMatcher, FreezeService freezeService, RecipeRegistry recipeRegistry, RitualAltarStore store) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.coreItemFactory = coreItemFactory;
        this.itemFactory = itemFactory;
        this.itemRegistry = itemRegistry;
        this.coreRegistry = coreRegistry;
        this.itemMatcher = itemMatcher;
        this.freezeService = freezeService;
        this.recipeRegistry = recipeRegistry;
        this.store = store;
    }

    public void start() {
        for (RitualAltarStore.StoredAltar altar : store.load()) restore(altar);
        save();
    }

    public void shutdown() {
        for (ActiveCraft craft : activeCrafts.values()) craft.task().cancel();
        activeCrafts.clear();
        save();
        sessions.clear();
    }

    public boolean isAltar(Block block) {
        return block != null && sessions.containsKey(BlockKey.of(block));
    }

    public boolean transformToAltar(Player player, Block craftingTable) {
        if (!isTransformable(player, craftingTable)) return false;
        BlockKey key = BlockKey.of(craftingTable);
        if (!transforming.add(key)) return false;
        Location location = craftingTable.getLocation().clone();
        startTransformEffects(location);
        player.sendMessage(configManager.getMessage("ritual-altar.transform-start"));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> finishTransform(player, location, key), configManager.getRitualAltarTransformSeconds() * 20L);
        return true;
    }

    private void finishTransform(Player player, Location location, BlockKey key) {
        transforming.remove(key);
        Block block = location.getBlock();
        if (!isTransformable(player, block)) {
            player.sendMessage(configManager.getMessage("ritual-altar.transform-cancelled"));
            return;
        }
        consumeRitualCore(player);
        RitualAltarSession session = new RitualAltarSession(key, location, 0L);
        sessions.put(key, session);
        playTransformEffects(location);
        save();
        player.sendMessage(configManager.getMessage("ritual-altar.transformed"));
    }

    public void openAltar(Player player, Block altarBlock) {
        if (!isAltar(altarBlock)) return;
        RitualAltarMenu menu = new RitualAltarMenu(altarBlock.getLocation());
        updateMenu(menu);
        player.openInventory(menu.getInventory());
        player.sendMessage(configManager.getMessage("ritual-altar.open"));
    }

    public boolean attemptCraft(Player player, Block altarBlock, RitualAltarMenu menu) {
        if (!isAltar(altarBlock)) return false;
        RitualAltarSession session = sessions.get(BlockKey.of(altarBlock));
        if (session != null && session.cooldownUntil() > System.currentTimeMillis()) return message(player, "ritual-altar.cooldown");
        if (menu.isLocked() || activeCrafts.containsKey(menu.getInventory())) return false;
        ItemStack[] matrix = menu.matrix();
        MatchResult match = matchRecipe(matrix).orElse(null);
        if (match == null) return message(player, "ritual-altar.invalid-recipe");
        if (!hasCoreInCenter(matrix)) return message(player, "ritual-altar.no-core");
        ItemStack[] lockedMatrix = cloneMatrix(matrix);
        clearMatrix(menu.getInventory());
        menu.setLocked(true);
        player.sendActionBar(configManager.getMessage("ritual-altar.crafting"));
        BukkitTask task = plugin.getServer().getScheduler().runTaskLater(plugin, () -> completeCraft(player, altarBlock.getLocation(), BlockKey.of(altarBlock), menu, match), configManager.getRitualAltarCraftSeconds() * 20L);
        activeCrafts.put(menu.getInventory(), new ActiveCraft(lockedMatrix, task));
        return true;
    }

    private void completeCraft(Player player, Location altarLocation, BlockKey key, RitualAltarMenu menu, MatchResult match) {
        ActiveCraft craft = activeCrafts.remove(menu.getInventory());
        if (craft == null) return;
        menu.setLocked(false);
        RitualAltarSession session = sessions.get(key);
        if (session == null) {
            giveAll(player, craft.ingredients());
            return;
        }
        applyCraftCost(player, altarLocation.clone());
        give(player, rollResult(player, match));
        sessions.put(key, new RitualAltarSession(key, altarLocation, System.currentTimeMillis() + configManager.getRitualAltarCooldownMillis()));
        save();
        updateMenu(menu);
    }

    public void breakAltar(Block block) {
        if (block == null) return;
        RitualAltarSession removed = sessions.remove(BlockKey.of(block));
        if (removed == null) return;
        Location location = block.getLocation().add(0.5, 0.5, 0.5);
        block.getWorld().createExplosion(location, configManager.getRitualAltarBreakExplosionPower(), false, false);
        block.getWorld().dropItemNaturally(location, new ItemStack(Material.CRAFTING_TABLE));
        save();
    }

    public void updateMenu(RitualAltarMenu menu) {
        Optional<MatchResult> result = matchRecipe(menu.matrix());
        boolean ready = result.isPresent() && hasCoreInCenter(menu.matrix());
        menu.setPreview(result.map(MatchResult::preview).orElse(null), ready);
    }

    public void returnMenuItems(Player player, Inventory inventory) {
        ActiveCraft activeCraft = activeCrafts.remove(inventory);
        if (activeCraft != null) {
            activeCraft.task().cancel();
            giveAll(player, activeCraft.ingredients());
            player.sendMessage(configManager.getMessage("ritual-altar.returned"));
            return;
        }
        boolean returned = false;
        for (int slot = 0; slot < 9; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (isEmpty(item)) continue;
            inventory.setItem(slot, null);
            give(player, item);
            returned = true;
        }
        if (returned) player.sendMessage(configManager.getMessage("ritual-altar.returned"));
    }

    public Optional<Block> blockFrom(Location location) {
        if (location == null || location.getWorld() == null) return Optional.empty();
        return Optional.of(location.getBlock());
    }

    private boolean isTransformable(Player player, Block block) {
        return player != null && block != null && block.getType() == Material.CRAFTING_TABLE && !isAltar(block) && hasRitualCore(player);
    }

    private void restore(RitualAltarStore.StoredAltar stored) {
        Block block = stored.location().getBlock();
        if (block.getType() != Material.CRAFTING_TABLE) return;
        sessions.put(BlockKey.of(block), new RitualAltarSession(BlockKey.of(block), block.getLocation(), 0L));
    }

    private boolean hasRitualCore(Player player) {
        int cost = configManager.getRitualAltarTransformCost();
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        return (itemMatcher.isCore(mainHand, "ritual_core") && mainHand.getAmount() >= cost)
            || (itemMatcher.isCore(offHand, "ritual_core") && offHand.getAmount() >= cost);
    }

    private void consumeRitualCore(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (itemMatcher.isCore(mainHand, "ritual_core") && mainHand.getAmount() >= configManager.getRitualAltarTransformCost()) {
            decrement(mainHand);
            player.getInventory().setItemInMainHand(mainHand.getAmount() > 0 ? mainHand : null);
            return;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (itemMatcher.isCore(offHand, "ritual_core")) {
            decrement(offHand);
            player.getInventory().setItemInOffHand(offHand.getAmount() > 0 ? offHand : null);
        }
    }

    private void decrement(ItemStack item) {
        item.setAmount(Math.max(0, item.getAmount() - configManager.getRitualAltarTransformCost()));
    }

    private Optional<MatchResult> matchRecipe(ItemStack[] matrix) {
        if (matrix == null || matrix.length < 9) return Optional.empty();
        for (CoreConfig core : coreRegistry.all()) if (coreMatches(core, matrix)) return Optional.of(coreResult(core));
        for (ItemDefinition item : itemRegistry.all()) if (itemMatches(item, matrix)) return Optional.of(itemResult(item));
        return Optional.empty();
    }

    private boolean coreMatches(CoreConfig core, ItemStack[] matrix) {
        return core.enabled() && core.craftable() && core.shape().size() == 3 && shapeMatches(core.shape(), core.ingredients(), matrix);
    }

    private boolean itemMatches(ItemDefinition item, ItemStack[] matrix) {
        ItemConfig config = configManager.getItemConfig(item.id());
        return config != null && config.enabled() && config.recipe().enabled() && shapeMatches(config.recipe().shape(), config.recipe().ingredients(), matrix);
    }

    private boolean shapeMatches(List<String> shape, Map<Character, String> ingredients, ItemStack[] matrix) {
        if (shape.size() != 3) return false;
        for (int slot = 0; slot < 9; slot++) if (!slotMatches(shape, ingredients, matrix, slot)) return false;
        return true;
    }

    private boolean slotMatches(List<String> shape, Map<Character, String> ingredients, ItemStack[] matrix, int slot) {
        char symbol = symbolAt(shape, slot);
        ItemStack item = matrix[slot];
        if (symbol == ' ') return isEmpty(item);
        String requirement = ingredients.get(symbol);
        int amount = recipeRegistry.getRequiredAmount(shape, ingredients, slot);
        return requirement != null && ingredientMatches(requirement, amount, item);
    }

    private char symbolAt(List<String> shape, int slot) {
        String row = shape.get(slot / 3);
        return slot % 3 < row.length() ? row.charAt(slot % 3) : ' ';
    }

    private boolean ingredientMatches(String requirement, int amount, ItemStack item) {
        if (isEmpty(item)) return false;
        String key = ParsedRequirement.keyOnly(requirement);
        if (item.getAmount() < Math.max(1, amount)) return false;
        return customIngredientMatches(key, item) || materialIngredientMatches(key, item);
    }

    private boolean customIngredientMatches(String key, ItemStack item) {
        return itemMatcher.is(item, key) || itemMatcher.isCore(item, key);
    }

    private boolean materialIngredientMatches(String key, ItemStack item) {
        if ("ANY_HEAD".equalsIgnoreCase(key)) return item.getType().name().endsWith("_HEAD") || item.getType().name().endsWith("_SKULL");
        if ("ANY_POISON_POTION".equalsIgnoreCase(key)) return item.getType() == Material.POTION || item.getType() == Material.SPLASH_POTION || item.getType() == Material.LINGERING_POTION;
        Material material = Material.matchMaterial(key);
        return material != null && item.getType() == material;
    }

    private MatchResult coreResult(CoreConfig core) {
        ItemStack result = "ruined_core".equals(core.id()) ? new ItemStack(Material.HEAVY_CORE) : coreItemFactory.create(core.id());
        return new MatchResult(core.id(), false, result);
    }

    private MatchResult itemResult(ItemDefinition item) {
        return new MatchResult(item.id(), configManager.isWeaponItem(item.id()), itemFactory.create(item.id()));
    }

    private boolean hasCoreInCenter(ItemStack[] matrix) {
        return matrix.length > 4 && itemMatcher.matchCore(matrix[4]).isPresent();
    }

    private ItemStack rollResult(Player player, MatchResult match) {
        if (match.weapon() && random.nextDouble() < configManager.getRitualAltarWeaponFailChance()) {
            player.sendMessage(configManager.getMessage("ritual-altar.craft-fail"));
            return coreItemFactory.create("ruined_core");
        }
        player.sendMessage(configManager.getMessage("ritual-altar.craft-success"));
        return match.preview().clone();
    }

    private void applyCraftCost(Player player, Location altarLocation) {
        int min = configManager.getRitualAltarCraftDamageMin();
        int max = configManager.getRitualAltarCraftDamageMax();
        player.damage(min + random.nextInt(Math.max(1, max - min + 1)));
        freezeService.freeze(player, configManager.getRitualAltarCraftFreezeSeconds() * 20);
        altarLocation.getWorld().createExplosion(altarLocation.add(0.5, 0.5, 0.5), configManager.getRitualAltarFailExplosionPower(), false, false);
    }

    private void playTransformEffects(Location location) {
        Location center = location.clone().add(0.5, 1.0, 0.5);
        SafeParticleSpawner.spawn(location.getWorld(), Particle.ENCHANT, center, 48, 0.5, 0.5, 0.5, 0.05);
        if (configManager.isRitualAltarLightningEnabled()) location.getWorld().strikeLightningEffect(center);
        if (configManager.isRitualAltarSoundEnabled()) location.getWorld().playSound(center, resolveSound(configManager.getRitualAltarTransformSound()), SoundCategory.BLOCKS, 1.0f, 0.7f);
    }

    private void startTransformEffects(Location location) {
        Location center = location.clone().add(0.5, 1.0, 0.5);
        Particle particle = resolveParticle(configManager.getRitualAltarSmokeParticle());
        int ticks = configManager.getRitualAltarTransformSeconds() * 20;
        for (int tick = 0; tick < ticks; tick += 10) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                if (configManager.isRitualAltarSmokeEnabled()) SafeParticleSpawner.spawn(location.getWorld(), particle, center, 18, 0.55, 0.45, 0.55, 0.02);
            }, tick);
        }
    }

    private void give(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item.clone());
        for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }

    private void giveAll(Player player, ItemStack[] items) {
        for (ItemStack item : items) if (!isEmpty(item)) give(player, item);
    }

    private ItemStack[] cloneMatrix(ItemStack[] matrix) {
        ItemStack[] copy = new ItemStack[9];
        for (int slot = 0; slot < Math.min(9, matrix.length); slot++) copy[slot] = isEmpty(matrix[slot]) ? null : matrix[slot].clone();
        return copy;
    }

    private void clearMatrix(Inventory inventory) { for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, null); }

    private Particle resolveParticle(String name) {
        try { return Particle.valueOf(name == null ? "SMOKE" : name.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return Particle.SMOKE; }
    }

    private Sound resolveSound(String name) {
        try { return Sound.valueOf(name == null ? "BLOCK_ENCHANTMENT_TABLE_USE" : name.toUpperCase(java.util.Locale.ROOT)); }
        catch (IllegalArgumentException exception) { return Sound.BLOCK_ENCHANTMENT_TABLE_USE; }
    }

    private boolean message(Player player, String key) {
        player.sendMessage(configManager.getMessage(key));
        return false;
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private boolean save() {
        Collection<RitualAltarStore.StoredAltar> stored = sessions.values().stream()
            .map(session -> new RitualAltarStore.StoredAltar(session.location()))
            .toList();
        return store.save(stored);
    }

    public record BlockKey(UUID worldId, int x, int y, int z) {
        public static BlockKey of(Block block) { return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    }

    private record RitualAltarSession(BlockKey key, Location location, long cooldownUntil) { }
    private record MatchResult(String id, boolean weapon, ItemStack preview) { }
    private record ActiveCraft(ItemStack[] ingredients, BukkitTask task) { }
    private record ParsedRequirement() {
        private static String keyOnly(String value) {
            if (value == null) return "";
            int colonIndex = value.indexOf(':');
            return colonIndex < 0 ? value.trim() : value.substring(0, colonIndex).trim();
        }
    }
}
