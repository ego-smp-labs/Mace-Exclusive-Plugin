package vn.nirussv.maceexclusive.config;

import org.bukkit.configuration.file.FileConfiguration;

public record PerformanceConfig(
    int holdingEffectTickRate,
    boolean firstCraftGlowing,
    int firstCraftDurationSeconds,
    boolean holdingGlowing,
    boolean holdingSoulParticles,
    int particleCount,
    double particleOffsetX,
    double particleOffsetY,
    double particleOffsetZ,
    double particleExtra,
    boolean customKillMessage,
    boolean groundSlamEnabled,
    int groundSlamRadius,
    int groundSlamMaxBlocks
) {

    static PerformanceConfig fromConfig(FileConfiguration config) {
        return new PerformanceConfig(
            config.getInt("performance.holding-effect-tick-rate", 5),
            config.getBoolean("performance.first-craft.glowing", config.getBoolean("effects.first-craft.glowing", true)),
            config.getInt("performance.first-craft.duration", config.getInt("effects.first-craft.duration", 300)),
            config.getBoolean("performance.holding.glowing", config.getBoolean("effects.holding.glowing", false)),
            config.getBoolean("performance.holding.soul-particles", config.getBoolean("effects.holding.soul-particles", false)),
            config.getInt("performance.holding.particle-count", 5),
            config.getDouble("performance.holding.particle-offset-x", 0.3),
            config.getDouble("performance.holding.particle-offset-y", 0.1),
            config.getDouble("performance.holding.particle-offset-z", 0.3),
            config.getDouble("performance.holding.particle-extra", 0.05),
            config.getBoolean("performance.combat.custom-kill-message", config.getBoolean("effects.combat.custom-kill-message", true)),
            config.getBoolean("performance.combat.ground-slam.enabled", config.getBoolean("effects.combat.ground-slam.enabled", false)),
            config.getInt("performance.combat.ground-slam.radius", config.getInt("effects.combat.ground-slam.radius", 3)),
            config.getInt("performance.combat.ground-slam.max-blocks", 64)
        );
    }
}
