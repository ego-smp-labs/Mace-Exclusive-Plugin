package vn.nirussv.maceexclusive.persistence;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;

public final class SavePaths {

    public static final String SAVES_DIRECTORY = "Saves";
    public static final String PLAYER_USAGE_FILE = "player-usage.yml";

    private SavePaths() {
    }

    public static File resolve(JavaPlugin plugin, String fileName) {
        File savesFile = new File(new File(plugin.getDataFolder(), SAVES_DIRECTORY), fileName);
        File legacyFile = new File(plugin.getDataFolder(), fileName);
        if (!savesFile.exists() && legacyFile.exists()) {
            plugin.getLogger().warning("Loading legacy save file " + legacyFile.getName() + "; future saves will be written to " + SAVES_DIRECTORY + "/" + fileName + ". Legacy files are not deleted automatically.");
            return legacyFile;
        }
        return savesFile;
    }

    public static File target(JavaPlugin plugin, String fileName) {
        return new File(new File(plugin.getDataFolder(), SAVES_DIRECTORY), fileName);
    }

    public static File playerUsage(JavaPlugin plugin) {
        return target(plugin, PLAYER_USAGE_FILE);
    }

    public static void ensureParent(File file) {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
    }
}
