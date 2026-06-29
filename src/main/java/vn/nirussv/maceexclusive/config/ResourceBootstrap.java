package vn.nirussv.maceexclusive.config;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.List;

public final class ResourceBootstrap {

    public static final String WEAPON_CONFIG_DIRECTORY = "weapons";
    public static final String UTILITY_ITEM_CONFIG_DIRECTORY = "items";
    public static final String CORE_CONFIG_DIRECTORY = "cores";
    public static final String LANG_DIRECTORY = "lang";

    public static final List<String> ROOT_RESOURCES = List.of(
        "config.yml",
        "items.yml",
        "discord.yml"
    );

    public static final List<String> LANG_RESOURCES = List.of(
        LANG_DIRECTORY + "/en_US.yml",
        LANG_DIRECTORY + "/vi_VN.yml"
    );

    public static final List<String> WEAPON_RESOURCES = List.of(
        WEAPON_CONFIG_DIRECTORY + "/power_mace.yml",
        WEAPON_CONFIG_DIRECTORY + "/void_mace.yml",
        WEAPON_CONFIG_DIRECTORY + "/chaos_mace.yml",
        WEAPON_CONFIG_DIRECTORY + "/vampiric_mace.yml",
        WEAPON_CONFIG_DIRECTORY + "/gravity_mace.yml",
        WEAPON_CONFIG_DIRECTORY + "/sonic_spear.yml",
        WEAPON_CONFIG_DIRECTORY + "/soulfire_mace.yml",
        WEAPON_CONFIG_DIRECTORY + "/chronos_anchor_spear.yml",
        WEAPON_CONFIG_DIRECTORY + "/cursed_sword.yml",
        WEAPON_CONFIG_DIRECTORY + "/avarice_sword.yml",
        WEAPON_CONFIG_DIRECTORY + "/soul_sever_spear.yml",
        WEAPON_CONFIG_DIRECTORY + "/void_edge.yml"
    );

    public static final List<String> ITEM_RESOURCES = List.of(
        UTILITY_ITEM_CONFIG_DIRECTORY + "/challenger_eye.yml",
        UTILITY_ITEM_CONFIG_DIRECTORY + "/obsidian_chaos.yml",
        UTILITY_ITEM_CONFIG_DIRECTORY + "/glitch_clock.yml",
        UTILITY_ITEM_CONFIG_DIRECTORY + "/cursed_player_head.yml",
        UTILITY_ITEM_CONFIG_DIRECTORY + "/warden_resonance_shard.yml",
        UTILITY_ITEM_CONFIG_DIRECTORY + "/plundered_heart.yml",
        UTILITY_ITEM_CONFIG_DIRECTORY + "/vile_ledger.yml"
    );

    public static final List<String> CORE_RESOURCES = List.of(
        CORE_CONFIG_DIRECTORY + "/soulfire_core.yml",
        CORE_CONFIG_DIRECTORY + "/ego_core.yml",
        CORE_CONFIG_DIRECTORY + "/avarice_ritual_core.yml",
        CORE_CONFIG_DIRECTORY + "/end_core.yml",
        CORE_CONFIG_DIRECTORY + "/sculk_ritual_core.yml",
        CORE_CONFIG_DIRECTORY + "/blood_ritual_core.yml",
        CORE_CONFIG_DIRECTORY + "/ruined_core.yml",
        CORE_CONFIG_DIRECTORY + "/chaos_core.yml",
        CORE_CONFIG_DIRECTORY + "/ritual_core.yml",
        CORE_CONFIG_DIRECTORY + "/echo_ritual_core.yml",
        CORE_CONFIG_DIRECTORY + "/void_ritual_core.yml",
        CORE_CONFIG_DIRECTORY + "/reaper_ritual_core.yml"
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
        for (String resource : LANG_RESOURCES) {
            CopyResult result = ensure(plugin, resource);
            copied += result.copied;
            missing += result.missing;
        }
        for (String resource : WEAPON_RESOURCES) {
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
        return new BootstrapSummary(copied, missing, ROOT_RESOURCES.size(), LANG_RESOURCES.size(), WEAPON_RESOURCES.size(), ITEM_RESOURCES.size(), CORE_RESOURCES.size());
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

    public record BootstrapSummary(int copied, int missing, int rootExpected, int langExpected, int weaponsExpected, int itemsExpected, int coresExpected) {
    }
}
