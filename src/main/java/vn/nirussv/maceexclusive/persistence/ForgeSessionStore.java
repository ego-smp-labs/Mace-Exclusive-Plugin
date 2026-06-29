package vn.nirussv.maceexclusive.persistence;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class ForgeSessionStore {

    private final MaceExclusivePlugin plugin;
    private final File loadFile;
    private final File saveFile;

    public ForgeSessionStore(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
        this.loadFile = SavePaths.resolve(plugin, "forge-sessions.yml");
        this.saveFile = SavePaths.target(plugin, "forge-sessions.yml");
    }

    public List<StoredForgeSession> load() {
        if (!loadFile.exists()) return List.of();
        FileConfiguration config = YamlConfiguration.loadConfiguration(loadFile);
        ConfigurationSection section = config.getConfigurationSection("sessions");
        if (section == null) return List.of();
        List<StoredForgeSession> sessions = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            StoredForgeSession stored = node == null ? null : readSession(node);
            if (stored != null) sessions.add(stored);
        }
        return sessions;
    }

    public boolean save(Collection<StoredForgeSession> sessions) {
        FileConfiguration config = new YamlConfiguration();
        int index = 0;
        for (StoredForgeSession session : sessions) {
            String path = "sessions." + index++;
            config.set(path + ".world", session.location().getWorld().getUID().toString());
            config.set(path + ".world-name", session.location().getWorld().getName());
            config.set(path + ".x", session.location().getBlockX());
            config.set(path + ".y", session.location().getBlockY());
            config.set(path + ".z", session.location().getBlockZ());
            config.set(path + ".item-id", session.itemId());
            config.set(path + ".owner", session.owner() == null ? null : session.owner().toString());
            config.set(path + ".started-at", session.startedAtMillis());
            config.set(path + ".charge-ends-at", session.chargeEndsAtMillis());
            config.set(path + ".ends-at", session.endsAtMillis());
        }
        try {
            // TODO Phase 2.2: write to a temp file then atomic move to harden restart-at-completion semantics.
            SavePaths.ensureParent(saveFile);
            config.save(saveFile);
            return true;
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save forge sessions", e);
            return false;
        }
    }

    private StoredForgeSession readSession(ConfigurationSection node) {
        World world = resolveWorld(node);
        if (world == null) return null;
        String itemId = node.getString("item-id", node.getString("type", "")).toLowerCase();
        if (itemId.isBlank()) return null;
        if (itemId.equals("power")) itemId = "power_mace";
        if (itemId.equals("chaos")) itemId = "chaos_mace";
        UUID owner = readOwner(node);
        long startedAt = node.getLong("started-at");
        long legacyEndsAt = node.getLong("ends-at");
        long chargeEndsAt = node.getLong("charge-ends-at", startedAt + 3000L);
        return new StoredForgeSession(new Location(world, node.getInt("x"), node.getInt("y"), node.getInt("z")), itemId, owner, startedAt, chargeEndsAt, legacyEndsAt);
    }

    private UUID readOwner(ConfigurationSection node) {
        String ownerRaw = node.getString("owner");
        if (ownerRaw == null || ownerRaw.isBlank()) return null;
        try {
            return UUID.fromString(ownerRaw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private World resolveWorld(ConfigurationSection node) {
        String worldId = node.getString("world");
        if (worldId != null) {
            try {
                World world = Bukkit.getWorld(UUID.fromString(worldId));
                if (world != null) return world;
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Bukkit.getWorld(node.getString("world-name", ""));
    }

    public record StoredForgeSession(Location location, String itemId, UUID owner, long startedAtMillis, long chargeEndsAtMillis, long endsAtMillis) { }
}
