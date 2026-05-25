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
import vn.nirussv.maceexclusive.item.ExclusiveItemId;
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

        Optional<ExclusiveItemId> heldItem = heldExclusiveItem(player);
        UUID uuid = player.getUniqueId();

        if (heldItem.isEmpty()) {
            attributeLease.revoke(player, Attribute.GENERIC_MAX_HEALTH);
            heldCursePlayers.remove(uuid);
            waterBackfirePlayers.remove(uuid);
            return;
        }

        ExclusiveItemId itemId = heldItem.get();
        applyHoldCurse(player, itemId);
        refreshEventDrivenHoldingEffects(player);

        heldCursePlayers.add(uuid);
        if (itemId == ExclusiveItemId.CHAOS_MACE) {
            waterBackfirePlayers.add(uuid);
        } else {
            waterBackfirePlayers.remove(uuid);
        }
    }

    private Optional<ExclusiveItemId> heldExclusiveItem(Player player) {
        ItemStack mainHand = player.getInventory().getItemInMainHand();
        Optional<ExclusiveItemId> mainMatch = itemMatcher.match(mainHand);
        Optional<ExclusiveItemId> offHandMatch = itemMatcher.match(player.getInventory().getItemInOffHand());

        if (mainMatch.filter(this::hasHoldCurse).isPresent()) {
            return mainMatch;
        }
        if (offHandMatch.filter(this::hasHoldCurse).isPresent()) {
            return offHandMatch;
        }
        return mainMatch.or(() -> offHandMatch);
    }

    private boolean hasHoldCurse(ExclusiveItemId itemId) {
        return itemId == ExclusiveItemId.CHAOS_MACE || itemId == ExclusiveItemId.CHRONOS_ANCHOR_SPEAR;
    }

    private void applyHoldCurse(Player player, ExclusiveItemId itemId) {
        switch (itemId) {
            case CHAOS_MACE -> {
                double amount = -Math.abs(configManager.getItemCurseDouble(
                    itemId.id(), "max_health_penalty", DEFAULT_CHAOS_HEALTH_PENALTY));
                attributeLease.apply(player, Attribute.GENERIC_MAX_HEALTH, maxHealthLeaseKey, amount,
                    AttributeModifier.Operation.ADD_NUMBER);
            }
            case CHRONOS_ANCHOR_SPEAR -> {
                double multiplier = configManager.getItemCurseDouble(
                    itemId.id(), "max_health_multiplier", DEFAULT_CHRONOS_HEALTH_MULTIPLIER);
                double scalar = Math.min(0.0D, multiplier - 1.0D);
                attributeLease.apply(player, Attribute.GENERIC_MAX_HEALTH, maxHealthLeaseKey, scalar,
                    AttributeModifier.Operation.ADD_SCALAR);
            }
            case POWER_MACE -> attributeLease.revoke(player, Attribute.GENERIC_MAX_HEALTH);
        }
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
        if (waterBackfirePlayers.isEmpty()) {
            return;
        }

        double waterDamagePerSecond = Math.max(0.0D, configManager.getItemCurseDouble(
            ExclusiveItemId.CHAOS_MACE.id(), "water_damage_per_second", DEFAULT_CHAOS_WATER_DAMAGE));
        if (waterDamagePerSecond <= 0.0D) {
            return;
        }
        double tickDamage = waterDamagePerSecond * Math.max(1L, environmentIntervalTicks) / 20.0D;

        for (UUID uuid : new HashSet<>(waterBackfirePlayers)) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player == null || !player.isOnline() || player.isDead()) {
                waterBackfirePlayers.remove(uuid);
                heldCursePlayers.remove(uuid);
                continue;
            }

            Optional<ExclusiveItemId> heldItem = heldExclusiveItem(player);
            if (heldItem.filter(id -> id == ExclusiveItemId.CHAOS_MACE).isEmpty()) {
                refresh(player);
                continue;
            }

            if (player.isInWater()) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 60, 0, false, false, false));
                player.setHealth(Math.max(0.0D, player.getHealth() - tickDamage));
            }
        }
    }
}
