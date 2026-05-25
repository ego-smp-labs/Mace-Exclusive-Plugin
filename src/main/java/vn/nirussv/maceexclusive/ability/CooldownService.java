package vn.nirussv.maceexclusive.ability;

import org.bukkit.entity.Player;
import vn.nirussv.maceexclusive.config.ConfigManager;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class CooldownService {

    private final ConfigManager configManager;
    private final Map<UUID, Map<String, Long>> cooldowns = new HashMap<>();

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
        double seconds = Math.ceil(remaining / 100.0D) / 10.0D;
        player.sendMessage(configManager.getPrefixedMessage("cooldown", Map.of("seconds", String.valueOf(seconds))));
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
            if (playerCooldowns.isEmpty()) {
                cooldowns.remove(uuid);
            }
            return 0L;
        }
        return remaining;
    }
}
