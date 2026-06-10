package vn.nirussv.maceexclusive.curse;

import org.bukkit.entity.Player;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class LockoutService {

    private final Map<UUID, Long> lockouts = new HashMap<>();

    public LockoutService() {
    }

    public boolean isCursed(Player player) {
        if (player == null) return false;
        return isCursed(player.getUniqueId());
    }

    public boolean isCursed(UUID uuid) {
        if (uuid == null) return false;
        Long endsAt = lockouts.get(uuid);
        if (endsAt == null) return false;
        if (System.currentTimeMillis() < endsAt) return true;
        lockouts.remove(uuid);
        return false;
    }

    public void applyCursed(UUID uuid, long durationSeconds) {
        if (uuid == null) return;
        lockouts.put(uuid, System.currentTimeMillis() + durationSeconds * 1000L);
    }

    public void removeCursed(UUID uuid) {
        if (uuid == null) return;
        lockouts.remove(uuid);
    }

    public long getRemainingSeconds(UUID uuid) {
        if (uuid == null) return 0;
        Long endsAt = lockouts.get(uuid);
        if (endsAt == null) return 0;
        long diff = endsAt - System.currentTimeMillis();
        return diff <= 0 ? 0 : diff / 1000L;
    }
}
