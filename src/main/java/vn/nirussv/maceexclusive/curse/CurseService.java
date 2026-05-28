package vn.nirussv.maceexclusive.curse;

import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.PerformanceConfig;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public final class CurseService implements Listener {

    private static final double DEFAULT_CHAOS_HEALTH_PENALTY = 4.0D;
    private static final double DEFAULT_CHAOS_WATER_DAMAGE = 1.5D;
    private static final double DEFAULT_CHRONOS_HEALTH_MULTIPLIER = 0.85D;

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;
    private final AttributeLease attributeLease;
    private final NamespacedKey maxHealthLeaseKey;
    private final Set<UUID> heldCursePlayers = new HashSet<>();
    private final Set<UUID> waterBackfirePlayers = new HashSet<>();
    private long environmentIntervalTicks;
    private BukkitTask environmentTask;

    public CurseService(Plugin plugin, ConfigManager configManager, ItemMatcher itemMatcher) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemMatcher = itemMatcher;
        this.attributeLease = new AttributeLease();
        this.maxHealthLeaseKey = new NamespacedKey(plugin, "curse_max_health");
    }

    public void start() {
        environmentIntervalTicks = Math.max(1L, configManager.getEnvironmentCurseIntervalTicks());
        environmentTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::tickEnvironmentCurses,
            environmentIntervalTicks,
            environmentIntervalTicks
        );
    }

    public void shutdown() {
        if (environmentTask != null) {
            environmentTask.cancel();
            environmentTask = null;
        }
        for (UUID uuid : new HashSet<>(heldCursePlayers)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null) {
                attributeLease.revokeAll(player);
            }
        }
        heldCursePlayers.clear();
        waterBackfirePlayers.clear();
        attributeLease.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwapHands(PlayerSwapHandItemsEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshNextTick(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            refreshNextTick(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            refreshNextTick(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        refreshNextTick(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        attributeLease.revokeAll(player);
        heldCursePlayers.remove(player.getUniqueId());
        waterBackfirePlayers.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        attributeLease.revokeAll(player);
        heldCursePlayers.remove(player.getUniqueId());
        waterBackfirePlayers.remove(player.getUniqueId());
    }

    private void refreshNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> refresh(player));
    }

    public void refresh(Player player) {
        if (player == null || !player.isOnline() || player.isDead()) {
            return;
        }

        Optional<String> heldItem = heldExclusiveItem(player);
        UUID uuid = player.getUniqueId();

        if (heldItem.isEmpty()) {
            attributeLease.revoke(player, Attribute.GENERIC_MAX_HEALTH);
            heldCursePlayers.remove(uuid);
            waterBackfirePlayers.remove(uuid);
            return;
        }

        String itemId = heldItem.get();
        applyHoldCurse(player, itemId);
        refreshEventDrivenHoldingEffects(player);

        heldCursePlayers.add(uuid);
        if (itemId.equals("chaos_mace")) {
            waterBackfirePlayers.add(uuid);
        } else {
            waterBackfirePlayers.remove(uuid);
        }
    }

    private boolean hasItemInInventory(Player player, String id) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && itemMatcher.is(item, id)) return true;
        }
        return false;
    }

    private Optional<String> heldExclusiveItem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Optional<String> mainMatch = itemMatcher.match(mainHand);
        Optional<String> offHandMatch = itemMatcher.match(player.getInventory().getItemInOffHand());

        if (mainMatch.filter(this::hasHoldCurse).isPresent()) {
            return mainMatch;
        }
        if (offHandMatch.filter(this::hasHoldCurse).isPresent()) {
            return offHandMatch;
        }
        return mainMatch.or(() -> offHandMatch);
    }

    private boolean hasHoldCurse(String itemId) {
        if (itemId.equals("chronos_anchor_spear")) return true;
        double penalty = configManager.getItemCurseDouble(itemId, "max_health_penalty", 0.0D);
        return penalty > 0.0D;
    }

    private void applyHoldCurse(Player player, String itemId) {
        double penalty = configManager.getItemCurseDouble(itemId, "max_health_penalty", 0.0D);
        if (penalty > 0.0D) {
            double amount = -Math.abs(penalty);
            attributeLease.apply(player, Attribute.GENERIC_MAX_HEALTH, maxHealthLeaseKey, amount, AttributeModifier.Operation.ADD_NUMBER);
            return;
        }
        if (itemId.equals("chronos_anchor_spear")) {
            double multiplier = configManager.getItemCurseDouble(itemId, "max_health_multiplier", DEFAULT_CHRONOS_HEALTH_MULTIPLIER);
            double scalar = Math.min(0.0D, multiplier - 1.0D);
            attributeLease.apply(player, Attribute.GENERIC_MAX_HEALTH, maxHealthLeaseKey, scalar, AttributeModifier.Operation.ADD_SCALAR);
            return;
        }
        attributeLease.revoke(player, Attribute.GENERIC_MAX_HEALTH);
    }

    private void refreshEventDrivenHoldingEffects(Player player) {
        PerformanceConfig performance = configManager.getPerformanceConfig();
        if (performance.holdingGlowing()) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 40, 0, false, false, false));
        }
        if (performance.holdingSoulParticles()) {
            player.getWorld().spawnParticle(
                Particle.SOUL,
                player.getLocation(),
                performance.particleCount(),
                performance.particleOffsetX(),
                performance.particleOffsetY(),
                performance.particleOffsetZ(),
                performance.particleExtra()
            );
        }
    }

    private void tickEnvironmentCurses() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) continue;

            // 1. Water contact check (Wither II for 3s)
            if (player.isInWater() || player.getLocation().getBlock().getType() == org.bukkit.Material.WATER) {
                boolean hasChaos = hasItemInInventory(player, "chaos_mace");
                boolean hasVoid = hasItemInInventory(player, "void_mace");
                if (hasChaos || hasVoid) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1, false, false, true));
                }
            }

            // 2. Void Mace Devoid of Light check (Blindness in light level < 7)
            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            boolean holdingVoid = itemMatcher.is(mainHand, "void_mace") || itemMatcher.is(offHand, "void_mace");
            if (holdingVoid) {
                int threshold = configManager.getItemEffectInt("void_mace", "effects.curses.light_level_threshold", 7);
                if (player.getLocation().getBlock().getLightLevel() < threshold) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 0, false, false, false));
                }
            }
        }
    }
}
