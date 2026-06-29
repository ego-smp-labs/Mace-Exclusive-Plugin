package vn.nirussv.maceexclusive.ritual;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class RitualAltarMenu implements InventoryHolder {

    public static final int SIZE = 27;
    public static final int RESULT_SLOT = 13;
    public static final int CRAFT_BUTTON_SLOT = 22;

    private static final int[] BORDER_SLOTS = {9, 10, 11, 12, 14, 15, 16, 17, 18, 19, 20, 21, 23, 24, 25, 26};

    private final Location altarLocation;
    private final Inventory inventory;
    private boolean locked;

    public RitualAltarMenu(Location altarLocation) {
        this.altarLocation = altarLocation.clone();
        this.inventory = Bukkit.createInventory(this, SIZE, Component.text("Ritual Crafting Table"));
        fillBorders();
        setInvalidButton();
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public Location altarLocation() {
        return altarLocation.clone();
    }

    public ItemStack[] matrix() {
        ItemStack[] matrix = new ItemStack[9];
        for (int slot = 0; slot < 9; slot++) matrix[slot] = inventory.getItem(slot);
        return matrix;
    }

    public void setPreview(ItemStack preview, boolean ready) {
        if (locked) return;
        inventory.setItem(RESULT_SLOT, preview == null ? null : preview.clone());
        if (ready) setReadyButton(); else setInvalidButton();
    }

    public boolean isLocked() { return locked; }

    public void setLocked(boolean locked) {
        this.locked = locked;
        inventory.setItem(RESULT_SLOT, null);
        inventory.setItem(CRAFT_BUTTON_SLOT, named(locked ? Material.YELLOW_CONCRETE : Material.RED_CONCRETE, Component.text(locked ? "Ritual in progress" : "Invalid Ritual")));
    }

    public boolean isMatrixSlot(int slot) {
        return slot >= 0 && slot < 9;
    }

    public boolean isProtectedSlot(int slot) {
        return slot == RESULT_SLOT || slot == CRAFT_BUTTON_SLOT || isBorderSlot(slot);
    }

    private void fillBorders() {
        ItemStack border = named(Material.GRAY_STAINED_GLASS_PANE, Component.text(" "));
        for (int slot : BORDER_SLOTS) inventory.setItem(slot, border);
    }

    private void setReadyButton() {
        inventory.setItem(CRAFT_BUTTON_SLOT, named(Material.GREEN_CONCRETE, Component.text("Forge Ritual")));
    }

    private void setInvalidButton() {
        inventory.setItem(CRAFT_BUTTON_SLOT, named(Material.RED_CONCRETE, Component.text("Invalid Ritual")));
    }

    private boolean isBorderSlot(int slot) {
        for (int border : BORDER_SLOTS) if (border == slot) return true;
        return false;
    }

    private ItemStack named(Material material, Component name) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }
}
