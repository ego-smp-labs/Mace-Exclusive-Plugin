package vn.nirussv.maceexclusive.ability;

import org.bukkit.entity.Player;
import vn.nirussv.maceexclusive.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownService {

    private final ConfigManager configManager;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();
    private final Map<UUID, Map<String, Long>> lastNotifications = new HashMap<>();
    private static final long NOTIFICATION_THROTTLE_MILLIS = 900L;

    public CooldownService(ConfigManager configManager) {
        this.configManager = configManager;
    }

    public boolean isReady(Player player, String abilityId) {
        return remainingMillis(player.getUniqueId(), abilityId) <= 0L;
    }

    public boolean checkAndNotify(Player player, String abilityId) {
        long remaining = remainingMillis(player.getUniqueId(), abilityId);
        if (remaining <= 0L) {
            return true;
        }
        long now = System.currentTimeMillis();
        Map<String, Long> playerNotifications = lastNotifications.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>());
        long lastNotification = playerNotifications.getOrDefault(abilityId, 0L);
        if (now - lastNotification < NOTIFICATION_THROTTLE_MILLIS) {
            return false;
        }
        playerNotifications.put(abilityId, now);
        double seconds = Math.ceil(remaining / 100.0D) / 10.0D;
        player.sendActionBar(configManager.getMessage("cooldown", Map.of("seconds", String.valueOf(seconds))));
        return false;
    }

    public void setCooldown(Player player, String abilityId, long durationMillis) {
        cooldowns.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
            .put(abilityId, System.currentTimeMillis() + Math.max(0L, durationMillis));
    }

    public long remainingMillis(UUID uuid, String abilityId) {
        Map<String, Long> playerCooldowns = cooldowns.get(uuid);
        if (playerCooldowns == null) {
            return 0L;
        }
        Long expiresAt = playerCooldowns.get(abilityId);
        if (expiresAt == null) {
            return 0L;
        }
        long remaining = expiresAt - System.currentTimeMillis();
        if (remaining <= 0L) {
            playerCooldowns.remove(abilityId);
            Map<String, Long> playerNotifications = lastNotifications.get(uuid);
            if (playerNotifications != null) {
                playerNotifications.remove(abilityId);
                if (playerNotifications.isEmpty()) {
                    lastNotifications.remove(uuid);
                }
            }
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(uuid);
            }
            return 0L;
        }
        return remaining;
    }
}
