package vn.nirussv.maceexclusive.item;

import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import vn.nirussv.maceexclusive.mace.MaceType;

import java.util.Optional;

public final class ItemMatcher {

    private final PdcKeys keys;

    public ItemMatcher(PdcKeys keys) {
        this.keys = keys;
    }

    public Optional<ExclusiveItemId> match(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return Optional.empty();
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return Optional.empty();
        }

        PersistentDataContainer pdc = meta.getPersistentDataContainer();

        String itemId = pdc.get(keys.itemId(), PersistentDataType.STRING);
        Optional<ExclusiveItemId> rootMatch = ExclusiveItemId.fromId(itemId)
            .filter(id -> item.getType() == id.material());
        if (rootMatch.isPresent()) {
            return rootMatch;
        }

        for (MaceType legacyType : MaceType.values()) {
            if (pdc.has(keys.legacyMaceKey(legacyType), PersistentDataType.BYTE)) {
                return ExclusiveItemId.fromMaceType(legacyType)
                    .filter(id -> item.getType() == id.material());
            }
        }

        return Optional.empty();
    }

    public boolean is(ItemStack item, ExclusiveItemId id) {
        return match(item).filter(match -> match == id).isPresent();
    }
}
