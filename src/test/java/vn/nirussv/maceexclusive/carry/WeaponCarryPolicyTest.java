package vn.nirussv.maceexclusive.carry;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public final class WeaponCarryPolicyTest {

    private WeaponCarryPolicy policy;

    @BeforeEach
    public void setUp() {
        policy = new WeaponCarryPolicy();
    }

    @Test
    public void testIsExclusiveWeapon() {
        assertTrue(policy.isExclusiveWeapon("power_mace"));
        assertTrue(policy.isExclusiveWeapon("void_mace"));
        assertTrue(policy.isExclusiveWeapon("chronos_anchor_spear"));
        assertTrue(policy.isExclusiveWeapon("cursed_sword"));
        assertTrue(policy.isExclusiveWeapon("truce_sigil"));

        assertFalse(policy.isExclusiveWeapon(null));
        assertFalse(policy.isExclusiveWeapon("diamond_sword"));
        assertFalse(policy.isExclusiveWeapon("netherite_ingot"));
    }

    @Test
    public void testIsMace() {
        assertTrue(policy.isMace("power_mace"));
        assertTrue(policy.isMace("void_mace"));
        assertFalse(policy.isMace("chronos_anchor_spear"));
        assertFalse(policy.isMace("cursed_sword"));
        assertFalse(policy.isMace("truce_sigil"));
        assertFalse(policy.isMace(null));
    }

    @Test
    public void testIsSigil() {
        assertTrue(policy.isSigil("truce_sigil"));
        assertFalse(policy.isSigil("power_mace"));
        assertFalse(policy.isSigil("cursed_sword"));
        assertFalse(policy.isSigil(null));
    }

    @Test
    public void testIsLegalSet_EmptyOrSingle() {
        assertTrue(policy.isLegalSet(null));
        assertTrue(policy.isLegalSet(Collections.emptyList()));
        assertTrue(policy.isLegalSet(List.of("power_mace")));
        assertTrue(policy.isLegalSet(List.of("chronos_anchor_spear")));
        assertTrue(policy.isLegalSet(List.of("cursed_sword")));
        assertTrue(policy.isLegalSet(List.of("truce_sigil")));
    }

    @Test
    public void testIsLegalSet_MaceRestrictions() {
        // Mace must be solo
        assertFalse(policy.isLegalSet(Arrays.asList("power_mace", "truce_sigil")));
        assertFalse(policy.isLegalSet(Arrays.asList("power_mace", "chronos_anchor_spear")));
        assertFalse(policy.isLegalSet(Arrays.asList("power_mace", "cursed_sword")));
        assertFalse(policy.isLegalSet(Arrays.asList("power_mace", "void_mace")));
    }

    @Test
    public void testIsLegalSet_TwoWeapons() {
        // Two non-mace weapons without sigil -> illegal
        assertFalse(policy.isLegalSet(Arrays.asList("chronos_anchor_spear", "cursed_sword")));
        assertFalse(policy.isLegalSet(Arrays.asList("chronos_anchor_spear", "chronos_anchor_spear")));

        // Two sigils -> illegal
        assertFalse(policy.isLegalSet(Arrays.asList("truce_sigil", "truce_sigil")));

        // One sigil + one non-mace weapon -> legal
        assertTrue(policy.isLegalSet(Arrays.asList("truce_sigil", "chronos_anchor_spear")));
        assertTrue(policy.isLegalSet(Arrays.asList("truce_sigil", "cursed_sword")));
    }

    @Test
    public void testIsLegalSet_ThreeOrMoreWeapons() {
        assertFalse(policy.isLegalSet(Arrays.asList("truce_sigil", "chronos_anchor_spear", "cursed_sword")));
        assertFalse(policy.isLegalSet(Arrays.asList("truce_sigil", "truce_sigil", "chronos_anchor_spear")));
    }

    @Test
    public void testCanCarryAdditional() {
        // Holding nothing, can add anything
        assertTrue(policy.canCarryAdditional(Collections.emptyList(), "power_mace"));
        assertTrue(policy.canCarryAdditional(Collections.emptyList(), "truce_sigil"));
        assertTrue(policy.canCarryAdditional(Collections.emptyList(), "diamond_sword")); // not exclusive

        // Holding a mace, cannot add any other exclusive weapon
        assertFalse(policy.canCarryAdditional(List.of("power_mace"), "truce_sigil"));
        assertFalse(policy.canCarryAdditional(List.of("power_mace"), "chronos_anchor_spear"));
        assertTrue(policy.canCarryAdditional(List.of("power_mace"), "diamond_sword")); // non-exclusive allowed

        // Holding a spear, can add sigil
        assertTrue(policy.canCarryAdditional(List.of("chronos_anchor_spear"), "truce_sigil"));
        // Holding a spear, cannot add another spear/sword without sigil
        assertFalse(policy.canCarryAdditional(List.of("chronos_anchor_spear"), "cursed_sword"));

        // Holding a sigil, can add a spear
        assertTrue(policy.canCarryAdditional(List.of("truce_sigil"), "chronos_anchor_spear"));
        // Holding a sigil, cannot add a mace
        assertFalse(policy.canCarryAdditional(List.of("truce_sigil"), "power_mace"));
    }
}
