package vn.nirussv.maceexclusive.forge;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import vn.nirussv.maceexclusive.effect.SafeParticleSpawner;
import vn.nirussv.maceexclusive.item.WeaponClass;

public final class ForgeVisualService {

    public void playCharge(Block block, String itemId, long elapsedTicks, long totalTicks) {
        if (block == null || block.getWorld() == null) return;
        if (WeaponClass.infer(itemId, null) == WeaponClass.SPEAR) {
            playSpearCharge(block, elapsedTicks);
            return;
        }
        playMaceCharge(block, elapsedTicks, totalTicks);
    }

    public void playChargeBurst(Block block) {
        if (block == null || block.getWorld() == null) return;
        Location center = block.getLocation().add(0.5, 1.0, 0.5);
        safeSpawn(block.getWorld(), Particle.EXPLOSION_EMITTER, center, 1, 0.0, 0.0, 0.0, 0.0);
        safeSpawn(block.getWorld(), Particle.ELECTRIC_SPARK, center, 18, 0.35, 0.35, 0.35, 0.08);
        safePlaySound(block.getWorld(), center, Sound.ENTITY_GENERIC_EXPLODE, 0.8f, 1.3f);
    }

    public void playCompletion(Block block) {
        if (block == null || block.getWorld() == null) return;
        Location center = block.getLocation().add(0.5, 1.0, 0.5);
        safeSpawn(block.getWorld(), Particle.TOTEM_OF_UNDYING, center, 60, 0.6, 0.6, 0.6, 0.12);
        safePlaySound(block.getWorld(), center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 0.8f);
    }

    private void playMaceCharge(Block block, long elapsedTicks, long totalTicks) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 1.05, 0.5);
        double progress = Math.min(1.0D, Math.max(0.0D, (double) elapsedTicks / Math.max(1L, totalTicks)));
        double radius = 2.5D - (2.1D * progress);
        for (int i = 0; i < 12; i++) {
            double angle = (Math.PI * 2.0D * i / 12.0D) + progress * Math.PI * 4.0D;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particle = center.clone().add(x, 0.15D * Math.sin(angle * 2), z);
            safeSpawn(world, Particle.ELECTRIC_SPARK, particle, 1, 0.01, 0.01, 0.01, 0.0);
            if (i % 4 == 0) safeSpawn(world, Particle.CRIT, particle, 1, -x / 10.0D, 0.02, -z / 10.0D, 0.08);
        }
        if (elapsedTicks % 10L == 0L) safePlaySound(world, center, Sound.BLOCK_BEACON_POWER_SELECT, 0.25f, 1.2f + (float) progress * 0.5f);
    }

    private void playSpearCharge(Block block, long elapsedTicks) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 1.1, 0.5);
        safeSpawn(world, Particle.ELECTRIC_SPARK, center, 12, 0.45, 0.45, 0.45, 0.06);
        safeSpawn(world, Particle.END_ROD, center, 6, 0.35, 0.6, 0.35, 0.03);
        safeSpawn(world, Particle.CLOUD, center, 4, 0.35, 0.1, 0.35, 0.01);
        if (elapsedTicks % 10L == 0L) {
            world.strikeLightningEffect(center);
            safePlaySound(world, center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.6f, 1.65f);
        }
    }

    private void safeSpawn(World world, Particle particle, Location location, int count, double offsetX, double offsetY, double offsetZ, double extra) {
        SafeParticleSpawner.spawn(world, particle, location, count, offsetX, offsetY, offsetZ, extra);
    }

    private void safePlaySound(World world, Location location, Sound sound, float volume, float pitch) {
        try {
            world.playSound(location, sound, volume, pitch);
        } catch (RuntimeException ignored) {
        }
    }

}
