package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.ParticleProfile;
import vn.nirussv.maceexclusive.effect.SoundProfile;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;

public final class PowerGroundPulseAbility implements ActiveAbility {

    private static final String ID = "power_mace.ground_pulse";

    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final ParticleProfile particles = new ParticleProfile(Particle.EXPLOSION, 1, 0.0, 0.0, 0.0, 0.0);
    private final SoundProfile sound = new SoundProfile(Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.75f);

    public PowerGroundPulseAbility(ConfigManager configManager, CooldownService cooldownService) {
        this.configManager = configManager;
        this.cooldownService = cooldownService;
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
    public boolean canActivate(AbilityContext context) {
        return context.player().isSneaking() && context.player().isOnGround();
    }

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        if (!cooldownService.checkAndNotify(player, id())) {
            return;
        }

        double radius = configManager.getItemEffectDouble("power_mace", "ground-pulse.radius", 4.0D);
        double damage = configManager.getItemEffectDouble("power_mace", "ground-pulse.damage", 4.0D);
        double upward = configManager.getItemEffectDouble("power_mace", "ground-pulse.upward-velocity", 0.55D);
        long cooldownMillis = configManager.getItemEffectInt("power_mace", "ground-pulse.cooldown-seconds", 12) * 1000L;

        Location origin = player.getLocation();
        for (LivingEntity entity : origin.getNearbyLivingEntities(radius, candidate -> !candidate.getUniqueId().equals(player.getUniqueId()))) {
            entity.damage(damage, player);
            Vector away = entity.getLocation().toVector().subtract(origin.toVector());
            if (away.lengthSquared() > 0.0D) {
                away.normalize().multiply(0.45D).setY(upward);
                entity.setVelocity(entity.getVelocity().add(away));
            }
        }

        particles.play(origin.add(0.0D, 0.15D, 0.0D));
        sound.play(player.getLocation());
        cooldownService.setCooldown(player, id(), cooldownMillis);

        net.kyori.adventure.text.Component msg = configManager.getItemMessage("power_mace", "messages.skill-ground-pulse");
        if (msg != null) {
            player.sendMessage(msg);
        }
    }
}
