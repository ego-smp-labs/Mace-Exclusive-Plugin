package vn.nirussv.maceexclusive.curse;

import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Owns temporary attribute modifiers applied by curse effects.
 *
 * <p>The lease key is a NamespacedKey, which is also the AttributeModifier key.
 * That keeps modifiers stable without hand-written hardcoded UUIDs.</p>
 */
public final class AttributeLease {

    private final Map<UUID, Map<Attribute, NamespacedKey>> activeLeases = new HashMap<>();

    public void apply(Player player, Attribute attribute, NamespacedKey leaseKey, double amount,
                      AttributeModifier.Operation operation) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(attribute, "attribute");
        Objects.requireNonNull(leaseKey, "leaseKey");
        Objects.requireNonNull(operation, "operation");

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }

        removeModifier(instance, leaseKey);

        AttributeModifier modifier = new AttributeModifier(
            leaseKey,
            amount,
            operation
        );
        instance.addModifier(modifier);

        activeLeases.computeIfAbsent(player.getUniqueId(), ignored -> new HashMap<>())
            .put(attribute, leaseKey);
        clampHealth(player);
    }

    public void revoke(Player player, Attribute attribute) {
        if (player == null || attribute == null) {
            return;
        }

        Map<Attribute, NamespacedKey> playerLeases = activeLeases.get(player.getUniqueId());
        NamespacedKey leaseKey = playerLeases == null ? null : playerLeases.remove(attribute);
        if (leaseKey == null) {
            return;
        }

        AttributeInstance instance = player.getAttribute(attribute);
        if (instance != null) {
            removeModifier(instance, leaseKey);
        }

        if (playerLeases.isEmpty()) {
            activeLeases.remove(player.getUniqueId());
        }
        clampHealth(player);
    }

    public void revokeAll(Player player) {
        if (player == null) {
            return;
        }

        Map<Attribute, NamespacedKey> playerLeases = activeLeases.remove(player.getUniqueId());
        if (playerLeases == null || playerLeases.isEmpty()) {
            return;
        }

        for (Map.Entry<Attribute, NamespacedKey> entry : playerLeases.entrySet()) {
            AttributeInstance instance = player.getAttribute(entry.getKey());
            if (instance != null) {
                removeModifier(instance, entry.getValue());
            }
        }
        clampHealth(player);
    }

    public void clear() {
        activeLeases.clear();
    }

    private void removeModifier(AttributeInstance instance, NamespacedKey leaseKey) {
        Iterator<AttributeModifier> iterator = instance.getModifiers().iterator();
        while (iterator.hasNext()) {
            AttributeModifier modifier = iterator.next();
            if (leaseKey.equals(modifier.getKey())) {
                instance.removeModifier(modifier);
            }
        }
    }

    private void clampHealth(Player player) {
        AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHealth != null && player.getHealth() > maxHealth.getValue()) {
            player.setHealth(Math.max(0.0D, maxHealth.getValue()));
        }
    }
}
