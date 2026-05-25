package vn.nirussv.maceexclusive.task;

import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import vn.nirussv.maceexclusive.config.ConfigManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class InventoryShuffleTask extends BukkitRunnable {

    private final Player player;
    private final int durationTicks;
    private final ConfigManager configManager;
    private int ticksElapsed = 0;
    private final int interval;

    public InventoryShuffleTask(Player player, int durationSeconds, int intervalTicks, ConfigManager configManager) {
        this.player = player;
        this.durationTicks = durationSeconds * 20;
        this.interval = intervalTicks;
        this.configManager = configManager;
    }

    @Override
    public void run() {
        if (!player.isOnline() || player.isDead()) {
            this.cancel();
            return;
        }

        if (ticksElapsed >= durationTicks) {
            this.cancel();
            player.sendMessage(configManager.getMessage("chaos.inventory-stabilized"));
            return;
        }

        if (ticksElapsed % interval == 0) {
            shuffleInventory(player);
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 1f, 2f);
        }

        ticksElapsed++;
    }

    private void shuffleInventory(Player p) {
        PlayerInventory inv = p.getInventory();
        
        List<ItemStack> contents = new ArrayList<>();
        for (int i = 0; i < 36; i++) {
            ItemStack item = inv.getItem(i);
            contents.add(item); 
        }
        
        Collections.shuffle(contents);
        
        for (int i = 0; i < 36; i++) {
            inv.setItem(i, contents.get(i));
        }
    }
}
