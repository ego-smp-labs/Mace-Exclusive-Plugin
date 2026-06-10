package vn.nirussv.maceexclusive.forge;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ItemRegistry;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.persistence.ForgeSessionStore;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

public final class ForgeService {

    private static final long TICK_PERIOD = 5L;

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final ExclusiveItemFactory itemFactory;
    private final ItemRegistry itemRegistry;
    private final MaceManager maceManager;
    private final ForgeSessionStore store;
    private final ForgeVisualService visualService;
    private final Map<BlockKey, ForgeSession> sessions = new HashMap<>();
    private final Set<String> reservedItemIds = new HashSet<>();

    public ForgeService(MaceExclusivePlugin plugin, ConfigManager configManager, ExclusiveItemFactory itemFactory, ItemRegistry itemRegistry, MaceManager maceManager, ForgeSessionStore store, ForgeVisualService visualService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemFactory = itemFactory;
        this.itemRegistry = itemRegistry;
        this.maceManager = maceManager;
        this.store = store;
        this.visualService = visualService;
    }

    public void start() {
        for (ForgeSessionStore.StoredForgeSession stored : store.load()) restore(stored);
        save();
    }

    public void shutdown() {
        save();
        for (ForgeSession session : sessions.values()) {
            if (session.task() != null) session.task().cancel();
            removeHologram(session);
        }
        sessions.clear();
        reservedItemIds.clear();
    }

    public boolean isForgeBlock(Block block) {
        return block != null && sessions.containsKey(BlockKey.of(block));
    }

    public boolean isItemReserved(String itemId) {
        if (itemId == null) return false;
        if (!configManager.isSingletonItem(itemId)) return false;
        return reservedItemIds.contains(itemId.toLowerCase());
    }

    public boolean tryStartFromCraft(Player player, CraftingInventory inventory, String itemId) {
        if (player == null || inventory == null || itemId == null) return false;
        String id = itemId.toLowerCase();
        if (itemRegistry.find(id).isEmpty()) return false;
        if (!reserve(id)) return false;

        Block craftBlock = resolveCraftingBlock(inventory);
        if (craftBlock == null) { release(id); return false; }
        if (sessions.containsKey(BlockKey.of(craftBlock))) { release(id); return false; }
        if (!turnIntoForgeBlock(craftBlock)) { release(id); return false; }

        ForgeSession session = null;
        try {
            session = createSession(player, craftBlock, id);
            if (!commitSession(session)) {
                cleanupFailedCommit(session, craftBlock, id, true);
                return false;
            }
            if (!consumeIngredientsSafely(inventory)) {
                cleanupFailedCommit(session, craftBlock, id, true);
                return false;
            }
            return true;
        } catch (RuntimeException exception) {
            cleanupFailedCommit(session, craftBlock, id, true);
            plugin.getLogger().log(Level.WARNING, "Failed to start forge transaction for " + id + " by " + player.getName(), exception);
            return false;
        }
    }

    public String unavailableReason(String itemId) {
        if (isItemReserved(itemId)) return "Vũ khí này đang được đúc.";
        String holder = maceManager.getHolderName(itemId);
        if (holder != null) return "Vũ khí này đã có chủ sở hữu: " + holder;
        return "Vũ khí này hiện không thể đúc.";
    }

    public void abort(Block block, boolean explode) {
        if (block == null) return;
        ForgeSession session = sessions.remove(BlockKey.of(block));
        if (session == null) return;
        reservedItemIds.remove(session.itemId());
        if (session.task() != null) session.task().cancel();
        removeHologram(session);
        Location center = session.blockLocation().clone().add(0.5, 1.0, 0.5);
        if (explode && configManager.getForgeAbortExplosionPower() > 0f) {
            session.blockLocation().getWorld().createExplosion(center, configManager.getForgeAbortExplosionPower(), false, false);
        }
        save();
    }

    private boolean reserve(String itemId) {
        if (configManager.isSingletonItem(itemId)) {
            if (reservedItemIds.contains(itemId)) return false;
            if (!maceManager.canCreate(itemId)) return false;
            reservedItemIds.add(itemId);
        }
        return true;
    }

    private void release(String itemId) {
        if (configManager.isSingletonItem(itemId)) {
            reservedItemIds.remove(itemId);
        }
    }

    private Block resolveCraftingBlock(CraftingInventory inventory) {
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) return null;
        Block block = location.getBlock();
        return block.getType() == Material.CRAFTING_TABLE ? block : null;
    }

    private boolean turnIntoForgeBlock(Block block) {
        if (block.getType() != Material.CRAFTING_TABLE) return false;
        block.setType(configManager.getForgeBlockMaterial(), false);
        return block.getType() == configManager.getForgeBlockMaterial();
    }

    private boolean consumeIngredientsSafely(CraftingInventory inventory) {
        ItemStack[] original = inventory.getMatrix();
        ItemStack[] snapshot = cloneMatrix(original);
        ItemStack originalResult = inventory.getResult() == null ? null : inventory.getResult().clone();
        try {
            ItemStack[] matrix = inventory.getMatrix();
            for (int index = 0; index < matrix.length; index++) {
                ItemStack item = matrix[index];
                if (item == null || item.getType().isAir()) continue;
                item.setAmount(item.getAmount() - 1);
                if (item.getAmount() <= 0) matrix[index] = null;
            }
            inventory.setMatrix(matrix);
            inventory.setResult(null);
            return true;
        } catch (RuntimeException exception) {
            inventory.setMatrix(snapshot);
            inventory.setResult(originalResult);
            plugin.getLogger().log(Level.WARNING, "Failed to consume forge craft ingredients safely.", exception);
            return false;
        }
    }

    private ItemStack[] cloneMatrix(ItemStack[] matrix) {
        ItemStack[] clone = new ItemStack[matrix.length];
        for (int index = 0; index < matrix.length; index++) {
            clone[index] = matrix[index] == null ? null : matrix[index].clone();
        }
        return clone;
    }

    private ForgeSession createSession(Player player, Block block, String itemId) {
        long now = System.currentTimeMillis();
        long chargeEnds = now + configManager.getPreforgeChargeSeconds() * 1000L;
        long forgeEnds = chargeEnds + configManager.getForgeDurationSeconds() * 1000L;
        return new ForgeSession(BlockKey.of(block), block.getLocation(), itemId, player.getUniqueId(), now, chargeEnds, forgeEnds, false, null, null);
    }

    private boolean commitSession(ForgeSession session) {
        sessions.put(session.key(), session);
        try {
            createHologram(session);
            schedule(session);
            if (save()) return true;
            return false;
        } catch (RuntimeException exception) {
            plugin.getLogger().log(Level.WARNING, "Failed to commit forge session for " + session.itemId(), exception);
            return false;
        }
    }

    private void cleanupFailedCommit(ForgeSession session, Block block, String itemId, boolean restoreCraftingTable) {
        release(itemId);
        if (session != null) {
            sessions.remove(session.key());
            if (session.task() != null) session.task().cancel();
            removeHologram(session);
        }
        if (restoreCraftingTable && block != null && block.getType() == configManager.getForgeBlockMaterial()) {
            block.setType(Material.CRAFTING_TABLE, false);
        }
        save();
    }

    private void restore(ForgeSessionStore.StoredForgeSession stored) {
        if (stored.location().getBlock().getType() != configManager.getForgeBlockMaterial()) return;
        if (!reserve(stored.itemId())) return;
        ForgeSession session = new ForgeSession(BlockKey.of(stored.location().getBlock()), stored.location(), stored.itemId(), stored.owner(), stored.startedAtMillis(), stored.chargeEndsAtMillis(), stored.endsAtMillis(), false, null, null);
        sessions.put(session.key(), session);
        createHologram(session);
        schedule(session);
    }

    private void schedule(ForgeSession session) {
        session.task(plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(session.key()), 0L, TICK_PERIOD));
    }

    private void tick(BlockKey key) {
        ForgeSession session = sessions.get(key);
        if (session == null) return;
        Block block = session.blockLocation().getBlock();
        if (block.getType() != configManager.getForgeBlockMaterial()) { abort(block, true); return; }
        long now = System.currentTimeMillis();
        if (now < session.chargeEndsAtMillis()) { tickCharge(session, now); return; }
        if (!session.chargeCompleted()) completeCharge(session);
        if (now >= session.endsAtMillis()) { complete(session); return; }
        updateForgeHologram(session, session.endsAtMillis() - now);
    }

    private void tickCharge(ForgeSession session, long now) {
        long elapsedTicks = Math.max(0L, (now - session.startedAtMillis()) / 50L);
        long totalTicks = Math.max(1L, (session.chargeEndsAtMillis() - session.startedAtMillis()) / 50L);
        visualService.playCharge(session.blockLocation().getBlock(), session.itemId(), elapsedTicks, totalTicks);
        updateChargeHologram(session, session.chargeEndsAtMillis() - now);
    }

    private void completeCharge(ForgeSession session) {
        session.chargeCompleted(true);
        Block block = session.blockLocation().getBlock();
        visualService.playChargeBurst(block);
        if (configManager.getPreforgeExplosionPower() > 0f) {
            block.getWorld().createExplosion(block.getLocation().add(0.5, 1.0, 0.5), configManager.getPreforgeExplosionPower(), false, false);
        }
        save();
    }

    private void complete(ForgeSession session) {
        sessions.remove(session.key());
        if (session.task() != null) session.task().cancel();
        removeHologram(session);
        save();

        Location spawnLocation = session.blockLocation().clone().add(0.5, 1.2, 0.5);
        World world = spawnLocation.getWorld();
        if (world == null) { reservedItemIds.remove(session.itemId()); return; }
        ItemStack result = itemFactory.create(session.itemId());
        if (session.owner() == null) {
            reservedItemIds.remove(session.itemId());
            plugin.getLogger().warning("Aborted forge completion without owner for itemId=" + session.itemId()
                + " owner=null location=" + formatLocation(spawnLocation));
            return;
        }
        if (!maceManager.register(result, session.owner(), session.itemId())) {
            reservedItemIds.remove(session.itemId());
            plugin.getLogger().warning("Aborted forge completion after failed registration for itemId=" + session.itemId()
                + " owner=" + session.owner() + " location=" + formatLocation(spawnLocation));
            return;
        }
        world.strikeLightning(spawnLocation);
        float explosionPower = Math.max(6.0f, configManager.getCompletionExplosionPower());
        world.createExplosion(spawnLocation, explosionPower, true, true);
        visualService.playCompletion(session.blockLocation().getBlock());
        Item item = world.dropItem(spawnLocation, result);
        item.setInvulnerable(true);
        item.setPickupDelay(20);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { if (!item.isDead()) item.setInvulnerable(false); }, 20L);
        Player owner = session.owner() == null ? null : plugin.getServer().getPlayer(session.owner());
        if (owner != null) maceManager.onPlayerBecameHolder(owner, spawnLocation, session.itemId());
        reservedItemIds.remove(session.itemId());
    }

    private String formatLocation(Location location) {
        if (location == null) return "null";
        World world = location.getWorld();
        String worldName = world == null ? "null" : world.getName();
        return worldName + ":" + location.getBlockX() + "," + location.getBlockY() + "," + location.getBlockZ();
    }

    private void createHologram(ForgeSession session) {
        Location location = session.blockLocation().clone().add(0.5, 1.7, 0.5);
        try {
            TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.text(configManager.getMessage("forge.charging"));
            });
            session.hologram(textDisplay);
        } catch (Throwable ignored) {
            ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class, stand -> {
                stand.setMarker(true);
                stand.setInvisible(true);
                stand.setCustomNameVisible(true);
                stand.customName(configManager.getMessage("forge.charging"));
            });
            session.hologram(armorStand);
        }
    }

    private void updateChargeHologram(ForgeSession session, long remainingMillis) {
        long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        String secondsStr = String.format("%02d", seconds);
        net.kyori.adventure.text.Component text = configManager.getMessage("forge.charging-format", java.util.Map.of(
            "name", displayName(session.itemId()),
            "seconds", secondsStr
        ));
        setHologramText(session, text);
    }

    private void updateForgeHologram(ForgeSession session, long remainingMillis) {
        long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        String timeStr = String.format("%02d:%02d", seconds / 60L, seconds % 60L);
        net.kyori.adventure.text.Component text = configManager.getMessage("forge.forging-format", java.util.Map.of(
            "name", displayName(session.itemId()),
            "time", timeStr
        ));
        setHologramText(session, text);
    }

    private String displayName(String itemId) {
        return itemRegistry.find(itemId).map(definition -> definition.name()).orElse(itemId);
    }

    private void setHologramText(ForgeSession session, Component text) {
        Entity hologram = session.hologram();
        if (hologram instanceof TextDisplay textDisplay) textDisplay.text(text);
        else if (hologram instanceof ArmorStand armorStand) armorStand.customName(text);
    }

    private void removeHologram(ForgeSession session) {
        Entity hologram = session.hologram();
        if (hologram != null && !hologram.isDead()) hologram.remove();
    }

    private boolean save() {
        Collection<ForgeSessionStore.StoredForgeSession> stored = sessions.values().stream()
            .map(s -> new ForgeSessionStore.StoredForgeSession(s.blockLocation(), s.itemId(), s.owner(), s.startedAtMillis(), s.chargeEndsAtMillis(), s.endsAtMillis()))
            .toList();
        return store.save(stored);
    }

    public record BlockKey(UUID worldId, int x, int y, int z) {
        public static BlockKey of(Block block) { return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ()); }
    }

    private static final class ForgeSession {
        private final BlockKey key;
        private final Location blockLocation;
        private final String itemId;
        private final UUID owner;
        private final long startedAtMillis;
        private final long chargeEndsAtMillis;
        private final long endsAtMillis;
        private boolean chargeCompleted;
        private Entity hologram;
        private BukkitTask task;

        private ForgeSession(BlockKey key, Location blockLocation, String itemId, UUID owner, long startedAtMillis, long chargeEndsAtMillis, long endsAtMillis, boolean chargeCompleted, Entity hologram, BukkitTask task) {
            this.key = key;
            this.blockLocation = blockLocation;
            this.itemId = itemId;
            this.owner = owner;
            this.startedAtMillis = startedAtMillis;
            this.chargeEndsAtMillis = chargeEndsAtMillis;
            this.endsAtMillis = endsAtMillis;
            this.chargeCompleted = chargeCompleted;
            this.hologram = hologram;
            this.task = task;
        }

        private BlockKey key() { return key; }
        private Location blockLocation() { return blockLocation; }
        private String itemId() { return itemId; }
        private UUID owner() { return owner; }
        private long startedAtMillis() { return startedAtMillis; }
        private long chargeEndsAtMillis() { return chargeEndsAtMillis; }
        private long endsAtMillis() { return endsAtMillis; }
        private boolean chargeCompleted() { return chargeCompleted; }
        private void chargeCompleted(boolean chargeCompleted) { this.chargeCompleted = chargeCompleted; }
        private Entity hologram() { return hologram; }
        private void hologram(Entity hologram) { this.hologram = hologram; }
        private BukkitTask task() { return task; }
        private void task(BukkitTask task) { this.task = task; }
    }
}
