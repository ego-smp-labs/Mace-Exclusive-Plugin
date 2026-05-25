package vn.nirussv.maceexclusive.mace;

import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.item.PdcKeys;

import java.util.Optional;

public class MaceFactory {

    private final ExclusiveItemFactory itemFactory;
    private final ItemMatcher itemMatcher;

    public MaceFactory(MaceExclusivePlugin plugin, ConfigManager configManager) {
        PdcKeys keys = new PdcKeys(plugin);
        this.itemFactory = new ExclusiveItemFactory(configManager, keys);
        this.itemMatcher = new ItemMatcher(keys);
    }

    public ItemStack createMace(MaceType type) {
        return createItem(type.getExclusiveItemId());
    }

    public ItemStack createUnawakenedWeapon(MaceType type) {
        return ExclusiveItemId.unawakenedFor(type)
            .map(this::createItem)
            .orElseGet(() -> createMace(type));
    }

    public ItemStack createItem(ExclusiveItemId id) {
        return itemFactory.create(id);
    }

    public ItemStack createPowerMace() {
        return createMace(MaceType.POWER);
    }
    
    public ItemStack createChaosMace() {
        return createMace(MaceType.CHAOS);
    }

    public MaceType getMaceType(ItemStack item) {
        Optional<ExclusiveItemId> itemId = getExclusiveItemId(item);
        return itemId.flatMap(ExclusiveItemId::legacyMaceType).orElse(null);
    }

    public Optional<ExclusiveItemId> getExclusiveItemId(ItemStack item) {
        return itemMatcher.match(item);
    }

    public Optional<MaceType> getAwakeningResult(ItemStack item) {
        return getExclusiveItemId(item).flatMap(ExclusiveItemId::awakeningResult);
    }

    public ItemMatcher getItemMatcher() {
        return itemMatcher;
    }

    public boolean isMaceItem(ItemStack item) {
        return getMaceType(item) != null;
    }

    public boolean isPowerMace(ItemStack item) {
        return getMaceType(item) == MaceType.POWER;
    }

    public boolean isChaosMace(ItemStack item) {
        return getMaceType(item) == MaceType.CHAOS;
    }
}
