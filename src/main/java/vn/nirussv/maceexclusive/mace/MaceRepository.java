package vn.nirussv.maceexclusive.mace;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.persistence.SavePaths;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MaceRepository {

    private final MaceExclusivePlugin plugin;
    private final File loadFile;
    private final File saveFile;
    private FileConfiguration config;
    private final Map<String, UUID> holders = new HashMap<>();

    public MaceRepository(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
        this.loadFile = SavePaths.resolve(plugin, "mace-data.yml");
        this.saveFile = SavePaths.target(plugin, "mace-data.yml");
        load();
    }

    private void load() {
        if (!loadFile.exists()) return;
        config = YamlConfiguration.loadConfiguration(loadFile);
        for (String key : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(key);
            String holderString = section == null ? null : section.getString("holder");
            if (holderString == null || holderString.isBlank()) continue;
            try {
                holders.put(key.toLowerCase(), UUID.fromString(holderString));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        if (config == null) config = new YamlConfiguration();
        for (Map.Entry<String, UUID> entry : holders.entrySet()) {
            String path = entry.getKey();
            config.set(path + ".registered", true);
            config.set(path + ".holder", entry.getValue().toString());
        }
        try {
            SavePaths.ensureParent(saveFile);
            config.save(saveFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save mace data", e);
        }
    }

    public boolean isRegistered(String id) {
        return id != null && holders.containsKey(id.toLowerCase()) && holders.get(id.toLowerCase()) != null;
    }

    public UUID getHolder(String id) {
        return id == null ? null : holders.get(id.toLowerCase());
    }

    public void setHolder(String id, UUID holder) {
        if (id == null) return;
        String key = id.toLowerCase();
        if (holder != null) holders.put(key, holder); else holders.remove(key);
        save();
    }

    public void reset(String id) {
        if (id != null) holders.remove(id.toLowerCase());
        save();
    }

    public void resetAll() {
        holders.clear();
        save();
    }
}
