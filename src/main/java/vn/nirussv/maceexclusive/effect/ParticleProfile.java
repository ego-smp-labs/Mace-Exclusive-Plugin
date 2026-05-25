package vn.nirussv.maceexclusive.effect;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;

public record ParticleProfile(
    Particle particle,
    int count,
    double offsetX,
    double offsetY,
    double offsetZ,
    double extra
) {

    public void play(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
    }
}
