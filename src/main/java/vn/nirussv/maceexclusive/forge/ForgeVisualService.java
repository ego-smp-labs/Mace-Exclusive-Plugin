package vn.nirussv.maceexclusive.forge;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;

public final class ForgeVisualService {

    public void playCharge(Block block, String itemId, long elapsedTicks, long totalTicks) {
        if (block == null || block.getWorld() == null) return;
        if (itemId != null && itemId.contains("spear")) {
            playSpearCharge(block, elapsedTicks);
            return;
        }
        playMaceCharge(block, elapsedTicks, totalTicks);
    }

    public void playChargeBurst(Block block) {
        if (block == null || block.getWorld() == null) return;
        Location center = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().spawnParticle(Particle.FLASH, center, 1);
        block.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, center, 1);
        block.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.3f);
    }

    public void playCompletion(Block block) {
        if (block == null || block.getWorld() == null) return;
        Location center = block.getLocation().add(0.5, 1.0, 0.5);
        block.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, center, 120, 0.6, 0.6, 0.6, 0.15);
        block.getWorld().playSound(center, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 0.8f);
    }

    private void playMaceCharge(Block block, long elapsedTicks, long totalTicks) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 1.05, 0.5);
        double progress = Math.min(1.0D, Math.max(0.0D, (double) elapsedTicks / Math.max(1L, totalTicks)));
        double radius = 2.5D - (2.1D * progress);
        for (int i = 0; i < 24; i++) {
            double angle = (Math.PI * 2.0D * i / 24.0D) + progress * Math.PI * 4.0D;
            double x = Math.cos(angle) * radius;
            double z = Math.sin(angle) * radius;
            Location particle = center.clone().add(x, 0.15D * Math.sin(angle * 2), z);
            world.spawnParticle(Particle.ELECTRIC_SPARK, particle, 1, 0.01, 0.01, 0.01, 0.0);
            if (i % 4 == 0) world.spawnParticle(Particle.CRIT, particle, 1, -x / 10.0D, 0.02, -z / 10.0D, 0.08);
        }
        world.playSound(center, Sound.BLOCK_BEACON_POWER_SELECT, 0.45f, 1.2f + (float) progress * 0.5f);
    }

    private void playSpearCharge(Block block, long elapsedTicks) {
        World world = block.getWorld();
        Location center = block.getLocation().add(0.5, 1.1, 0.5);
        world.spawnParticle(Particle.ELECTRIC_SPARK, center, 22, 0.45, 0.45, 0.45, 0.06);
        world.spawnParticle(Particle.END_ROD, center, 10, 0.35, 0.6, 0.35, 0.03);
        world.spawnParticle(Particle.CLOUD, center, 6, 0.35, 0.1, 0.35, 0.01);
        if (elapsedTicks % 10L == 0L) {
            world.strikeLightningEffect(center);
            world.playSound(center, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 1.65f);
        }
    }
}
