package vn.nirussv.maceexclusive.curse;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerExpChangeEvent;
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
import vn.nirussv.maceexclusive.util.Scheduler;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class CurseService implements Listener {

    private static final double DEFAULT_CHAOS_HEALTH_PENALTY = 4.0D;
    private static final double DEFAULT_CHAOS_WATER_DAMAGE = 1.5D;

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;
    private final AttributeLease attributeLease;
    private final NamespacedKey maxHealthLeaseKey;
    private final Set<UUID> heldCursePlayers = new HashSet<>();
    private final Set<UUID> waterBackfirePlayers = new HashSet<>();
    private final Random random = new Random();
    private long coreCurseTickCounter;
    private long environmentIntervalTicks;
    private BukkitTask environmentTask;
    private BukkitTask particleTask;

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
        long particleTickRate = Math.max(1L, configManager.getPerformanceConfig().holdingEffectTickRate());
        particleTask = plugin.getServer().getScheduler().runTaskTimer(
            plugin,
            this::tickFeetParticles,
            particleTickRate,
            particleTickRate
        );
    }

    public void shutdown() {
        if (environmentTask != null) {
            environmentTask.cancel();
            environmentTask = null;
        }
        if (particleTask != null) {
            particleTask.cancel();
            particleTask = null;
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
        Scheduler.runEntityTask(plugin, player, () -> refresh(player));
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
            player.removePotionEffect(PotionEffectType.WEAKNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
            return;
        }

        String itemId = heldItem.get();
        applyHoldCurse(player, itemId);
        refreshEventDrivenHoldingEffects(player, itemId);

        heldCursePlayers.add(uuid);
        if (itemId.equals("chaos_mace")) {
            waterBackfirePlayers.add(uuid);
        } else {
            waterBackfirePlayers.remove(uuid);
        }
        if (!itemId.equals("cursed_sword")) {
            player.removePotionEffect(PotionEffectType.WEAKNESS);
            player.removePotionEffect(PotionEffectType.SLOWNESS);
        }
    }

    private boolean hasItemInInventory(Player player, String id) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && itemMatcher.is(item, id)) return true;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && itemMatcher.is(offHand, id)) return true;
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
        attributeLease.revoke(player, Attribute.GENERIC_MAX_HEALTH);
    }

    private void refreshEventDrivenHoldingEffects(Player player, String itemId) {
        PerformanceConfig performance = configManager.getPerformanceConfig();
        boolean isWeapon = itemId.endsWith("_mace") || itemId.endsWith("_spear") || itemId.equals("cursed_sword");
        boolean itemGlowing = configManager.getItemCurseBoolean(itemId, "hold.glowing", performance.holdingGlowing());
        if (isWeapon && itemGlowing) {
            int durationTicks = Math.max(40, performance.holdingGlowingDurationSeconds() * 20);
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, durationTicks, 0, false, false, false));
        }
        if ("cursed_sword".equals(itemId)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 40, 0, false, false, true));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 40, 0, false, false, true));
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
        coreCurseTickCounter += environmentIntervalTicks;
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) continue;

            // 0. Void Mace Totem Curse (Drop all totems from inventory if void_mace is carried)
            if (hasItemInInventory(player, "void_mace")) {
                boolean dropped = false;
                for (int i = 0; i < player.getInventory().getSize(); i++) {
                    ItemStack item = player.getInventory().getItem(i);
                    if (item != null && item.getType() == Material.TOTEM_OF_UNDYING) {
                        player.getInventory().setItem(i, null);
                        player.getWorld().dropItemNaturally(player.getLocation(), item);
                        dropped = true;
                    }
                }
                ItemStack offHand = player.getInventory().getItemInOffHand();
                if (offHand != null && offHand.getType() == Material.TOTEM_OF_UNDYING) {
                    player.getInventory().setItemInOffHand(null);
                    player.getWorld().dropItemNaturally(player.getLocation(), offHand);
                    dropped = true;
                }
                if (dropped) {
                    player.sendMessage(configManager.getMessage("special.totem-dropped"));
                }
            }

            // 1. Water contact check (Wither II for 3s)
            if (player.isInWater() || player.getLocation().getBlock().getType() == org.bukkit.Material.WATER) {
                boolean hasChaos = hasItemInInventory(player, "chaos_mace");
                boolean hasVoid = hasItemInInventory(player, "void_mace");
                if (hasChaos || hasVoid) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 60, 1, false, false, true));
                }
                if (hasItemInInventory(player, "soulfire_mace")) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1, false, false, true));
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

            // 3. Periodic holding effects refresh (glowing / particles)
            heldExclusiveItem(player).ifPresent(itemId -> refreshEventDrivenHoldingEffects(player, itemId));

            tickCoreInstability(player);
        }
    }

    private void tickCoreInstability(Player player) {
        if (coreCurseTickCounter % (20L * 10L) == 0L) {
            if (hasCoreInInventory(player, "blood_core") && random.nextDouble() < 0.10D) {
                player.damage(2.0D);
                player.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, player.getLocation().add(0, 1.0, 0), 8, 0.3, 0.4, 0.3, 0.02);
            }
            if (hasCoreInInventory(player, "sculk_core") && random.nextDouble() < 0.19D) {
                PotionEffectType darkness = PotionEffectType.getByName("DARKNESS");
                if (darkness == null) darkness = PotionEffectType.BLINDNESS;
                player.addPotionEffect(new PotionEffect(darkness, 20 * 29, 0, false, true, true));
                player.getWorld().spawnParticle(Particle.SCULK_SOUL, player.getLocation().add(0, 1.0, 0), 24, 0.5, 0.6, 0.5, 0.03);
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_WARDEN_HEARTBEAT, 0.8f, 0.7f);
            }
        }
        if (coreCurseTickCounter % (20L * 30L) == 0L) {
            if (hasCoreInInventory(player, "soulfire_core") && random.nextDouble() < 0.10D) {
                player.setFireTicks(100);
                player.getWorld().playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.5f);
                player.sendActionBar(configManager.getMessage("core.soulfire-burn"));
            }
        }
        if (coreCurseTickCounter % (20L * 60L) == 0L && hasCoreInInventory(player, "end_core") && random.nextDouble() < 0.10D) {
            teleportNearbySafely(player);
        }
    }

    private boolean hasCoreInInventory(Player player, String id) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && itemMatcher.isCore(item, id)) return true;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && itemMatcher.isCore(offHand, id)) return true;
        return false;
    }

    private boolean isHoldingCore(Player player, String id) {
        return itemMatcher.isCore(player.getInventory().getItemInMainHand(), id)
            || itemMatcher.isCore(player.getInventory().getItemInOffHand(), id);
    }

    private void teleportNearbySafely(Player player) {
        Location origin = player.getLocation();
        if (origin.getWorld() == null) return;
        for (int attempt = 0; attempt < 18; attempt++) {
            int dx = random.nextInt(33) - 16;
            int dz = random.nextInt(33) - 16;
            Location candidate = origin.clone().add(dx, 0, dz);
            candidate.setY(origin.getWorld().getHighestBlockYAt(candidate) + 1.0D);
            Material feet = candidate.getBlock().getType();
            Material head = candidate.clone().add(0, 1, 0).getBlock().getType();
            Material below = candidate.clone().add(0, -1, 0).getBlock().getType();
            if (feet.isAir() && head.isAir() && below.isSolid() && below != Material.LAVA && below != Material.FIRE) {
                origin.getWorld().spawnParticle(Particle.REVERSE_PORTAL, origin.add(0, 1, 0), 32, 0.5, 0.6, 0.5, 0.05);
                player.teleport(candidate.setDirection(origin.getDirection()));
                player.getWorld().spawnParticle(Particle.PORTAL, player.getLocation().add(0, 1, 0), 32, 0.5, 0.6, 0.5, 0.05);
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.0f);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onXpPickup(PlayerExpChangeEvent event) {
        Player player = event.getPlayer();
        if (hasCoreInInventory(player, "ego_core")) {
            event.setAmount(0);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (hasCoreInInventory(attacker, "ego_core")) {
            double attackerY = attacker.getLocation().getY();
            double targetY = event.getEntity().getLocation().getY();
            if (targetY >= attackerY) {
                double damage = 2.0D + (random.nextDouble() * 2.0D);
                attacker.damage(damage);
                attacker.getWorld().spawnParticle(Particle.DAMAGE_INDICATOR, attacker.getLocation().add(0, 1.0, 0), 8, 0.3, 0.4, 0.3, 0.02);
                attacker.getWorld().playSound(attacker.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 0.5f);
                java.util.Map<String, String> placeholders = java.util.Map.of(
                    "damage", String.format("%.1f", damage / 2.0)
                );
                attacker.sendActionBar(configManager.getMessage("core.ego-backfire", placeholders));
            }
        }
    }

    private void tickFeetParticles() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) continue;
            Particle particle = getFeetParticleType(player);
            if (particle != null) {
                spawnFeetParticles(player, particle);
            }
        }
    }

    private Particle getFeetParticleType(Player player) {
        boolean hasPower = false, hasEgo = false;
        boolean hasSoulfireMace = false, hasSoulfireCore = false;
        boolean hasSonic = false, hasSculk = false;
        boolean hasVoid = false, hasEnd = false;
        boolean hasChaosMace = false, hasChaosCore = false;
        boolean hasVampiric = false, hasBlood = false;
        boolean hasCursedSword = false, hasRuined = false;
        boolean hasChronos = false;

        for (ItemStack item : player.getInventory().getContents()) {
            if (item == null) continue;
            Optional<String> idOpt = itemMatcher.match(item);
            if (idOpt.isPresent()) {
                String id = idOpt.get();
                switch (id) {
                    case "power_mace" -> hasPower = true;
                    case "soulfire_mace" -> hasSoulfireMace = true;
                    case "sonic_mace" -> hasSonic = true;
                    case "void_mace" -> hasVoid = true;
                    case "chaos_mace" -> hasChaosMace = true;
                    case "vampiric_mace" -> hasVampiric = true;
                    case "cursed_sword" -> hasCursedSword = true;
                    case "chronos_anchor_spear" -> hasChronos = true;
                }
            }
            Optional<String> coreOpt = itemMatcher.matchCore(item);
            if (coreOpt.isPresent()) {
                String id = coreOpt.get();
                switch (id) {
                    case "ego_core" -> hasEgo = true;
                    case "soulfire_core" -> hasSoulfireCore = true;
                    case "sculk_core" -> hasSculk = true;
                    case "end_core" -> hasEnd = true;
                    case "chaos_core" -> hasChaosCore = true;
                    case "blood_core" -> hasBlood = true;
                    case "ruined_core" -> hasRuined = true;
                }
            }
        }

        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null) {
            Optional<String> idOpt = itemMatcher.match(offHand);
            if (idOpt.isPresent()) {
                String id = idOpt.get();
                switch (id) {
                    case "power_mace" -> hasPower = true;
                    case "soulfire_mace" -> hasSoulfireMace = true;
                    case "sonic_mace" -> hasSonic = true;
                    case "void_mace" -> hasVoid = true;
                    case "chaos_mace" -> hasChaosMace = true;
                    case "vampiric_mace" -> hasVampiric = true;
                    case "cursed_sword" -> hasCursedSword = true;
                    case "chronos_anchor_spear" -> hasChronos = true;
                }
            }
            Optional<String> coreOpt = itemMatcher.matchCore(offHand);
            if (coreOpt.isPresent()) {
                String id = coreOpt.get();
                switch (id) {
                    case "ego_core" -> hasEgo = true;
                    case "soulfire_core" -> hasSoulfireCore = true;
                    case "sculk_core" -> hasSculk = true;
                    case "end_core" -> hasEnd = true;
                    case "chaos_core" -> hasChaosCore = true;
                    case "blood_core" -> hasBlood = true;
                    case "ruined_core" -> hasRuined = true;
                }
            }
        }

        if (hasPower || hasEgo) return Particle.CRIT;
        if (hasSoulfireMace || hasSoulfireCore) return Particle.SOUL_FIRE_FLAME;
        if (hasSonic || hasSculk) return Particle.SCULK_SOUL;
        if (hasVoid || hasEnd) return Particle.PORTAL;
        if (hasChaosMace || hasChaosCore) return Particle.WITCH;
        if (hasVampiric || hasBlood) return Particle.DAMAGE_INDICATOR;
        if (hasCursedSword || hasRuined) return Particle.SMOKE;
        if (hasChronos) return Particle.GLOW;

        return null;
    }

    private void spawnFeetParticles(Player player, Particle particle) {
        Location loc = player.getLocation();
        World world = loc.getWorld();
        if (world == null) return;
        double radius = 0.5;
        for (int i = 0; i < 8; i++) {
            double angle = i * (Math.PI / 4.0);
            double dx = Math.cos(angle) * radius;
            double dz = Math.sin(angle) * radius;
            world.spawnParticle(particle, loc.clone().add(dx, 0.05, dz), 1, 0, 0, 0, 0);
        }
    }
}
