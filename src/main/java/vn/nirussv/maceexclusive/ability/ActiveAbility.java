package vn.nirussv.maceexclusive.ability;

import vn.nirussv.maceexclusive.item.ExclusiveItemId;

public interface ActiveAbility {

    String id();

    ExclusiveItemId weaponId();

    boolean canActivate(AbilityContext context);

    void activate(AbilityContext context);
}
