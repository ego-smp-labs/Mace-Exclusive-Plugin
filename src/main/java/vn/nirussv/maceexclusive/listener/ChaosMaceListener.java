package vn.nirussv.maceexclusive.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.mace.MaceFactory;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.mace.MaceType;
import vn.nirussv.maceexclusive.task.InventoryShuffleTask;

import java.util.Map;
import java.util.Random;

public class ChaosMaceListener implements Listener {

    private final MaceExclusivePlugin plugin;
    private final MaceManager maceManager;
    private final MaceFactory maceFactory;
    private final ConfigManager configManager;
    private final Random random = new Random();

    public ChaosMaceListener(MaceExclusivePlugin plugin, MaceManager maceManager, ConfigManager configManager, MaceFactory maceFactory) {
        this.plugin = plugin;
        this.maceManager = maceManager;
        this.maceFactory = maceFactory;
        this.configManager = configManager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        ItemStack result = event.getInventory().getResult();
        
        if (result == null || result.getType() != Material.MACE) return;
        
        if (maceFactory.isChaosMace(result)) {
            if (!maceManager.canCraft(MaceType.CHAOS)) {
                event.getInventory().setResult(null);
                return;
            }
            
            // Validate that all NETHER_STAR ingredients are Dark Ego items
            // (have the egosmp:dark_ego PDC tag from the EgoSMP plugin)
            NamespacedKey darkEgoKey = new NamespacedKey("egosmp", "dark_ego");
            for (ItemStack ingredient : event.getInventory().getMatrix()) {
                if (ingredient != null && ingredient.getType() == Material.NETHER_STAR) {
                    if (!ingredient.hasItemMeta() || 
                        !ingredient.getItemMeta().getPersistentDataContainer()
                            .has(darkEgoKey, PersistentDataType.BYTE)) {
                        // Plain Nether Star without Dark Ego tag - reject recipe
                        event.getInventory().setResult(null);
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftChaos(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (!maceFactory.isChaosMace(result)) return;

        if (event.isShiftClick()) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(configManager.getPrefixedMessage("mace.cannot-shift-click"));
            }
            return;
        }

        if (!maceManager.canCraft(MaceType.CHAOS)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                String holderName = maceManager.getHolderName(MaceType.CHAOS);
                player.sendMessage(configManager.getPrefixedMessage("chaos.already-exists", 
                    Map.of("player", holderName != null ? holderName : "Unknown")));
            }
            return;
        }

        if (event.getWhoClicked() instanceof Player player) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && maceFactory.isChaosMace(cursor)) {
                    if (maceManager.canCraft(MaceType.CHAOS)) {
                        if (maceManager.register(cursor, player.getUniqueId(), MaceType.CHAOS)) {
                            maceManager.onPlayerBecameHolder(player, player.getLocation(), MaceType.CHAOS);
                            applySelfCurse(player);
                        }
                    }
                    return;
                }
                
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && maceFactory.isChaosMace(item)) {
                        if (maceManager.canCraft(MaceType.CHAOS)) {
                            if (maceManager.register(item, player.getUniqueId(), MaceType.CHAOS)) {
                                maceManager.onPlayerBecameHolder(player, player.getLocation(), MaceType.CHAOS);
                                applySelfCurse(player);
                            }
                        }
                        break;
                    }
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickupChaos(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        
        if (maceManager.isChaosMace(item)) {
            maceManager.onPlayerBecameHolder(player, player.getLocation(), MaceType.CHAOS);
            applySelfCurse(player);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCombat(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof Player victim)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (maceManager.isChaosMace(weapon)) {
            double chance = plugin.getConfig().getDouble("mace-chaos.effects.shuffle-inventory.chance", 0.2);
            if (random.nextDouble() < chance) {
                int duration = plugin.getConfig().getInt("mace-chaos.effects.shuffle-inventory.duration", 5);
                int interval = plugin.getConfig().getInt("mace-chaos.effects.shuffle-inventory.interval", 5);
                
                victim.sendMessage("§c§k||| §r§cSYSTEM ERROR: INVENTORY CORRUPTION §c§k|||");
                new InventoryShuffleTask(victim, duration, interval).runTaskTimer(plugin, 0L, 1L);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();

        if (killer != null) {
            ItemStack weapon = killer.getInventory().getItemInMainHand();
            if (maceManager.isChaosMace(weapon)) {
                if (plugin.getConfig().getBoolean("mace-chaos.effects.glitch-kill-name", true)) {
                    event.deathMessage(
                        Component.text(victim.getName(), NamedTextColor.RED)
                        .append(Component.text(" was OBLITERATED by ", NamedTextColor.GRAY))
                        .append(Component.text("§kERROR_404", NamedTextColor.DARK_PURPLE))
                    );
                }
            }
        }
    }

    private void applySelfCurse(Player player) {
        if (!plugin.getConfig().getBoolean("mace-chaos.effects.self-curse.enabled", true)) return;
        
        int witherDur = plugin.getConfig().getInt("mace-chaos.effects.self-curse.wither-duration", 10) * 20;
        int shuffleDur = plugin.getConfig().getInt("mace-chaos.effects.self-curse.shuffle-duration", 10);
        
        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, witherDur, 1));
        
        player.sendMessage("§5The §kCHAOS§r§5 corrupts your body!");
        new InventoryShuffleTask(player, shuffleDur, 10).runTaskTimer(plugin, 0L, 1L);
    }
}
