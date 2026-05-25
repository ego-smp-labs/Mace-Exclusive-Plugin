package vn.nirussv.maceexclusive.effect;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;

public record SoundProfile(Sound sound, float volume, float pitch) {

    public void play(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }
        World world = location.getWorld();
        world.playSound(location, sound, volume, pitch);
    }
}
