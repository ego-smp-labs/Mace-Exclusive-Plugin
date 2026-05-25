package vn.nirussv.maceexclusive.persistence;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.mace.MaceType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class ForgeSessionStore {

    private final MaceExclusivePlugin plugin;
    private final File dataFile;

    public ForgeSessionStore(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "forge-sessions.yml");
    }

    public List<StoredForgeSession> load() {
        if (!dataFile.exists()) {
            return List.of();
        }

        FileConfiguration config = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection section = config.getConfigurationSection("sessions");
        if (section == null) {
            return List.of();
        }

        List<StoredForgeSession> sessions = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            if (node == null) {
                continue;
            }
            StoredForgeSession stored = readSession(node);
            if (stored != null) {
                sessions.add(stored);
            }
        }
        return sessions;
    }

    public void save(Collection<StoredForgeSession> sessions) {
        FileConfiguration config = new YamlConfiguration();
        int index = 0;
        for (StoredForgeSession session : sessions) {
            String path = "sessions." + index++;
            config.set(path + ".world", session.location().getWorld().getUID().toString());
            config.set(path + ".world-name", session.location().getWorld().getName());
            config.set(path + ".x", session.location().getBlockX());
            config.set(path + ".y", session.location().getBlockY());
            config.set(path + ".z", session.location().getBlockZ());
            config.set(path + ".type", session.type().name());
            config.set(path + ".owner", session.owner() == null ? null : session.owner().toString());
            config.set(path + ".started-at", session.startedAtMillis());
            config.set(path + ".ends-at", session.endsAtMillis());
        }

        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save forge sessions", e);
        }
    }

    private StoredForgeSession readSession(ConfigurationSection node) {
        World world = resolveWorld(node);
        if (world == null) {
            return null;
        }

        MaceType type;
        try {
            type = MaceType.valueOf(node.getString("type", ""));
        } catch (IllegalArgumentException ex) {
            return null;
        }

        UUID owner = null;
        String ownerRaw = node.getString("owner");
        if (ownerRaw != null && !ownerRaw.isBlank()) {
            try {
                owner = UUID.fromString(ownerRaw);
            } catch (IllegalArgumentException ignored) {
            }
        }

        Location location = new Location(world, node.getInt("x"), node.getInt("y"), node.getInt("z"));
        return new StoredForgeSession(
            location,
            type,
            owner,
            node.getLong("started-at"),
            node.getLong("ends-at")
        );
    }

    private World resolveWorld(ConfigurationSection node) {
        String worldId = node.getString("world");
        if (worldId != null) {
            try {
                World world = Bukkit.getWorld(UUID.fromString(worldId));
                if (world != null) {
                    return world;
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
        return Bukkit.getWorld(node.getString("world-name", ""));
    }

    public record StoredForgeSession(
        Location location,
        MaceType type,
        UUID owner,
        long startedAtMillis,
        long endsAtMillis
    ) {
    }
}
