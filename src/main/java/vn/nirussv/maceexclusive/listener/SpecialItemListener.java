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
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.block.BlockPlaceEvent;

public final class SpecialItemListener implements Listener {

    private final ExclusiveItemFactory itemFactory;
    private final ItemMatcher itemMatcher;
    private final ConfigManager configManager;
    private final Random random = new Random();
    private final Set<UUID> pendingChallengerEyeDeaths = new HashSet<>();
    private final Map<UUID, SlaughterTracker> slaughterMap = new HashMap<>();

    private static final class SlaughterTracker {
        int killCount = 0;
        long lastKillTime = 0L;
    }

    public SpecialItemListener(ExclusiveItemFactory itemFactory, ItemMatcher itemMatcher, ConfigManager configManager) {
        this.itemFactory = itemFactory;
        this.itemMatcher = itemMatcher;
        this.configManager = configManager;
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
                    player.sendMessage(configManager.getMessage("special.obsidian-chaos-drop"));
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

        if (hasVoidMaceInInventory(player)) {
            event.setCancelled(true);
            player.sendMessage(configManager.getMessage("special.void-totem-blocked"));
            return;
        }

        if (player.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent) {
            if (damageEvent.getDamager().getType() == EntityType.ENDERMAN) {
                pendingChallengerEyeDeaths.add(player.getUniqueId());
                event.setCancelled(true);
                player.sendMessage(configManager.getMessage("special.enderman-totem-blocked"));
            }
        }
    }

    private boolean hasVoidMaceInInventory(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && itemMatcher.is(item, "void_mace")) return true;
        }
        ItemStack offHand = player.getInventory().getItemInOffHand();
        if (offHand != null && itemMatcher.is(offHand, "void_mace")) return true;
        return false;
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

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(PlayerQuitEvent event) {
        slaughterMap.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMobSlaughter(EntityDeathEvent event) {
        Player player = event.getEntity().getKiller();
        if (player == null) return;
        if (player.getWorld().getEnvironment() != org.bukkit.World.Environment.NETHER) return;

        ItemStack mainHand = player.getInventory().getItemInMainHand();
        ItemStack offHand = player.getInventory().getItemInOffHand();
        boolean holdingHead = (mainHand != null && mainHand.getType() == Material.PLAYER_HEAD)
                           || (offHand != null && offHand.getType() == Material.PLAYER_HEAD);
        if (!holdingHead) return;

        long now = System.currentTimeMillis();
        SlaughterTracker tracker = slaughterMap.computeIfAbsent(player.getUniqueId(), k -> new SlaughterTracker());
        if (now - tracker.lastKillTime > 30000L) {
            tracker.killCount = 1;
        } else {
            tracker.killCount++;
        }
        tracker.lastKillTime = now;

        if (tracker.killCount >= 15) {
            tracker.killCount = 0;
            boolean consumed = false;
            if (mainHand != null && mainHand.getType() == Material.PLAYER_HEAD) {
                mainHand.setAmount(mainHand.getAmount() - 1);
                player.getInventory().setItemInMainHand(mainHand.getAmount() > 0 ? mainHand : null);
                consumed = true;
            } else if (offHand != null && offHand.getType() == Material.PLAYER_HEAD) {
                offHand.setAmount(offHand.getAmount() - 1);
                player.getInventory().setItemInOffHand(offHand.getAmount() > 0 ? offHand : null);
                consumed = true;
            }

            if (consumed) {
                ItemStack cursedHead = itemFactory.create("cursed_player_head");
                java.util.HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(cursedHead);
                for (ItemStack item : leftover.values()) {
                    player.getWorld().dropItemNaturally(player.getLocation(), item);
                }
                player.getWorld().strikeLightningEffect(player.getLocation());
                player.getWorld().spawnParticle(org.bukkit.Particle.SOUL_FIRE_FLAME, player.getLocation().add(0, 1, 0), 32, 0.5, 0.5, 0.5, 0.05);
                player.getWorld().playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EVOKER_PREPARE_ATTACK, 1.0f, 0.8f);
                player.sendMessage(configManager.getMessage("nether.cursed-head-forged"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (itemMatcher.matchCore(item).isPresent()) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(configManager.getMessage("core.cannot-place"));
        }
    }
}
