package vn.nirussv.maceexclusive.item;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.Plugin;
import vn.nirussv.maceexclusive.mace.MaceType;

import java.util.EnumMap;
import java.util.Map;

public final class PdcKeys {

    public static final String ROOT_NAMESPACE = "mace_exclusive";
    public static final String ITEM_ID_KEY = "item_id";

    private final NamespacedKey itemId;
    private final Map<MaceType, NamespacedKey> legacyMaceKeys = new EnumMap<>(MaceType.class);
    private final Map<MaceType, NamespacedKey> legacyOwnerKeys = new EnumMap<>(MaceType.class);

    public PdcKeys(Plugin plugin) {
        this.itemId = new NamespacedKey(ROOT_NAMESPACE, ITEM_ID_KEY);
        for (MaceType type : MaceType.values()) {
            legacyMaceKeys.put(type, new NamespacedKey(plugin, type.getPdcKey()));
            legacyOwnerKeys.put(type, new NamespacedKey(plugin, type.getPdcKey() + "_owner"));
        }
    }

    public NamespacedKey itemId() {
        return itemId;
    }

    public NamespacedKey legacyMaceKey(MaceType type) {
        return legacyMaceKeys.get(type);
    }

    public NamespacedKey legacyOwnerKey(MaceType type) {
        return legacyOwnerKeys.get(type);
    }
}
