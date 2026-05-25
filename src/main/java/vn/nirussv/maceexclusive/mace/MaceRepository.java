package vn.nirussv.maceexclusive.mace;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;

import java.io.File;
import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

public class MaceRepository {

    private final MaceExclusivePlugin plugin;
    private final File dataFile;
    private FileConfiguration config;
    private final Map<ExclusiveItemId, UUID> holders = new EnumMap<>(ExclusiveItemId.class);

    public MaceRepository(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "mace-data.yml");
        load();
    }

    private void load() {
        if (!dataFile.exists()) {
            return;
        }
        
        config = YamlConfiguration.loadConfiguration(dataFile);
        
        for (ExclusiveItemId id : ExclusiveItemId.values()) {
            String holderString = config.getString(id.id() + ".holder");
            if (holderString == null) {
                holderString = id.legacyMaceType()
                    .map(type -> config.getString(type.name().toLowerCase() + ".holder"))
                    .orElse(null);
            }
            if (holderString != null && !holderString.isEmpty()) {
                try {
                    holders.put(id, UUID.fromString(holderString));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
    }

    public void save() {
        if (config == null) {
            config = new YamlConfiguration();
        }
        
        for (ExclusiveItemId id : ExclusiveItemId.values()) {
            String path = id.id();
            UUID holder = holders.get(id);
            config.set(path + ".registered", holder != null);
            config.set(path + ".holder", holder != null ? holder.toString() : null);
        }
        
        try {
            config.save(dataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to save mace data", e);
        }
    }

    public boolean isRegistered(ExclusiveItemId id) {
        return holders.containsKey(id) && holders.get(id) != null;
    }

    public UUID getHolder(ExclusiveItemId id) {
        return holders.get(id);
    }

    public void setHolder(ExclusiveItemId id, UUID holder) {
        if (holder != null) {
            holders.put(id, holder);
        } else {
            holders.remove(id);
        }
        save();
    }

    public void reset(ExclusiveItemId id) {
        holders.remove(id);
        save();
    }

    public void resetAll() {
        holders.clear();
        save();
    }

    @Deprecated
    public boolean isRegistered(MaceType type) {
        return ExclusiveItemId.fromMaceType(type).map(this::isRegistered).orElse(false);
    }

    @Deprecated
    public UUID getHolder(MaceType type) {
        return ExclusiveItemId.fromMaceType(type).map(this::getHolder).orElse(null);
    }

    @Deprecated
    public void setHolder(MaceType type, UUID holder) {
        ExclusiveItemId.fromMaceType(type).ifPresent(id -> setHolder(id, holder));
    }

    @Deprecated
    public void reset(MaceType type) {
        ExclusiveItemId.fromMaceType(type).ifPresent(this::reset);
    }

    @Deprecated
    public boolean isMaceRegistered() {
        return isRegistered(ExclusiveItemId.POWER_MACE);
    }

    @Deprecated
    public UUID getCurrentHolder() {
        return getHolder(ExclusiveItemId.POWER_MACE);
    }

    @Deprecated
    public void setCurrentHolder(UUID holder) {
        setHolder(ExclusiveItemId.POWER_MACE, holder);
    }

    @Deprecated
    public void reset() {
        reset(ExclusiveItemId.POWER_MACE);
    }
}
