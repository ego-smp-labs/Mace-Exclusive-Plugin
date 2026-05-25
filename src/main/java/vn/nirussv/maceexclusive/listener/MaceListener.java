package vn.nirussv.maceexclusive.listener;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.CrafterCraftEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.mace.MaceFactory;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.mace.MaceType;

import java.util.Map;

public class MaceListener implements Listener {

    private final MaceExclusivePlugin plugin;
    private final MaceManager maceManager;
    private final ConfigManager configManager;
    private final MaceFactory maceFactory;

    public MaceListener(MaceExclusivePlugin plugin, MaceManager maceManager, ConfigManager configManager, MaceFactory maceFactory) {
        this.plugin = plugin;
        this.maceManager = maceManager;
        this.configManager = configManager;
        this.maceFactory = maceFactory;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        
        if (maceManager.isPowerMace(weapon)) {
            victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            attacker.playSound(attacker.getLocation(), Sound.ENTITY_WARDEN_ATTACK_IMPACT, 1f, 1f);
        }
    }

    @EventHandler
    public void onMaceInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        
        Player player = event.getPlayer();
        ItemStack item = event.getItem();
        
        if (item == null || item.getType() != Material.MACE) return;
        
        MaceType type = maceManager.getMaceType(item);
        if (type == null) {
            if (maceManager.canCraft(MaceType.POWER)) {
                if (maceManager.register(item, player.getUniqueId(), MaceType.POWER)) {
                    maceManager.onPlayerBecameHolder(player, player.getLocation(), MaceType.POWER);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPrepareCraft(PrepareItemCraftEvent event) {
        if (event.getRecipe() == null) return;
        ItemStack result = event.getInventory().getResult();
        
        if (result == null || result.getType() != Material.MACE) return;
        
        MaceType type = maceFactory.getMaceType(result);
        if (type == null) {
            type = maceFactory.getAwakeningResult(result).orElse(null);
        }
        if (type == null) return;
        
        if (!maceManager.canCraft(type)) {
            event.getInventory().setResult(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCrafterCraft(CrafterCraftEvent event) {
        ItemStack result = event.getResult();
        if (result == null || result.getType() != Material.MACE) return;
        
        event.setCancelled(true);
        
        if (configManager.isVerboseLogging()) {
            plugin.getLogger().info("Blocked Crafter from crafting Mace at " + event.getBlock().getLocation());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCraftMace(CraftItemEvent event) {
        ItemStack result = event.getRecipe().getResult();
        if (result.getType() != Material.MACE) return;

        MaceType type = maceFactory.getMaceType(result);
        boolean unawakenedRecipe = false;
        if (type == null) {
            type = maceFactory.getAwakeningResult(result).orElse(null);
            unawakenedRecipe = type != null;
        }
        if (type == null || type == MaceType.CHAOS) return;

        if (event.isShiftClick() && configManager.isCraftingShiftClickPrevented()) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                player.sendMessage(configManager.getPrefixedMessage("mace.cannot-shift-click"));
            }
            return;
        }
    
        if (!maceManager.canCraft(type)) {
            event.setCancelled(true);
            if (event.getWhoClicked() instanceof Player player) {
                String holderName = maceManager.getHolderName(type);
                String msgKey = type == MaceType.CHAOS ? "chaos.already-exists" : "mace.already-exists";
                player.sendMessage(configManager.getPrefixedMessage(msgKey, 
                    Map.of("player", holderName != null ? holderName : "Unknown")));
            }
            return;
        }

        if (unawakenedRecipe) {
            return;
        }

        if (event.getWhoClicked() instanceof Player player) {
            MaceType finalType = type;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                ItemStack cursor = player.getItemOnCursor();
                if (cursor != null && cursor.getType() == Material.MACE) {
                    MaceType cursorType = maceFactory.getMaceType(cursor);
                    if (cursorType == finalType && maceManager.canCraft(finalType)) {
                        if (maceManager.register(cursor, player.getUniqueId(), finalType)) {
                            maceManager.onPlayerBecameHolder(player, player.getLocation(), finalType);
                        }
                    }
                    return;
                }
                
                for (ItemStack item : player.getInventory().getContents()) {
                    if (item != null && item.getType() == Material.MACE) {
                        MaceType itemType = maceFactory.getMaceType(item);
                        if (itemType == finalType && maceManager.canCraft(finalType)) {
                            if (maceManager.register(item, player.getUniqueId(), finalType)) {
                                maceManager.onPlayerBecameHolder(player, player.getLocation(), finalType);
                            }
                            break;
                        }
                    }
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickupMace(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack item = event.getItem().getItemStack();
        
        MaceType type = maceManager.getMaceType(item);
        if (type == MaceType.POWER) {
            maceManager.onPlayerBecameHolder(player, player.getLocation(), MaceType.POWER);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerDrop(PlayerDropItemEvent event) {
        ItemStack item = event.getItemDrop().getItemStack();
        
        if (maceManager.isRegisteredMace(item)) {
            if (configManager.isStrictMode() || !configManager.isDropAllowed()) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(configManager.getPrefixedMessage("mace.cannot-drop"));
            }
        }
    }

}
