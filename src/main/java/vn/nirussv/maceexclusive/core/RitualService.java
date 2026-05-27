package vn.nirussv.maceexclusive.core;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.item.ItemMatcher;

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
    private final Random random = new Random();
    private final Map<UUID, Long> processingItems = new HashMap<>();

    public RitualService(CoreItemFactory coreItemFactory, ItemMatcher itemMatcher) {
        this.coreItemFactory = coreItemFactory;
        this.itemMatcher = itemMatcher;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWardenDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.WARDEN) return;
        Optional<Item> heavyCore = nearestHeavyCore(event.getEntity().getLocation(), 5.0D, Material.SCULK_CATALYST);
        if (heavyCore.isEmpty()) return;
        transformOne(heavyCore.get(), "sculk_core");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onChronoPortal(EntityPortalEvent event) {
        if (!(event.getEntity() instanceof Item item)) return;
        if (item.getItemStack().getType() != Material.HEAVY_CORE) return;
        if (event.getTo() == null || event.getTo().getWorld() == null) return;
        if (event.getTo().getWorld().getEnvironment() != World.Environment.THE_END) return;
        String result = random.nextBoolean() ? "chrono_core" : "ruined_core";
        if (transformOne(item, result)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBloodSacrifice(PlayerInteractEntityEvent event) {
        Entity clicked = event.getRightClicked();
        if (!isBloodSacrificeEntity(clicked)) return;
        ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
        if (hand == null || hand.getType() != Material.NETHERITE_SWORD) return;
        if (event.getPlayer().getWorld().getEnvironment() != World.Environment.NETHER) return;
        Optional<Item> heavyCore = nearestHeavyCore(clicked.getLocation(), 6.0D, Material.CRIMSON_NYLIUM);
        if (heavyCore.isEmpty()) return;
        clicked.getWorld().strikeLightningEffect(clicked.getLocation());
        if (clicked instanceof Player victim) victim.damage(6.0D, event.getPlayer()); else clicked.remove();
        if (transformOne(heavyCore.get(), "blood_core")) event.setCancelled(true);
    }

    private boolean isBloodSacrificeEntity(Entity entity) {
        return entity.getType() == EntityType.PIGLIN || entity.getType() == EntityType.PIGLIN_BRUTE || entity instanceof Player;
    }

    private Optional<Item> nearestHeavyCore(Location center, double radius, Material altarMaterial) {
        if (center == null || center.getWorld() == null) return Optional.empty();
        return center.getWorld().getNearbyEntities(center, radius, radius, radius, entity -> entity instanceof Item)
            .stream()
            .map(Item.class::cast)
            .filter(item -> item.isValid() && !item.isDead())
            .filter(item -> item.getItemStack().getType() == Material.HEAVY_CORE)
            .filter(item -> isOnAltar(item, altarMaterial))
            .min(Comparator.comparingDouble(item -> item.getLocation().distanceSquared(center)));
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
        location.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, location, 48, 0.5, 0.5, 0.5, 0.03);
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
