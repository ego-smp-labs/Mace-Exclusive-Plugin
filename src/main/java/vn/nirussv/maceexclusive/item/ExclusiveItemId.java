package vn.nirussv.maceexclusive.item;

import org.bukkit.Material;
import vn.nirussv.maceexclusive.mace.MaceType;

import java.util.Arrays;
import java.util.Optional;

public enum ExclusiveItemId {

    POWER_MACE("power_mace", Material.MACE, "mace", "&b&lMACE OF POWER"),
    CHAOS_MACE("chaos_mace", Material.MACE, "mace-chaos", "&5&lMACE OF CHAOS"),
    UNAWAKENED_POWER_MACE("unawakened_power_mace", Material.MACE, "unawakened-power-mace", "&7&lUnawakened Mace of Power"),
    UNAWAKENED_CHAOS_MACE("unawakened_chaos_mace", Material.MACE, "unawakened-chaos-mace", "&7&lUnawakened Mace of Chaos"),
    CHRONOS_ANCHOR_SPEAR("chronos_anchor_spear", Material.TRIDENT, "chronos-anchor-spear", "&6&lCHRONOS ANCHOR SPEAR");

    private final String id;
    private final Material material;
    private final String configPath;
    private final String fallbackName;

    ExclusiveItemId(String id, Material material, String configPath, String fallbackName) {
        this.id = id;
        this.material = material;
        this.configPath = configPath;
        this.fallbackName = fallbackName;
    }

    public String id() {
        return id;
    }

    public Material material() {
        return material;
    }

    public String configPath() {
        return "weapons." + id;
    }

    public String legacyConfigPath() {
        return "items." + id;
    }

    public String fallbackName() {
        return fallbackName;
    }

    public Optional<MaceType> legacyMaceType() {
        return switch (this) {
            case POWER_MACE -> Optional.of(MaceType.POWER);
            case CHAOS_MACE -> Optional.of(MaceType.CHAOS);
            case UNAWAKENED_POWER_MACE, UNAWAKENED_CHAOS_MACE, CHRONOS_ANCHOR_SPEAR -> Optional.empty();
        };
    }

    public static Optional<ExclusiveItemId> fromId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Arrays.stream(values())
            .filter(value -> value.id.equals(id))
            .findFirst();
    }

    public static Optional<ExclusiveItemId> fromMaceType(MaceType type) {
        if (type == null) {
            return Optional.empty();
        }
        return switch (type) {
            case POWER -> Optional.of(POWER_MACE);
            case CHAOS -> Optional.of(CHAOS_MACE);
        };
    }

    public static Optional<ExclusiveItemId> unawakenedFor(MaceType type) {
        if (type == null) {
            return Optional.empty();
        }
        return switch (type) {
            case POWER -> Optional.of(UNAWAKENED_POWER_MACE);
            case CHAOS -> Optional.of(UNAWAKENED_CHAOS_MACE);
        };
    }

    public Optional<MaceType> awakeningResult() {
        return switch (this) {
            case UNAWAKENED_POWER_MACE -> Optional.of(MaceType.POWER);
            case UNAWAKENED_CHAOS_MACE -> Optional.of(MaceType.CHAOS);
            default -> Optional.empty();
        };
    }
}
