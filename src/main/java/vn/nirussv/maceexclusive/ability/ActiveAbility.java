package vn.nirussv.maceexclusive.ability;

public interface ActiveAbility {

    String id();

    String weaponId();

    boolean canActivate(AbilityContext context);

    void activate(AbilityContext context);
}
