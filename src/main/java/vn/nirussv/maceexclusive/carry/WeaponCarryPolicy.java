package vn.nirussv.maceexclusive.carry;

import java.util.List;

/**
 * Central, side-effect-free rules for how many exclusive weapons a player may carry.
 *
 * <p>Base rule: a player may carry at most one exclusive weapon. The only exception is the
 * Truce Sigil, which lets a player additionally carry one non-mace exclusive weapon (total of
 * two exclusive items, one of which must be the sigil). A Mace is always solo and can never be
 * co-carried, even with a sigil.</p>
 */
public final class WeaponCarryPolicy {

    public static final String TRUCE_SIGIL_ID = "truce_sigil";

    /** Returns true when the given item id is an exclusive weapon subject to carry limits. */
    public boolean isExclusiveWeapon(String id) {
        if (id == null) return false;
        return id.endsWith("_mace")
            || id.endsWith("_spear")
            || id.equals("cursed_sword")
            || id.equals(TRUCE_SIGIL_ID);
    }

    public boolean isMace(String id) {
        return id != null && id.endsWith("_mace");
    }

    public boolean isSigil(String id) {
        return TRUCE_SIGIL_ID.equals(id);
    }

    /**
     * Validates a complete set of exclusive weapon ids a player would simultaneously hold.
     * The list may contain duplicates (e.g. two of the same spear).
     */
    public boolean isLegalSet(List<String> exclusiveIds) {
        if (exclusiveIds == null || exclusiveIds.isEmpty()) return true;
        int total = exclusiveIds.size();
        if (total == 1) return true;

        boolean hasMace = exclusiveIds.stream().anyMatch(this::isMace);
        if (hasMace) {
            // A mace must be the only exclusive weapon held.
            return false;
        }
        if (total == 2) {
            // Exactly one of the two must be the sigil; the other must be a non-mace weapon.
            long sigils = exclusiveIds.stream().filter(this::isSigil).count();
            return sigils == 1;
        }
        return false;
    }

    /**
     * Returns true if, given the exclusive ids already held (excluding the incoming item),
     * adding the incoming exclusive id keeps the inventory within the carry limit.
     */
    public boolean canCarryAdditional(List<String> heldExclusiveIds, String incomingId) {
        if (!isExclusiveWeapon(incomingId)) return true;
        java.util.List<String> projected = new java.util.ArrayList<>(heldExclusiveIds);
        projected.add(incomingId);
        return isLegalSet(projected);
    }
}
