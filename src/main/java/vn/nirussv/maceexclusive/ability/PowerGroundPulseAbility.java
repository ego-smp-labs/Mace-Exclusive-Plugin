package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.ParticleProfile;
import vn.nirussv.maceexclusive.effect.SoundProfile;

public final class PowerGroundPulseAbility implements ActiveAbility {

    private static final String ID = "power_mace.ground_pulse";

    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final Plugin plugin;
    private final java.util.Random random = new java.util.Random();
    private final ParticleProfile particles = new ParticleProfile(Particle.EXPLOSION, 1, 0.0, 0.0, 0.0, 0.0);
    private final SoundProfile sound = new SoundProfile(Sound.ENTITY_GENERIC_EXPLODE, 0.9f, 0.75f);

    public PowerGroundPulseAbility(Plugin plugin, ConfigManager configManager, CooldownService cooldownService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cooldownService = cooldownService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String weaponId() {
        return "power_mace";
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        return context.player().isSneaking() && context.target() != null;
    }

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        if (!cooldownService.checkAndNotify(player, id())) {
            return;
        }

        applyHitEffects(player, context.target());
        context.target().damage(configManager.getItemEffectDouble("power_mace", "hit.damage", 4.0D), player);
        long cooldownMillis = configManager.getItemEffectInt("power_mace", "hit.cooldown-seconds", 8) * 1000L;
        cooldownService.setCooldown(player, id(), cooldownMillis);
    }

    public void applyHitEffects(Player player, LivingEntity target) {
        applyTemporarySpeed(player);
        if (random.nextDouble() >= configManager.getItemEffectDouble("power_mace", "hit.blast-chance", 0.10D)) {
            particles.play(target.getLocation().add(0.0D, 0.9D, 0.0D));
            sound.play(target.getLocation());
            return;
        }

        double radius = configManager.getItemEffectDouble("power_mace", "hit.blast-radius", 10.0D);
        double upward = configManager.getItemEffectDouble("power_mace", "hit.upward-velocity", 1.25D);

        Location origin = target.getLocation();
        for (LivingEntity entity : origin.getNearbyLivingEntities(radius, candidate -> !candidate.getUniqueId().equals(player.getUniqueId()))) {
            Vector away = entity.getLocation().toVector().subtract(origin.toVector());
            if (away.lengthSquared() > 0.0D) {
                away.normalize().multiply(0.25D).setY(upward);
                entity.setVelocity(entity.getVelocity().add(away));
            }
        }

        particles.play(origin.add(0.0D, 0.15D, 0.0D));
        sound.play(player.getLocation());

        net.kyori.adventure.text.Component msg = configManager.getItemMessage("power_mace", "messages.skill-ground-pulse");
        if (msg != null) {
            player.sendMessage(msg);
        }
    }

    private void applyTemporarySpeed(Player player) {
        AttributeInstance speed = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
        if (speed == null) return;
        NamespacedKey key = new NamespacedKey(plugin, "power_mace_speed");
        for (AttributeModifier modifier : java.util.List.copyOf(speed.getModifiers())) {
            if (key.equals(modifier.getKey())) speed.removeModifier(modifier);
        }
        speed.addModifier(new AttributeModifier(key, 0.05D, AttributeModifier.Operation.ADD_SCALAR));
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            AttributeInstance current = player.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (current == null) return;
            for (AttributeModifier modifier : java.util.List.copyOf(current.getModifiers())) {
                if (key.equals(modifier.getKey())) current.removeModifier(modifier);
            }
        }, 20L * 10L);
    }
}
