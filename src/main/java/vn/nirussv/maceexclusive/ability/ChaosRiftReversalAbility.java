package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDeathEvent;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.ParticleProfile;
import vn.nirussv.maceexclusive.effect.SoundProfile;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class ChaosRiftReversalAbility implements ActiveAbility, PassiveAbility {

    private static final String ID = "chaos_mace.rift_reversal";

    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final Map<UUID, Mark> marks = new HashMap<>();
    private final ParticleProfile particles = new ParticleProfile(Particle.REVERSE_PORTAL, 40, 0.45, 0.7, 0.45, 0.08);
    private final SoundProfile sound = new SoundProfile(Sound.BLOCK_PORTAL_TRAVEL, 0.55f, 1.7f);

    public ChaosRiftReversalAbility(ConfigManager configManager, CooldownService cooldownService) {
        this.configManager = configManager;
        this.cooldownService = cooldownService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public ExclusiveItemId weaponId() {
        return ExclusiveItemId.CHAOS_MACE;
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        return context.player().isSneaking() && context.target() != null;
    }

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        LivingEntity target = context.target();
        if (target == null || !cooldownService.checkAndNotify(player, id())) {
            return;
        }

        Location playerLocation = player.getLocation().clone();
        Location targetLocation = target.getLocation().clone();
        particles.play(playerLocation.add(0, 1, 0));
        particles.play(targetLocation.clone().add(0, 1, 0));

        player.teleport(targetLocation.setDirection(player.getLocation().getDirection()));
        target.teleport(playerLocation.setDirection(target.getLocation().getDirection()));

        double damage = configManager.getItemEffectDouble("chaos_mace", "rift-reversal.damage", 5.0D);
        target.damage(damage, player);

        long windowMillis = configManager.getItemEffectInt("chaos_mace", "rift-reversal.backfire-window-seconds", 3) * 1000L;
        marks.put(target.getUniqueId(), new Mark(player.getUniqueId(), System.currentTimeMillis() + windowMillis));

        sound.play(player.getLocation());
        cooldownService.setCooldown(player, id(), configManager.getItemEffectInt("chaos_mace", "rift-reversal.cooldown-seconds", 18) * 1000L);

        net.kyori.adventure.text.Component msg = configManager.getItemMessage("chaos_mace", "messages.skill-rift-reversal");
        if (msg != null) {
            player.sendMessage(msg);
        }
    }

    @Override
    public void onDeath(AbilityContext context, EntityDeathEvent event) {
        Mark mark = marks.remove(event.getEntity().getUniqueId());
        if (mark == null || mark.expiresAt < System.currentTimeMillis()) {
            return;
        }
        Player player = event.getEntity().getServer().getPlayer(mark.userId);
        if (player == null || player.isDead()) {
            return;
        }
        double backfireDamage = configManager.getItemEffectDouble("chaos_mace", "rift-reversal.backfire-damage", 6.0D);
        particles.play(player.getLocation().add(0, 1, 0));
        player.damage(backfireDamage);
    }

    private record Mark(UUID userId, long expiresAt) {
    }
}
