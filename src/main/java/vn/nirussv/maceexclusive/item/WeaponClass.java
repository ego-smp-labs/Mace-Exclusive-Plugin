package vn.nirussv.maceexclusive.item;

import org.bukkit.Material;

import java.util.Locale;

public enum WeaponClass {
    MACE(true),
    SPEAR(true),
    SWORD(true),
    UTILITY(false),
    CORE(false),
    UNKNOWN(false);

    private final boolean weapon;

    WeaponClass(boolean weapon) {
        this.weapon = weapon;
    }

    public boolean isWeapon() {
        return weapon;
    }

    public String pdcValue() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static WeaponClass fromConfig(String configured, String id, Material material) {
        if (configured != null && !configured.isBlank()) {
            try {
                return WeaponClass.valueOf(configured.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
            } catch (IllegalArgumentException ignored) {
                return infer(id, material);
            }
        }
        return infer(id, material);
    }

    public static WeaponClass infer(String id, Material material) {
        String normalizedId = id == null ? "" : id.toLowerCase(Locale.ROOT);
        if (normalizedId.endsWith("_mace")) return MACE;
        if (normalizedId.endsWith("_spear")) return SPEAR;
        if (normalizedId.endsWith("_sword") || normalizedId.equals("cursed_sword")) return SWORD;
        if (normalizedId.endsWith("_core") || material == Material.HEAVY_CORE) return CORE;
        if (material != null && material.name().endsWith("SWORD")) return SWORD;
        if (material == Material.MACE) return MACE;
        return UTILITY;
    }
}
