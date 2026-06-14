package vn.nirussv.maceexclusive.forge;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.SmithingInventory;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.util.Scheduler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class NetheriteForgeService {

    private static final long TICK_PERIOD = 5L;
    private static final double LOCK_DRIFT_DISTANCE_SQUARED = 0.04D;

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final Map<BlockKey, NetheriteForgeSession> sessions = new HashMap<>();
    private final Set<UUID> visualItems = new HashSet<>();

    public NetheriteForgeService(MaceExclusivePlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    MaceExclusivePlugin plugin() {
        return plugin;
    }

    public boolean isForgeBlock(Block block) {
        return block != null && sessions.containsKey(BlockKey.of(block));
    }

    public boolean tryStartAfterCraft(Player player, Block workstation, ItemStack expectedResult, ConfigManager.TimedForgeItemSettings settings) {
        if (player == null || workstation == null || expectedResult == null || expectedResult.getType().isAir() || settings == null) return false;
        if (!configManager.isTimedForgeEnabled() || !settings.enabled()) return false;
        BlockKey key = BlockKey.of(workstation);
        if (sessions.containsKey(key)) {
            player.sendMessage(configManager.getMessage("timed-forge.busy"));
            return false;
        }

        ItemStack forgedResult = yankCraftedResult(player, expectedResult);
        if (forgedResult == null) {
            player.sendMessage(configManager.getMessage("timed-forge.collect-failed"));
            return false;
        }
        Material originalBlock = workstation.getType();
        workstation.setType(configManager.getForgeBlockMaterial(), false);
        if (workstation.getType() != configManager.getForgeBlockMaterial()) {
            returnResult(player, forgedResult, workstation.getLocation());
            return false;
        }

        long durationMillis = settings.durationSeconds() * 1000L;
        Location blockLocation = workstation.getLocation();
        Location lockLocation = player.getLocation().clone();
        Item visualItem = spawnVisualItem(blockLocation, forgedResult);
        NetheriteForgeSession session = new NetheriteForgeSession(
            key,
            player.getUniqueId(),
            blockLocation,
            lockLocation,
            originalBlock,
            forgedResult,
            settings.successRate(),
            durationMillis,
            isOwnerInRange(player, blockLocation),
            System.currentTimeMillis(),
            visualItem == null ? null : visualItem.getUniqueId(),
            null
        );
        sessions.put(key, session);
        schedule(session);
        player.sendMessage(configManager.getMessage("timed-forge.started"));
        return true;
    }

    public void resume(Player player, Block block) {
        if (player == null || block == null) return;
        NetheriteForgeSession session = sessions.get(BlockKey.of(block));
        if (session == null) return;
        if (!player.getUniqueId().equals(session.owner())) {
            player.sendMessage(configManager.getMessage("timed-forge.busy"));
            return;
        }
        if (!isOwnerInRange(player, session.blockLocation())) {
            player.sendMessage(configManager.getMessage("timed-forge.paused"));
            return;
        }
        session.active(true);
        session.pausedNotified(false);
        session.lastTickMillis(System.currentTimeMillis());
        player.sendMessage(configManager.getMessage("timed-forge.resumed"));
    }

    public void abort(Block block, boolean notifyOwner) {
        if (block == null) return;
        NetheriteForgeSession session = sessions.remove(BlockKey.of(block));
        if (session == null) return;
        if (session.task() != null) session.task().cancel();
        removeVisualItem(session);
        restoreOriginalBlock(session);
        Player owner = plugin.getServer().getPlayer(session.owner());
        if (owner != null) {
            returnResult(owner, session.result(), session.blockLocation());
            if (notifyOwner) owner.sendMessage(configManager.getMessage("timed-forge.aborted"));
            return;
        }
        dropResult(session.blockLocation(), session.result());
    }

    public void shutdown() {
        for (NetheriteForgeSession session : java.util.List.copyOf(sessions.values())) {
            abort(session.blockLocation().getBlock(), false);
        }
        sessions.clear();
    }

    Block resolveSmithingTable(SmithingInventory inventory) {
        Location location = inventory.getLocation();
        if (location == null || location.getWorld() == null) return null;
        Block block = location.getBlock();
        return block.getType() == Material.SMITHING_TABLE ? block : null;
    }

    public boolean isVisualItem(Item item) {
        return item != null && visualItems.contains(item.getUniqueId());
    }

    private void schedule(NetheriteForgeSession session) {
        session.task(Scheduler.runLocationTaskTimer(plugin, session.blockLocation(), () -> tick(session.key()), 0L, TICK_PERIOD));
    }

    private void tick(BlockKey key) {
        NetheriteForgeSession session = sessions.get(key);
        if (session == null) return;
        Block block = session.blockLocation().getBlock();
        if (block.getType() != configManager.getForgeBlockMaterial()) {
            abort(block, true);
            return;
        }

        long now = System.currentTimeMillis();
        Player owner = plugin.getServer().getPlayer(session.owner());
        if (owner == null || !isOwnerInRange(owner, session.blockLocation())) {
            pause(session, owner);
            return;
        }
        if (!session.active()) {
            session.lastTickMillis(now);
            return;
        }

        freezeOwner(owner, session);
        long elapsed = Math.max(0L, now - session.lastTickMillis());
        session.lastTickMillis(now);
        session.remainingMillis(session.remainingMillis() - elapsed);
        if (session.remainingMillis() <= 0L) complete(session);
    }

    private void pause(NetheriteForgeSession session, Player owner) {
        session.active(false);
        session.lastTickMillis(System.currentTimeMillis());
        if (owner != null && !session.pausedNotified()) {
            owner.sendMessage(configManager.getMessage("timed-forge.paused"));
            session.pausedNotified(true);
        }
    }

    private boolean isOwnerInRange(Player player, Location blockLocation) {
        if (player == null || blockLocation == null || blockLocation.getWorld() == null) return false;
        if (!player.getWorld().equals(blockLocation.getWorld())) return false;
        double maxDistance = configManager.getTimedForgeMaxDistance();
        return player.getLocation().distanceSquared(blockLocation.clone().add(0.5D, 0.5D, 0.5D)) <= maxDistance * maxDistance;
    }

    private void freezeOwner(Player player, NetheriteForgeSession session) {
        if (!configManager.shouldFreezeTimedForgePlayer()) return;
        player.setVelocity(player.getVelocity().zero());
        if (!player.getWorld().equals(session.lockLocation().getWorld())) return;
        if (player.getLocation().distanceSquared(session.lockLocation()) > LOCK_DRIFT_DISTANCE_SQUARED) {
            player.teleport(session.lockLocation());
        }
    }

    private void complete(NetheriteForgeSession session) {
        sessions.remove(session.key());
        if (session.task() != null) session.task().cancel();
        removeVisualItem(session);
        if (configManager.shouldRestoreWorkstationOnTimedForgeComplete()) {
            restoreOriginalBlock(session);
        }
        boolean success = ThreadLocalRandom.current().nextDouble() < session.successRate();
        Player owner = plugin.getServer().getPlayer(session.owner());
        if (success) {
            dropResult(session.blockLocation(), session.result());
            if (owner != null) owner.sendMessage(configManager.getMessage("timed-forge.completed"));
        } else if (owner != null) {
            owner.sendMessage(configManager.getMessage("timed-forge.failed"));
        }
    }

    private void restoreOriginalBlock(NetheriteForgeSession session) {
        Block block = session.blockLocation().getBlock();
        if (block.getType() == configManager.getForgeBlockMaterial()) {
            block.setType(session.originalBlock(), false);
        }
    }

    private void returnResult(Player owner, ItemStack result, Location fallbackLocation) {
        HashMap<Integer, ItemStack> leftover = owner.getInventory().addItem(result.clone());
        for (ItemStack item : leftover.values()) dropResult(fallbackLocation, item);
    }

    private void dropResult(Location blockLocation, ItemStack result) {
        if (blockLocation == null || blockLocation.getWorld() == null || result == null || result.getType().isAir()) return;
        World world = blockLocation.getWorld();
        Item item = world.dropItem(blockLocation.clone().add(0.5D, 1.0D, 0.5D), result.clone());
        item.setPickupDelay(20);
    }

    private Item spawnVisualItem(Location blockLocation, ItemStack result) {
        if (blockLocation == null || blockLocation.getWorld() == null) return null;
        Item item = blockLocation.getWorld().dropItem(blockLocation.clone().add(0.5D, 1.05D, 0.5D), result.clone());
        item.setPickupDelay(Integer.MAX_VALUE);
        item.setInvulnerable(true);
        item.setGravity(false);
        visualItems.add(item.getUniqueId());
        return item;
    }

    private void removeVisualItem(NetheriteForgeSession session) {
        UUID visualItemId = session.visualItemId();
        if (visualItemId == null) return;
        visualItems.remove(visualItemId);
        World world = session.blockLocation().getWorld();
        if (world == null) return;
        org.bukkit.entity.Entity entity = world.getEntity(visualItemId);
        if (entity != null && !entity.isDead()) entity.remove();
    }

    private ItemStack yankCraftedResult(Player player, ItemStack expectedResult) {
        ItemStack fromCursor = takeOneMatching(player.getItemOnCursor(), expectedResult, true);
        if (fromCursor != null) {
            player.setItemOnCursor(decrementOne(player.getItemOnCursor()));
            return fromCursor;
        }
        fromCursor = takeOneMatching(player.getItemOnCursor(), expectedResult, false);
        if (fromCursor != null) {
            player.setItemOnCursor(decrementOne(player.getItemOnCursor()));
            return fromCursor;
        }
        PlayerInventory inventory = player.getInventory();
        ItemStack fromStorage = yankFromArray(inventory.getStorageContents(), expectedResult, true, inventory::setStorageContents);
        if (fromStorage != null) return fromStorage;
        ItemStack fromArmor = yankFromArray(inventory.getArmorContents(), expectedResult, true, inventory::setArmorContents);
        if (fromArmor != null) return fromArmor;
        ItemStack fromOffhand = yankFromOffhand(inventory, expectedResult, true);
        if (fromOffhand != null) return fromOffhand;
        fromStorage = yankFromArray(inventory.getStorageContents(), expectedResult, false, inventory::setStorageContents);
        if (fromStorage != null) return fromStorage;
        fromArmor = yankFromArray(inventory.getArmorContents(), expectedResult, false, inventory::setArmorContents);
        if (fromArmor != null) return fromArmor;
        return yankFromOffhand(inventory, expectedResult, false);
    }

    private ItemStack yankFromArray(ItemStack[] contents, ItemStack expectedResult, boolean requireSimilar, java.util.function.Consumer<ItemStack[]> setter) {
        for (int index = 0; index < contents.length; index++) {
            ItemStack taken = takeOneMatching(contents[index], expectedResult, requireSimilar);
            if (taken == null) continue;
            contents[index] = decrementOne(contents[index]);
            setter.accept(contents);
            return taken;
        }
        return null;
    }

    private ItemStack yankFromOffhand(PlayerInventory inventory, ItemStack expectedResult, boolean requireSimilar) {
        ItemStack offhand = inventory.getItemInOffHand();
        ItemStack taken = takeOneMatching(offhand, expectedResult, requireSimilar);
        if (taken == null) return null;
        inventory.setItemInOffHand(decrementOne(offhand));
        return taken;
    }

    private ItemStack takeOneMatching(ItemStack item, ItemStack expectedResult, boolean requireSimilar) {
        if (item == null || item.getType().isAir() || expectedResult == null || expectedResult.getType().isAir()) return null;
        if (requireSimilar && !item.isSimilar(expectedResult)) return null;
        if (!requireSimilar && item.getType() != expectedResult.getType()) return null;
        ItemStack single = item.clone();
        single.setAmount(1);
        return single;
    }

    private ItemStack decrementOne(ItemStack item) {
        if (item == null || item.getType().isAir()) return null;
        ItemStack clone = item.clone();
        clone.setAmount(clone.getAmount() - 1);
        return clone.getAmount() <= 0 ? null : clone;
    }

    public record BlockKey(UUID worldId, int x, int y, int z) {
        public static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private static final class NetheriteForgeSession {
        private final BlockKey key;
        private final UUID owner;
        private final Location blockLocation;
        private final Location lockLocation;
        private final Material originalBlock;
        private final ItemStack result;
        private final double successRate;
        private long remainingMillis;
        private boolean active;
        private boolean pausedNotified;
        private long lastTickMillis;
        private final UUID visualItemId;
        private Scheduler.Task task;

        private NetheriteForgeSession(BlockKey key, UUID owner, Location blockLocation, Location lockLocation, Material originalBlock, ItemStack result, double successRate, long remainingMillis, boolean active, long lastTickMillis, UUID visualItemId, Scheduler.Task task) {
            this.key = key;
            this.owner = owner;
            this.blockLocation = blockLocation;
            this.lockLocation = lockLocation;
            this.originalBlock = originalBlock;
            this.result = result;
            this.successRate = successRate;
            this.remainingMillis = remainingMillis;
            this.active = active;
            this.lastTickMillis = lastTickMillis;
            this.visualItemId = visualItemId;
            this.task = task;
        }

        private BlockKey key() { return key; }
        private UUID owner() { return owner; }
        private Location blockLocation() { return blockLocation; }
        private Location lockLocation() { return lockLocation; }
        private Material originalBlock() { return originalBlock; }
        private ItemStack result() { return result; }
        private double successRate() { return successRate; }
        private long remainingMillis() { return remainingMillis; }
        private void remainingMillis(long remainingMillis) { this.remainingMillis = remainingMillis; }
        private boolean active() { return active; }
        private void active(boolean active) { this.active = active; }
        private boolean pausedNotified() { return pausedNotified; }
        private void pausedNotified(boolean pausedNotified) { this.pausedNotified = pausedNotified; }
        private long lastTickMillis() { return lastTickMillis; }
        private void lastTickMillis(long lastTickMillis) { this.lastTickMillis = lastTickMillis; }
        private UUID visualItemId() { return visualItemId; }
        private Scheduler.Task task() { return task; }
        private void task(Scheduler.Task task) { this.task = task; }
    }
}
