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
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class SpecialItemListener implements Listener {

    private final ExclusiveItemFactory itemFactory;
    private final ItemMatcher itemMatcher;
    private final Random random = new Random();
    private final Set<UUID> pendingChallengerEyeDeaths = new HashSet<>();

    public SpecialItemListener(ExclusiveItemFactory itemFactory, ItemMatcher itemMatcher) {
        this.itemFactory = itemFactory;
        this.itemMatcher = itemMatcher;
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
            boolean customObsidianChaos = false;

            if (mainHand != null && mainHand.getType() == Material.OBSIDIAN) {
                obsidianStack = mainHand;
                inMainHand = true;
            } else if (offHand != null && offHand.getType() == Material.OBSIDIAN) {
                obsidianStack = offHand;
            } else if (itemMatcher.is(mainHand, "obsidian_chaos")) {
                obsidianStack = mainHand;
                inMainHand = true;
                customObsidianChaos = true;
            } else if (itemMatcher.is(offHand, "obsidian_chaos")) {
                obsidianStack = offHand;
                customObsidianChaos = true;
            }

            if (obsidianStack != null) {
                if (random.nextDouble() < 0.05) {
                    if (!customObsidianChaos) {
                        obsidianStack.setAmount(obsidianStack.getAmount() - 1);
                        if (inMainHand) {
                            player.getInventory().setItemInMainHand(obsidianStack.getAmount() > 0 ? obsidianStack : null);
                        } else {
                            player.getInventory().setItemInOffHand(obsidianStack.getAmount() > 0 ? obsidianStack : null);
                        }
                    }
                    player.getWorld().dropItemNaturally(player.getLocation(), itemFactory.create("obsidian_chaos"));
                    player.sendMessage("§5[Mace-Exclusive] Một mảnh Obsidian Chaos đã rơi ra từ vụ nổ!");
                    player.damage(Math.max(1000.0D, player.getHealth() + player.getAbsorptionAmount() + 100.0D), event.getEntity());
                    if (!player.isDead()) {
                        player.setHealth(0.0D);
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlayerResurrect(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (player.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
            if (damageEvent.getDamager().getType() == EntityType.ENDERMAN) {
                pendingChallengerEyeDeaths.add(player.getUniqueId());
                event.setCancelled(true);
                player.sendMessage("§d[Mace-Exclusive] The Enderman's challenge rejects your Totem. The Eye will fall with you.");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (!pendingChallengerEyeDeaths.remove(player.getUniqueId())) return;
        if (!(player.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent)) return;
        if (damageEvent.getDamager().getType() != EntityType.ENDERMAN) return;
        try {
            event.getDrops().add(itemFactory.create("challenger_eye"));
        } catch (Exception ignored) {
        }
    }
}
