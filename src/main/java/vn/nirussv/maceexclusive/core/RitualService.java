package vn.nirussv.maceexclusive.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.maceexclusive.effect.SafeParticleSpawner;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.curse.LockoutService;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;

public final class RitualService implements Listener {

    private static final long PROCESSING_TTL_MILLIS = 1000L;

    private final CoreItemFactory coreItemFactory;
    private final ItemMatcher itemMatcher;
    private final LockoutService lockoutService;
    private final ConfigManager configManager;
    private final ExclusiveItemFactory itemFactory;
    private final Random random = new Random();
    private final Map<UUID, Long> processingItems = new HashMap<>();
    private final Map<UUID, Long> lastSculkCurseCheck = new HashMap<>();

    public RitualService(CoreItemFactory coreItemFactory, ItemMatcher itemMatcher, LockoutService lockoutService, ConfigManager configManager, ExclusiveItemFactory itemFactory) {
        this.coreItemFactory = coreItemFactory;
        this.itemMatcher = itemMatcher;
        this.lockoutService = lockoutService;
        this.configManager = configManager;
        this.itemFactory = itemFactory;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWardenDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.WARDEN) return;
        Player killer = event.getEntity().getKiller();
        if (killer != null && random.nextDouble() < 0.10D) {
            event.getDrops().add(itemFactory.create("warden_resonance_shard"));
            killer.sendMessage(configManager.getMessage("core.sculk-shard-drop"));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) { applySculkHeldCurse(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) { applySculkHeldCurse(event.getPlayer()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBloodHunt(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Monster)) return;
        if (event.getEntity().getWorld().getEnvironment() != World.Environment.NETHER) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null || !consumeInventoryRitualCoreOnChance(killer, 0.05D)) return;

        Location location = event.getEntity().getLocation();
        dropCoreWithEffect(location, "blood_ritual_core");
        killer.sendMessage(configManager.getMessage("core.blood-hunt-success"));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onReaperRitual(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (player.getWorld().getEnvironment() != World.Environment.NETHER) return;
        if (!wasKilledByWitherSkeleton(player)) return;
        if (!hasRitualCoreInHand(player)) return;

        if (random.nextDouble() >= 0.20D) {
            player.sendMessage(configManager.getMessage("core.reaper-ritual-fail"));
            return;
        }

        consumeRitualCoreFromHands(player);
        removeOneRitualCoreDrop(event);
        dropCoreWithEffect(player.getLocation(), "reaper_ritual_core");
        player.sendMessage(configManager.getMessage("core.reaper-ritual-success"));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEndPortalRitual(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        ItemStack stack = item.getItemStack();
        if (stack.getType() != Material.HEAVY_CORE) return;
        if (event.getTo() == null || event.getTo().getWorld() == null) return;
        if (event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) return;

        Optional<String> coreId = itemMatcher.matchCore(stack);
        if (coreId.isEmpty()) {
            handleEndCoreRitual(item, event);
            return;
        }
        if ("ritual_core".equals(coreId.get())) {
            handleVoidCoreRitual(item, event);
        }
    }

    private void handleEndCoreRitual(Item item, EntityPortalEvent event) {
        String result = "end_core";
        if (random.nextDouble() >= 0.20D) {
            result = "ruined_core";
            UUID throwerUid = item.getThrower();
            if (throwerUid != null) {
                Player player = org.bukkit.Bukkit.getPlayer(throwerUid);
                if (player != null) {
                    lockoutService.applyCursed(player.getUniqueId(), configManager.getCoreCraftLockoutSeconds());
                    player.sendMessage(configManager.getMessage("core.craft-failed"));
                }
            }
        }
        if (transformOne(item, result)) event.setCancelled(true);
    }

    private void handleVoidCoreRitual(Item item, EntityPortalEvent event) {
        UUID throwerUid = item.getThrower();
        if (throwerUid == null) return;
        Player player = org.bukkit.Bukkit.getPlayer(throwerUid);
        if (player == null) return;

        ItemStack challengerEye = null;
        int eyeSlot = -1;
        for (int i = 0; i < player.getInventory().getContents().length; i++) {
            ItemStack invItem = player.getInventory().getItem(i);
            if (invItem != null && itemMatcher.is(invItem, "challenger_eye")) {
                challengerEye = invItem;
                eyeSlot = i;
                break;
            }
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (challengerEye == null && offHand != null && itemMatcher.is(offHand, "challenger_eye")) {
            challengerEye = offHand;
            eyeSlot = -2;
        }
        if (challengerEye == null) return;

        event.setCancelled(true);
        Location location = item.getLocation();
        if (location.getWorld() == null) return;

        consumeOne(item, item.getItemStack());
        if (eyeSlot == -2) {
            challengerEye.setAmount(challengerEye.getAmount() - 1);
            player.getInventory().setItemInOffHand(challengerEye.getAmount() > 0 ? challengerEye : null);
        } else if (eyeSlot >= 0) {
            challengerEye.setAmount(challengerEye.getAmount() - 1);
            player.getInventory().setItem(eyeSlot, challengerEye.getAmount() > 0 ? challengerEye : null);
        }

        SafeParticleSpawner.spawn(location.getWorld(), Particle.PORTAL, location, 64, 0.8, 0.8, 0.8, 0.1);
        location.getWorld().playSound(location, Sound.BLOCK_END_PORTAL_FRAME_FILL, 1.0f, 0.5f);

        if (random.nextDouble() < 0.50D) {
            location.getWorld().dropItemNaturally(location, coreItemFactory.create("void_ritual_core"));
            player.sendMessage(configManager.getMessage("core.craft-success"));
        } else {
            player.sendMessage(configManager.getMessage("core.craft-failed"));
        }
    }

    private Optional<Item> nearestRitualCore(Location center, double radius, Material altarMaterial) {
        if (center == null || center.getWorld() == null) return Optional.empty();
        return center.getWorld().getNearbyEntities(center, radius, radius, radius, entity -> entity instanceof Item)
            .stream()
            .map(Item.class::cast)
            .filter(item -> item.isValid() && !item.isDead())
            .filter(item -> isRitualCore(item.getItemStack()))
            .filter(item -> isOnAltar(item, altarMaterial))
            .min(Comparator.comparingDouble(item -> item.getLocation().distanceSquared(center)));
    }

    private boolean consumeInventoryRitualCoreOnChance(Player player, double chance) {
        if (random.nextDouble() >= chance) return false;
        return consumeRitualCoreFromInventory(player);
    }

    private boolean consumeRitualCoreFromInventory(Player player) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (!isRitualCore(item)) continue;
            decrementInventorySlot(player, slot, item);
            return true;
        }
        return consumeRitualCoreFromOffHand(player);
    }

    private void decrementInventorySlot(Player player, int slot, ItemStack item) {
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItem(slot, item.getAmount() > 0 ? item : null);
    }

    private boolean consumeRitualCoreFromOffHand(Player player) {
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (!isRitualCore(offHand)) return false;
        offHand.setAmount(offHand.getAmount() - 1);
        player.getInventory().setItemInOffHand(offHand.getAmount() > 0 ? offHand : null);
        return true;
    }

    private boolean hasRitualCoreInHand(Player player) {
        return isRitualCore(player.getInventory().getItemInMainHand())
            || isRitualCore(player.getInventory().getItemInOffHand());
    }

    private void consumeRitualCoreFromHands(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        if (isRitualCore(mainHand)) {
            decrementMainHand(player, mainHand);
            return;
        }
        consumeRitualCoreFromOffHand(player);
    }

    private void decrementMainHand(Player player, ItemStack item) {
        item.setAmount(item.getAmount() - 1);
        player.getInventory().setItemInMainHand(item.getAmount() > 0 ? item : null);
    }

    private void removeOneRitualCoreDrop(PlayerDeathEvent event) {
        for (Iterator<ItemStack> iterator = event.getDrops().iterator(); iterator.hasNext();) {
            ItemStack drop = iterator.next();
            if (!isRitualCore(drop)) continue;
            drop.setAmount(drop.getAmount() - 1);
            if (drop.getAmount() <= 0) iterator.remove();
            return;
        }
    }

    private boolean wasKilledByWitherSkeleton(Player player) {
        if (!(player.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent)) return false;
        return damageEvent.getDamager().getType() == EntityType.WITHER_SKELETON;
    }

    private void applySculkHeldCurse(Player player) {
        if (!itemMatcher.isCore(player.getInventory().getItemInMainHand(), "sculk_ritual_core")
            && !itemMatcher.isCore(player.getInventory().getItemInOffHand(), "sculk_ritual_core")) return;
        long now = System.currentTimeMillis();
        long last = lastSculkCurseCheck.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < 20_000L) return;
        lastSculkCurseCheck.put(player.getUniqueId(), now);
        if (random.nextDouble() >= 0.20D) return;
        player.addPotionEffect(new PotionEffect(resolveDarknessEffect(), 20 * 10, 0, false, true, true));
        player.sendActionBar(configManager.getMessage("core.sculk-curse"));
    }

    private PotionEffectType resolveDarknessEffect() {
        PotionEffectType darkness = PotionEffectType.getByName("DARKNESS");
        return darkness == null ? PotionEffectType.BLINDNESS : darkness;
    }

    private boolean isRitualCore(ItemStack item) {
        return itemMatcher.isCore(item, "ritual_core");
    }

    private void dropCoreWithEffect(Location location, String coreId) {
        if (location == null || location.getWorld() == null) return;
        SafeParticleSpawner.spawn(location.getWorld(), Particle.SOUL_FIRE_FLAME, location, 48, 0.5, 0.5, 0.5, 0.03);
        location.getWorld().playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.7f);
        location.getWorld().dropItemNaturally(location, coreItemFactory.create(coreId));
    }

    private void notifyKiller(EntityDeathEvent event, String messageKey) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) killer.sendMessage(configManager.getMessage(messageKey));
    }

    private boolean isOnAltar(Item item, Material altarMaterial) {
        Location location = item.getLocation();
        if (location.getWorld() == null) return false;
        Block block = location.getBlock().getRelative(0, -1, 0);
        return block.getType() == altarMaterial;
    }

    private boolean transformOne(Item item, String coreId) {
        cleanupProcessing();
        if (item == null || item.isDead() || !item.isValid()) return false;
        if (processingItems.containsKey(item.getUniqueId())) return false;
        ItemStack stack = item.getItemStack();
        if (stack == null || stack.getAmount() <= 0) return false;
        Location location = item.getLocation();
        if (location.getWorld() == null) return false;

        processingItems.put(item.getUniqueId(), System.currentTimeMillis() + PROCESSING_TTL_MILLIS);
        SafeParticleSpawner.spawn(location.getWorld(), Particle.SOUL_FIRE_FLAME, location, 48, 0.5, 0.5, 0.5, 0.03);
        location.getWorld().playSound(location, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.0f, 0.7f);
        location.getWorld().dropItemNaturally(location, coreItemFactory.create(coreId));
        consumeOne(item, stack);
        return true;
    }

    private void consumeOne(Item item, ItemStack stack) {
        if (stack.getAmount() <= 1) {
            item.remove();
            return;
        }
        stack.setAmount(stack.getAmount() - 1);
        item.setItemStack(stack);
    }

    private void cleanupProcessing() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<UUID, Long>> iterator = processingItems.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue() <= now) iterator.remove();
        }
    }
}
