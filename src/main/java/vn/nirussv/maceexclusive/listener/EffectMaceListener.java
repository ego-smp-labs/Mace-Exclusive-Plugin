package vn.nirussv.maceexclusive.listener;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.PerformanceConfig;
import vn.nirussv.maceexclusive.mace.MaceManager;

import java.util.ArrayList;
import java.util.List;

public class EffectMaceListener implements Listener {

    private final MaceExclusivePlugin plugin;
    private final MaceManager maceManager;
    private final ConfigManager configManager;

    public EffectMaceListener(MaceExclusivePlugin plugin, MaceManager maceManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.maceManager = maceManager;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraftMace(CraftItemEvent event) {
        PerformanceConfig performance = configManager.getPerformanceConfig();
        if (!performance.firstCraftGlowing()) return;
        
        ItemStack result = event.getRecipe().getResult();
        if (result.getType() != Material.MACE) return;

        // Ensure it is our Mace (should be restricted by item identity/manager logic already, but safe check)
        if (event.isCancelled()) return;

        if (event.getWhoClicked() instanceof Player player) {
            int duration = performance.firstCraftDurationSeconds() * 20;
            player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, duration, 0));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();

        if (maceManager.isRegisteredMace(weapon)) {
            if (configManager.getPerformanceConfig().groundSlamEnabled()) {
                performGroundSlam(attacker, event.getEntity().getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        if (!configManager.getPerformanceConfig().customKillMessage()) return;

        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
             ItemStack weapon = killer.getInventory().getItemInMainHand();
             if (maceManager.isRegisteredMace(weapon)) {
                 Component msg = configManager.getPrefixedMessage("mace.kill-message", 
                     java.util.Map.of("killer", killer.getName(), "victim", victim.getName()));
                 event.deathMessage(msg);
             }
        }
    }

    private void performGroundSlam(Player attacker, org.bukkit.Location targetLoc) {
        PerformanceConfig performance = configManager.getPerformanceConfig();
        int radius = performance.groundSlamRadius();
        List<Block> blocks = new ArrayList<>();
        
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                 Block block = targetLoc.clone().add(x, -1, z).getBlock();
                  if (!block.getType().isAir() && block.getType().isSolid() && blocks.size() < performance.groundSlamMaxBlocks()) {
                      blocks.add(block);
                  }
            }
        }

        for (Block block : blocks) {
             FallingBlock fb = block.getWorld().spawnFallingBlock(block.getLocation().add(0, 1, 0), block.getBlockData());
             fb.setVelocity(new Vector(0, 0.4, 0));
             fb.setDropItem(false);
        }
        attacker.playSound(targetLoc, Sound.ENTITY_GENERIC_EXPLODE, 1f, 0.5f);
    }
}
