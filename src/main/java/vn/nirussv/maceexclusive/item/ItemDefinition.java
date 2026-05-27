package vn.nirussv.maceexclusive.item;

import org.bukkit.Material;

import java.util.Objects;

public final class ItemDefinition {

    private final String id;
    private final Material material;
    private final String name;

    public ItemDefinition(String id, Material material, String name) {
        this.id = Objects.requireNonNull(id, "id");
        this.material = Objects.requireNonNull(material, "material");
        this.name = name == null || name.isBlank() ? id : name;
    }

    public String id() { return id; }
    public Material material() { return material; }
    public String name() { return name; }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof ItemDefinition other && id.equals(other.id);
    }

    @Override
    public int hashCode() { return id.hashCode(); }

    @Override
    public String toString() { return id; }
}
