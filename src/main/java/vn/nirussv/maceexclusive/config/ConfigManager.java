package vn.nirussv.maceexclusive.config;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;

public class ConfigManager {

    private final MaceExclusivePlugin plugin;
    private FileConfiguration langConfig;
    private final Map<String, String> messageCache = new HashMap<>();

    public ConfigManager(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
        reload(); // Load text immediately
    }

    public void reload() {
        plugin.reloadConfig();
        loadLanguage();
    }

    private void loadLanguage() {
        String langCode = plugin.getConfig().getString("settings.language", "en");
        String fileName = "lang_" + langCode + ".yml";
        File langFile = new File(plugin.getDataFolder(), fileName);

        if (!langFile.exists()) {
            plugin.saveResource(fileName, false);
        }

        langConfig = YamlConfiguration.loadConfiguration(langFile);
        
        InputStream defStream = plugin.getResource(fileName);
        if (defStream != null) {
            langConfig.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(defStream, StandardCharsets.UTF_8)));
        }
        
        messageCache.clear();
    }

    public String getRawMessage(String key) {
        if (langConfig == null) loadLanguage();
        return langConfig.getString(key, "Missing key: " + key);
    }

    public Component getMessage(String key) {
        return toComponent(getRawMessage(key));
    }

    public Component getMessage(String key, Map<String, String> placeholders) {
        String msg = getRawMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return toComponent(msg);
    }

    public Component getPrefixedMessage(String key) {
        String prefix = getRawMessage("prefix");
        return toComponent(prefix + getRawMessage(key));
    }
    
    public Component getPrefixedMessage(String key, Map<String, String> placeholders) {
        String prefix = getRawMessage("prefix");
        String msg = getRawMessage(key);
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            msg = msg.replace("%" + entry.getKey() + "%", entry.getValue());
        }
        return toComponent(prefix + msg);
    }

    private Component toComponent(String legacyText) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacyText);
    }
    
    public boolean isDropAllowed() {
        return plugin.getConfig().getBoolean("settings.allow-drop", true);
    }
    
    public boolean isStrictMode() {
        return plugin.getConfig().getBoolean("settings.strict-mode", false);
    }
    
    public boolean isVerboseLogging() {
        return plugin.getConfig().getBoolean("settings.verbose", false);
    }

    /**
     * If true AND strict-mode is enabled, trying to store a mace
     * will drop it at the player's feet instead of just cancelling.
     */
    public boolean isStrictModeDrop() {
        return plugin.getConfig().getBoolean("settings.strict-mode-drop", false);
    }

    /**
     * If true, prevents hoppers and droppers from moving registered maces.
     */
    public boolean isPreventHopperPickup() {
        return plugin.getConfig().getBoolean("settings.prevent-hopper-pickup", true);
    }
}
