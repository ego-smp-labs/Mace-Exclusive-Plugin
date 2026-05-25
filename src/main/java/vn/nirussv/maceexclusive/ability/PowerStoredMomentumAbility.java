package vn.nirussv.maceexclusive.ability;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.ParticleProfile;
import vn.nirussv.maceexclusive.effect.SoundProfile;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;

public final class PowerStoredMomentumAbility implements PassiveAbility {

    private static final String ID = "power_mace.stored_momentum";

    private final ConfigManager configManager;
    private final ParticleProfile particles = new ParticleProfile(Particle.CRIT, 18, 0.35, 0.35, 0.35, 0.05);
    private final SoundProfile sound = new SoundProfile(Sound.ENTITY_PLAYER_ATTACK_KNOCKBACK, 0.8f, 0.9f);

    public PowerStoredMomentumAbility(ConfigManager configManager) {
        this.configManager = configManager;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ExclusiveItemId weaponId() {
        return ExclusiveItemId.POWER_MACE;
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        LivingEntity target = context.target();
        if (target == null) {
            return;
        }

        double downwardSpeed = Math.max(0.0D, -context.player().getVelocity().getY());
        double fallDistance = context.player().getFallDistance();
        if (downwardSpeed < 0.18D && fallDistance < 1.5F) {
            return;
        }

        double multiplier = configManager.getItemEffectDouble("power_mace", "stored-momentum.damage-multiplier", 1.15D);
        double knockback = configManager.getItemEffectDouble("power_mace", "stored-momentum.knockback", 0.35D);
        event.setDamage(event.getDamage() * Math.max(1.0D, multiplier));

        Vector direction = target.getLocation().toVector().subtract(context.player().getLocation().toVector());
        if (direction.lengthSquared() > 0.0D) {
            direction.normalize().multiply(knockback).setY(Math.max(0.18D, knockback * 0.55D));
            target.setVelocity(target.getVelocity().add(direction));
        }

        particles.play(target.getLocation().add(0.0D, 1.0D, 0.0D));
        sound.play(target.getLocation());
    }
}
