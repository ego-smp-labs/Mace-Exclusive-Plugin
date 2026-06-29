package vn.nirussv.maceexclusive.carry;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import vn.nirussv.maceexclusive.config.ConfigManager;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CarryViolationService implements Listener {

    private static final long CHECK_INTERVAL_TICKS = 10L;
    private final Plugin plugin;
    private final ConfigManager configManager;
    private final CarryService carryService;
    private final Map<UUID, Long> violationStartedAtMillis = new HashMap<>();
    private BukkitTask task;

    public CarryViolationService(Plugin plugin, ConfigManager configManager, CarryService carryService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.carryService = carryService;
    }

    public void start() {
        if (!configManager.isCarryViolationCountdownEnabled() || task != null) return;
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, CHECK_INTERVAL_TICKS, CHECK_INTERVAL_TICKS);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
        violationStartedAtMillis.clear();
    }

    public boolean isCountdownActive(Player player) {
        return player != null && violationStartedAtMillis.containsKey(player.getUniqueId());
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead()) {
                violationStartedAtMillis.remove(player.getUniqueId());
                continue;
            }
            List<String> heldIds = carryService.heldExclusiveIds(player, true);
            if (carryService.policy().isLegalSet(heldIds)) {
                clearViolation(player);
                continue;
            }
            tickViolation(player, heldIds.size(), now);
        }
    }

    private void tickViolation(Player player, int count, long now) {
        UUID uuid = player.getUniqueId();
        long startedAt = violationStartedAtMillis.computeIfAbsent(uuid, ignored -> now);
        int countdownSeconds = configManager.getCarryViolationCountdownSeconds();
        long elapsedMillis = now - startedAt;
        long remainingMillis = countdownSeconds * 1000L - elapsedMillis;
        if (remainingMillis <= 0L) {
            punish(player, count);
            violationStartedAtMillis.remove(uuid);
            return;
        }
        int remainingSeconds = Math.max(1, (int) Math.ceil(remainingMillis / 1000.0D));
        player.sendActionBar(configManager.getMessage("carry-limit.warning", Map.of(
            "seconds", String.valueOf(remainingSeconds),
            "count", String.valueOf(count)
        )));
    }

    private void clearViolation(Player player) {
        if (violationStartedAtMillis.remove(player.getUniqueId()) != null) {
            player.sendActionBar(configManager.getMessage("carry-limit.cancelled"));
        }
    }

    private void punish(Player player, int count) {
        Location location = player.getLocation();
        if (location.getWorld() != null) {
            location.getWorld().createExplosion(location, configManager.getCarryViolationExplosionPower(), false, false);
        }
        player.sendMessage(configManager.getPrefixedMessage("carry-limit.punished", Map.of("count", String.valueOf(count))));
        player.setHealth(0.0D);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        violationStartedAtMillis.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        violationStartedAtMillis.remove(event.getEntity().getUniqueId());
    }
}
