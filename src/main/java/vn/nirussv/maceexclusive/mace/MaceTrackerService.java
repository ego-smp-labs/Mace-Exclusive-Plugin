package vn.nirussv.maceexclusive.mace;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class MaceTrackerService implements Listener {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final MaceRepository repository;
    private final ItemMatcher itemMatcher;

    private final Map<String, Item> droppedMaces = new ConcurrentHashMap<>();
    private final Map<String, BossBar> activeBossBars = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> activeTasks = new ConcurrentHashMap<>();
    private final Map<String, Integer> elapsedSeconds = new ConcurrentHashMap<>();

    public MaceTrackerService(Plugin plugin, ConfigManager configManager, MaceRepository repository, ItemMatcher itemMatcher) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.repository = repository;
        this.itemMatcher = itemMatcher;
    }

    public void startTracking(String maceId) {
        if (maceId == null) return;
        maceId = maceId.toLowerCase();

        // Stop existing task if any
        stopTracking(maceId);

        // Determine BossBar color
        BarColor color = getBarColor(maceId);

        // Create BossBar
        String displayName = getDisplayName(maceId);
        String searchingText = configManager.getRawMessage("tracking.searching");
        String initialTitle = configManager.getRawMessage("tracking.unlocated-format")
            .replace("%name%", displayName)
            .replace("%status%", searchingText);
        BossBar bossBar = Bukkit.createBossBar(
            org.bukkit.ChatColor.translateAlternateColorCodes('&', initialTitle),
            color,
            BarStyle.SOLID
        );
        bossBar.setVisible(true);

        // Add all online players
        for (Player player : Bukkit.getOnlinePlayers()) {
            bossBar.addPlayer(player);
        }

        activeBossBars.put(maceId, bossBar);
        elapsedSeconds.put(maceId, 0);

        final String finalMaceId = maceId;
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tickTracker(finalMaceId), 0L, 20L);
        activeTasks.put(maceId, task);
    }

    public void stopTracking(String maceId) {
        if (maceId == null) return;
        maceId = maceId.toLowerCase();

        BukkitTask task = activeTasks.remove(maceId);
        if (task != null) {
            task.cancel();
        }

        BossBar bossBar = activeBossBars.remove(maceId);
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }

        elapsedSeconds.remove(maceId);
    }

    public void shutdown() {
        for (String maceId : new ArrayList<>(activeTasks.keySet())) {
            stopTracking(maceId);
        }
    }

    private void tickTracker(String maceId) {
        BossBar bossBar = activeBossBars.get(maceId);
        Integer elapsed = elapsedSeconds.get(maceId);
        if (bossBar == null || elapsed == null) {
            stopTracking(maceId);
            return;
        }

        int trackingDurationSeconds = configManager.getTrackingDurationSeconds();
        if (elapsed >= trackingDurationSeconds) {
            stopTracking(maceId);
            return;
        }

        elapsed++;
        elapsedSeconds.put(maceId, elapsed);

        // Update progress
        double progress = (double) (trackingDurationSeconds - elapsed) / trackingDurationSeconds;
        bossBar.setProgress(Math.max(0.0, Math.min(1.0, progress)));

        // Locate the mace
        Location loc = null;
        String statusLabel = configManager.getRawMessage("tracking.offline");

        Item dropped = droppedMaces.get(maceId);
        if (dropped != null && dropped.isValid() && !dropped.isDead()) {
            loc = dropped.getLocation();
            statusLabel = configManager.getRawMessage("tracking.dropped");
        } else {
            UUID holderUuid = repository.getHolder(maceId);
            if (holderUuid != null) {
                Player holder = Bukkit.getPlayer(holderUuid);
                if (holder != null && holder.isOnline()) {
                    loc = holder.getLocation();
                    statusLabel = holder.getName();
                }
            }
        }

        String displayName = getDisplayName(maceId);
        String titleText;
        if (loc != null && loc.getWorld() != null) {
            titleText = configManager.getRawMessage("tracking.located-format")
                .replace("%name%", displayName)
                .replace("%status%", statusLabel)
                .replace("%world%", loc.getWorld().getName())
                .replace("%x%", String.valueOf(loc.getBlockX()))
                .replace("%y%", String.valueOf(loc.getBlockY()))
                .replace("%z%", String.valueOf(loc.getBlockZ()));
        } else {
            titleText = configManager.getRawMessage("tracking.unlocated-format")
                .replace("%name%", displayName)
                .replace("%status%", statusLabel);
        }

        bossBar.setTitle(org.bukkit.ChatColor.translateAlternateColorCodes('&', titleText));

        // Sync players
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!bossBar.getPlayers().contains(p)) {
                bossBar.addPlayer(p);
            }
        }
        for (Player p : new ArrayList<>(bossBar.getPlayers())) {
            if (!p.isOnline()) {
                bossBar.removePlayer(p);
            }
        }
    }

    private BarColor getBarColor(String maceId) {
        return switch (maceId) {
            case "chaos_mace" -> BarColor.PURPLE;
            case "void_mace" -> BarColor.BLUE;
            case "vampiric_mace" -> BarColor.RED;
            case "gravity_mace" -> BarColor.PINK;
            case "power_mace" -> BarColor.YELLOW;
            case "sonic_mace" -> BarColor.GREEN;
            default -> BarColor.WHITE;
        };
    }

    private String getDisplayName(String maceId) {
        ItemConfig cfg = configManager.getItemConfig(maceId);
        return cfg == null ? maceId : cfg.name();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        Optional<String> matched = itemMatcher.match(item);
        if (matched.isPresent()) {
            String maceId = matched.get().toLowerCase();
            if (configManager.isSingletonItem(maceId)) {
                droppedMaces.put(maceId, event.getItemDrop());
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player)) return;
        ItemStack item = event.getItem().getItemStack();
        Optional<String> matched = itemMatcher.match(item);
        if (matched.isPresent()) {
            String maceId = matched.get().toLowerCase();
            Item dropped = droppedMaces.remove(maceId);
            if (dropped != null && configManager.isGroundPickupRevealEnabled() && configManager.isSingletonItem(maceId)) {
                startTracking(maceId);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        for (BossBar bar : activeBossBars.values()) {
            bar.addPlayer(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        for (BossBar bar : activeBossBars.values()) {
            bar.removePlayer(event.getPlayer());
        }
    }
}
