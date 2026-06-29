package vn.nirussv.maceexclusive.effect;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.inventory.ItemStack;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public final class SafeParticleSpawner {

    private static final Set<String> WARNED_PARTICLES = ConcurrentHashMap.newKeySet();

    private SafeParticleSpawner() {
    }

    public static void spawn(World world, Particle particle, Location location, int count) {
        spawn(world, particle, location, count, 0.0D, 0.0D, 0.0D, 0.0D);
    }

    public static void spawn(World world, Particle particle, Location location, int count,
                             double offsetX, double offsetY, double offsetZ, double extra) {
        if (world == null || particle == null || location == null) {
            return;
        }
        try {
            spawnWithRequiredData(world, particle, location, count, offsetX, offsetY, offsetZ, extra);
        } catch (IllegalArgumentException exception) {
            warnOnce(particle, exception);
        } catch (UnsupportedOperationException exception) {
            warnOnce(particle, exception);
        } catch (RuntimeException exception) {
            Bukkit.getLogger().log(Level.SEVERE, "[Mace-Exclusive] Unexpected particle spawn failure for " + particle, exception);
        }
    }

    private static void spawnWithRequiredData(World world, Particle particle, Location location, int count,
                                              double offsetX, double offsetY, double offsetZ, double extra) {
        Class<?> dataType = particle.getDataType();
        if (dataType == Void.class) {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra);
            return;
        }

        Object data = defaultData(dataType, extra);
        if (data != null) {
            world.spawnParticle(particle, location, count, offsetX, offsetY, offsetZ, extra, data);
            return;
        }

        world.spawnParticle(Particle.END_ROD, location, count, offsetX, offsetY, offsetZ, extra);
    }

    private static Object defaultData(Class<?> dataType, double extra) {
        if (dataType == Float.class) {
            return Float.valueOf((float) Math.max(0.01D, extra));
        }
        if (dataType == Particle.DustOptions.class) {
            return new Particle.DustOptions(Color.WHITE, 1.0F);
        }
        if (dataType == Particle.DustTransition.class) {
            return new Particle.DustTransition(Color.WHITE, Color.GRAY, 1.0F);
        }
        if (dataType == BlockData.class) {
            return Bukkit.createBlockData(Material.STONE);
        }
        if (dataType == ItemStack.class) {
            return new ItemStack(Material.STONE);
        }
        if (dataType == Color.class) {
            return Color.WHITE;
        }
        return null;
    }

    private static void warnOnce(Particle particle, RuntimeException exception) {
        String key = particle.name() + ':' + exception.getClass().getSimpleName();
        if (WARNED_PARTICLES.add(key)) {
            Bukkit.getLogger().warning("[Mace-Exclusive] Skipping unsupported particle " + particle + ": " + exception.getMessage());
        }
    }
}
