package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.ParticleProfile;
import vn.nirussv.maceexclusive.effect.SoundProfile;

public final class ChaosFracturedStepAbility implements PassiveAbility {

    private static final String ID = "chaos_mace.fractured_step";

    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final ParticleProfile particles = new ParticleProfile(Particle.PORTAL, 28, 0.35, 0.8, 0.35, 0.1);
    private final SoundProfile sound = new SoundProfile(Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.25f);

    public ChaosFracturedStepAbility(ConfigManager configManager, CooldownService cooldownService) {
        this.configManager = configManager;
        this.cooldownService = cooldownService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String weaponId() {
        return "chaos_mace";
    }

    @Override
    public void onDamaged(AbilityContext context, EntityDamageByEntityEvent event) {
        Player player = context.player();
        if (!cooldownService.isReady(player, id())) {
            return;
        }
        if (!(context.source() instanceof LivingEntity attacker)) {
            return;
        }

        double chance = configManager.getItemEffectDouble("chaos_mace", "fractured-step.chance", 0.35D);
        if (Math.random() > chance) {
            return;
        }

        Location destination = behind(attacker, player.getLocation().getY());
        if (!destination.getBlock().isPassable() || !destination.clone().add(0, 1, 0).getBlock().isPassable()) {
            destination = player.getLocation().subtract(player.getLocation().getDirection().normalize().multiply(1.6D));
        }

        particles.play(player.getLocation().add(0, 1, 0));
        player.teleport(destination.setDirection(attacker.getLocation().subtract(destination).toVector()));
        particles.play(player.getLocation().add(0, 1, 0));
        sound.play(player.getLocation());

        net.kyori.adventure.text.Component msg = configManager.getItemMessage("chaos_mace", "messages.skill-fractured-step");
        if (msg != null) {
            player.sendMessage(msg);
        }

        double reduction = configManager.getItemEffectDouble("chaos_mace", "fractured-step.damage-reduction", 0.25D);
        event.setDamage(event.getDamage() * Math.max(0.0D, 1.0D - reduction));
        cooldownService.setCooldown(player, id(), configManager.getItemEffectInt("chaos_mace", "fractured-step.cooldown-seconds", 8) * 1000L);
    }

    private Location behind(LivingEntity entity, double fallbackY) {
        Vector direction = entity.getLocation().getDirection().normalize();
        Location destination = entity.getLocation().subtract(direction.multiply(1.8D));
        destination.setY(Math.max(destination.getY(), fallbackY - 1.0D));
        return destination;
    }
}
