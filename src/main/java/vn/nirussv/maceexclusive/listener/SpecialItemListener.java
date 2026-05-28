package vn.nirussv.maceexclusive.listener;

import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;

import java.util.Random;

public final class SpecialItemListener implements Listener {

    private final ExclusiveItemFactory itemFactory;
    private final Random random = new Random();

    public SpecialItemListener(ExclusiveItemFactory itemFactory) {
        this.itemFactory = itemFactory;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreeperExplode(EntityExplodeEvent event) {
        if (event.getEntityType() != EntityType.CREEPER) return;

        double radius = 5.0;
        for (Entity entity : event.getLocation().getWorld().getNearbyEntities(event.getLocation(), radius, radius, radius)) {
            if (!(entity instanceof Player player)) continue;

            ItemStack mainHand = player.getInventory().getItemInMainHand();
            ItemStack offHand = player.getInventory().getItemInOffHand();
            ItemStack obsidianStack = null;
            boolean inMainHand = false;

            if (mainHand != null && mainHand.getType() == Material.OBSIDIAN) {
                obsidianStack = mainHand;
                inMainHand = true;
            } else if (offHand != null && offHand.getType() == Material.OBSIDIAN) {
                obsidianStack = offHand;
            }

            if (obsidianStack != null) {
                if (random.nextDouble() < 0.05) {
                    obsidianStack.setAmount(obsidianStack.getAmount() - 1);
                    if (inMainHand) {
                        player.getInventory().setItemInMainHand(obsidianStack.getAmount() > 0 ? obsidianStack : null);
                    } else {
                        player.getInventory().setItemInOffHand(obsidianStack.getAmount() > 0 ? obsidianStack : null);
                    }
                    player.getWorld().dropItemNaturally(player.getLocation(), itemFactory.create("obsidian_chaos"));
                    player.sendMessage("§5[Mace-Exclusive] Một mảnh Obsidian Chaos đã rơi ra từ vụ nổ!");
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (player.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
            if (damageEvent.getDamager().getType() == EntityType.ENDERMAN) {
                try {
                    ItemStack eye = itemFactory.create("challenger_eye");
                    player.getWorld().dropItemNaturally(player.getLocation(), eye);
                    player.sendMessage("§d[Mace-Exclusive] Bạn đã thu thập được Mắt của kẻ thách thức từ cái chết cận kề!");
                } catch (Exception e) {
                    // Fail-safe
                }
            }
        }
    }
}
