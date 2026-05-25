package vn.nirussv.maceexclusive.mace;

import vn.nirussv.maceexclusive.item.ExclusiveItemId;

@Deprecated
public enum MaceType {

    POWER("mace", "mace_power_item"),
    CHAOS("mace-chaos", "mace_chaos_item");

    private final String configPath;
    private final String pdcKey;

    MaceType(String configPath, String pdcKey) {
        this.configPath = configPath;
        this.pdcKey = pdcKey;
    }

    public String getConfigPath() {
        return configPath;
    }

    public String getPdcKey() {
        return pdcKey;
    }

    public ExclusiveItemId getExclusiveItemId() {
        return switch (this) {
            case POWER -> ExclusiveItemId.POWER_MACE;
            case CHAOS -> ExclusiveItemId.CHAOS_MACE;
        };
    }

    public static MaceType fromPdcKey(String key) {
        for (MaceType type : values()) {
            if (type.pdcKey.equals(key)) {
                return type;
            }
        }
        return null;
    }
}
