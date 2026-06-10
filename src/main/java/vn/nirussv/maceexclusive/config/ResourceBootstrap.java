package vn.nirussv.maceexclusive.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class ResourceBootstrap {

    public static final List<String> ROOT_RESOURCES = List.of(
        "config.yml",
        "lang_en.yml",
        "lang_vi.yml"
    );

    public static final List<String> ITEM_RESOURCES = List.of(
        "items/power_mace.yml",
        "items/void_mace.yml",
        "items/chaos_mace.yml",
        "items/vampiric_mace.yml",
        "items/gravity_mace.yml",
        "items/sonic_mace.yml",
        "items/soulfire_mace.yml",
        "items/challenger_eye.yml",
        "items/obsidian_chaos.yml",
        "items/chronos_anchor_spear.yml",
        "items/cursed_sword.yml",
        "items/cursed_player_head.yml"
    );

    public static final List<String> CORE_RESOURCES = List.of(
        "cores/soulfire_core.yml",
        "cores/ego_core.yml",
        "cores/end_core.yml",
        "cores/sculk_core.yml",
        "cores/blood_core.yml",
        "cores/ruined_core.yml",
        "cores/chaos_core.yml"
    );

    private ResourceBootstrap() {
    }

    public static BootstrapSummary ensureAll(JavaPlugin plugin) {
        int copied = 0;
        int missing = 0;
        for (String resource : ROOT_RESOURCES) {
            CopyResult result = ensure(plugin, resource);
            copied += result.copied;
            missing += result.missing;
        }
        for (String resource : ITEM_RESOURCES) {
            CopyResult result = ensure(plugin, resource);
            copied += result.copied;
            missing += result.missing;
        }
        for (String resource : CORE_RESOURCES) {
            CopyResult result = ensure(plugin, resource);
            copied += result.copied;
            missing += result.missing;
        }
        return new BootstrapSummary(copied, missing, ROOT_RESOURCES.size(), ITEM_RESOURCES.size(), CORE_RESOURCES.size());
    }

    public static CopyResult ensure(JavaPlugin plugin, String resource) {
        File target = new File(plugin.getDataFolder(), resource);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            plugin.getLogger().warning("Could not create resource directory: " + parent.getPath());
        }
        if (target.exists()) {
            return new CopyResult(0, 0);
        }
        if (plugin.getResource(resource) == null) {
            plugin.getLogger().warning("Bundled resource is missing from jar: " + resource);
            return new CopyResult(0, 1);
        }
        plugin.saveResource(resource, false);
        return new CopyResult(1, 0);
    }

    public record CopyResult(int copied, int missing) {
    }

    public record BootstrapSummary(int copied, int missing, int rootExpected, int itemsExpected, int coresExpected) {
    }
}
