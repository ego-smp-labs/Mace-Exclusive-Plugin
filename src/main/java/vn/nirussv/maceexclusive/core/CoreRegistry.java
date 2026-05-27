package vn.nirussv.maceexclusive.core;

import org.bukkit.configuration.file.YamlConfiguration;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class CoreRegistry {

    private static final String CORES_DIR = "cores";
    private static final String YML = ".yml";

    private final MaceExclusivePlugin plugin;
    private final Map<String, CoreConfig> cores = new LinkedHashMap<>();

    public CoreRegistry(MaceExclusivePlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        cores.clear();
        ensureBundledYamlCopied();
        File directory = new File(plugin.getDataFolder(), CORES_DIR);
        if (!directory.exists() && !directory.mkdirs()) {
            plugin.getLogger().warning("Could not create core config directory: " + directory.getPath());
            return;
        }
        File[] files = directory.listFiles((dir, name) -> name.endsWith(YML));
        if (files == null) return;
        for (File file : files) register(file);
        plugin.getLogger().info("Loaded " + cores.size() + " core definitions.");
    }

    public Optional<CoreConfig> find(String id) { return id == null ? Optional.empty() : Optional.ofNullable(cores.get(id.toLowerCase())); }
    public Collection<CoreConfig> all() { return List.copyOf(cores.values()); }

    private void register(File file) {
        String id = file.getName().substring(0, file.getName().length() - YML.length()).toLowerCase();
        cores.put(id, CoreConfig.fromSection(id, YamlConfiguration.loadConfiguration(file)));
    }

    private void ensureBundledYamlCopied() {
        File dir = new File(plugin.getDataFolder(), CORES_DIR);
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("Could not create core config directory: " + dir.getPath());
        for (String resource : bundledYamlResources()) {
            File target = new File(plugin.getDataFolder(), resource);
            if (!target.exists()) plugin.saveResource(resource, false);
        }
    }

    private List<String> bundledYamlResources() {
        URL url = plugin.getClass().getClassLoader().getResource(CORES_DIR);
        if (url == null) return List.of();
        if ("file".equals(url.getProtocol())) {
            File dir = new File(url.getPath());
            File[] files = dir.listFiles((ignored, name) -> name.endsWith(YML));
            if (files == null) return List.of();
            List<String> result = new ArrayList<>();
            for (File file : files) result.add(CORES_DIR + "/" + file.getName());
            return result;
        }
        if (!"jar".equals(url.getProtocol())) return List.of();
        try {
            JarURLConnection connection = (JarURLConnection) url.openConnection();
            try (JarFile jar = connection.getJarFile()) {
                List<String> result = new ArrayList<>();
                var entries = jar.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    if (!entry.isDirectory() && entry.getName().startsWith(CORES_DIR + "/") && entry.getName().endsWith(YML)) result.add(entry.getName());
                }
                return result;
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("Could not scan bundled core configs: " + exception.getMessage());
            return List.of();
        }
    }
}
