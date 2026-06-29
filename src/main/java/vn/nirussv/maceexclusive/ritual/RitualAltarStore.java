package vn.nirussv.maceexclusive.ritual;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.persistence.SavePaths;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

public final class RitualAltarStore {

    private final MaceExclusivePlugin plugin;
    private final File loadFile;
    private final File saveFile;

    public RitualAltarStore(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
        this.loadFile = SavePaths.resolve(plugin, "ritual_altars.yml");
        this.saveFile = SavePaths.target(plugin, "ritual_altars.yml");
    }

    public List<StoredAltar> load() {
        if (!loadFile.exists()) return List.of();
        FileConfiguration config = YamlConfiguration.loadConfiguration(loadFile);
        ConfigurationSection section = config.getConfigurationSection("altars");
        if (section == null) return List.of();
        List<StoredAltar> altars = new ArrayList<>();
        for (String key : section.getKeys(false)) {
            ConfigurationSection node = section.getConfigurationSection(key);
            StoredAltar stored = node == null ? null : readAltar(node);
            if (stored != null) altars.add(stored);
        }
        return altars;
    }

    public boolean save(Collection<StoredAltar> altars) {
        FileConfiguration config = new YamlConfiguration();
        int index = 0;
        for (StoredAltar altar : altars) {
            String path = "altars." + index++;
            config.set(path + ".world", altar.location().getWorld().getUID().toString());
            config.set(path + ".world-name", altar.location().getWorld().getName());
            config.set(path + ".x", altar.location().getBlockX());
            config.set(path + ".y", altar.location().getBlockY());
            config.set(path + ".z", altar.location().getBlockZ());
        }
        try {
            SavePaths.ensureParent(saveFile);
            config.save(saveFile);
            return true;
        } catch (IOException exception) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save ritual altars", exception);
            return false;
        }
    }

    private StoredAltar readAltar(ConfigurationSection node) {
        World world = resolveWorld(node);
        if (world == null) return null;
        return new StoredAltar(new Location(world, node.getInt("x"), node.getInt("y"), node.getInt("z")));
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

    public record StoredAltar(Location location) { }
}
