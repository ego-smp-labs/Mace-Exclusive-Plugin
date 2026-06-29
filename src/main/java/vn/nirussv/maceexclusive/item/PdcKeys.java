package vn.nirussv.maceexclusive.item;

import org.bukkit.NamespacedKey;

public final class PdcKeys {

    public static final String ROOT_NAMESPACE = "mace_exclusive";
    public static final String ITEM_ID_KEY = "item_id";
    public static final String CORE_ID_KEY = "core_id";
    public static final String WEAPON_CLASS_KEY = "weapon_class";
    public static final String OWNER_KEY = "owner";

    private final NamespacedKey itemId = new NamespacedKey(ROOT_NAMESPACE, ITEM_ID_KEY);
    private final NamespacedKey coreId = new NamespacedKey(ROOT_NAMESPACE, CORE_ID_KEY);
    private final NamespacedKey weaponClass = new NamespacedKey(ROOT_NAMESPACE, WEAPON_CLASS_KEY);
    private final NamespacedKey owner = new NamespacedKey(ROOT_NAMESPACE, OWNER_KEY);

    public NamespacedKey itemId() {
        return itemId;
    }

    public NamespacedKey coreId() {
        return coreId;
    }

    public NamespacedKey weaponClass() {
        return weaponClass;
    }

    public NamespacedKey owner() {
        return owner;
    }
}
