package vn.nirussv.maceexclusive.ability;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.FreezeService;

import java.util.*;

public final class VoidMaceAbility implements ActiveAbility, PassiveAbility, Listener {

    private static final String ID = "void_mace.resurrection";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final FreezeService freezeService;
    private final Random random = new Random();

    // Map to keep track of locked slots: Target UUID -> (Slot index -> Original ItemStack)
    private final Map<UUID, Map<Integer, ItemStack>> lockedSlots = new HashMap<>();
    
    // In-memory states for active resurrection state
    private final Map<UUID, Long> resurrectionEnds = new HashMap<>();

    public VoidMaceAbility(MaceExclusivePlugin plugin, ConfigManager configManager, CooldownService cooldownService, FreezeService freezeService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        this.freezeService = freezeService;
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String weaponId() {
        return "void_mace";
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        // Active is triggered on near-fatal damage, not on command
        return false;
    }

    @Override
    public void activate(AbilityContext context) {
        // Handled via onFatalDamage
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFatalDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        
        ItemStack weapon = player.getInventory().getItemInMainHand();
        if (weapon == null || !plugin.getMaceManager().getExclusiveItemKey(weapon).filter(id -> id.equals("void_mace")).isPresent()) return;

        if (player.getHealth() - event.getFinalDamage() <= 0.0) {
            UUID uuid = player.getUniqueId();
            if (!cooldownService.isReady(player, id())) return;

            // Must be entity damage. Environmental deaths do not resurrect.
            if (!(player.getLastDamageCause() instanceof EntityDamageByEntityEvent damageEvent)) return;
            org.bukkit.entity.Entity damager = damageEvent.getDamager();
            if (damager instanceof org.bukkit.entity.Projectile proj && proj.getShooter() instanceof org.bukkit.entity.Entity shooter) {
                damager = shooter;
            }

            double baseChance = 0.0D;
            if (damager instanceof Player) {
                baseChance = 0.25D; // 25% if killed by player
            } else if (damager instanceof LivingEntity) {
                baseChance = 0.05D; // 5% if killed by mob
            } else {
                return; // Non-living entity or other causes don't resurrect
            }

            // Check if anyone within 10 blocks is holding a Totem
            boolean nearbyTotem = false;
            for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(10.0, 10.0, 10.0)) {
                if (nearby instanceof Player otherPlayer && !otherPlayer.getUniqueId().equals(player.getUniqueId())) {
                    ItemStack mainHand = otherPlayer.getInventory().getItemInMainHand();
                    ItemStack offHand = otherPlayer.getInventory().getItemInOffHand();
                    if ((mainHand != null && mainHand.getType() == Material.TOTEM_OF_UNDYING)
                            || (offHand != null && offHand.getType() == Material.TOTEM_OF_UNDYING)) {
                        nearbyTotem = true;
                        break;
                    }
                }
            }

            double finalChance = baseChance;
            if (nearbyTotem) {
                finalChance += 0.50D; // Boost by +50% if totem nearby
            }

            if (random.nextDouble() < finalChance) {
                event.setCancelled(true);
                player.setHealth(1.0D); // Keep alive
                
                long durationSeconds = configManager.getItemEffectInt("void_mace", "effects.resurrect.duration", 30);
                long cooldownSeconds = configManager.getItemEffectInt("void_mace", "cooldowns.resurrection", 600);
                
                resurrectionEnds.put(uuid, System.currentTimeMillis() + (durationSeconds * 1000L));
                cooldownService.setCooldown(player, id(), cooldownSeconds * 1000L);

                // Apply Potion Effects: 10 absorption hearts (level 4 is 5 * 2 = 10 hearts = 20 health)
                player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, (int) durationSeconds * 20, 4, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, (int) durationSeconds * 20, 0, false, false, true));
                player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, false, false, true));

                player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1.0f, 0.8f);
                net.kyori.adventure.text.Component msg = configManager.getItemMessage("void_mace", "messages.skill-resurrect");
                if (msg != null) player.sendMessage(msg);

                // Force trigger and consume totem of nearby opponents within 10 blocks
                for (org.bukkit.entity.Entity nearby : player.getNearbyEntities(10.0, 10.0, 10.0)) {
                    if (nearby instanceof Player otherPlayer && !otherPlayer.getUniqueId().equals(player.getUniqueId())) {
                        ItemStack mainHand = otherPlayer.getInventory().getItemInMainHand();
                        ItemStack offHand = otherPlayer.getInventory().getItemInOffHand();
                        boolean triggered = false;
                        if (mainHand != null && mainHand.getType() == Material.TOTEM_OF_UNDYING) {
                            mainHand.setAmount(mainHand.getAmount() - 1);
                            otherPlayer.getInventory().setItemInMainHand(mainHand.getAmount() > 0 ? mainHand : null);
                            triggered = true;
                        } else if (offHand != null && offHand.getType() == Material.TOTEM_OF_UNDYING) {
                            offHand.setAmount(offHand.getAmount() - 1);
                            otherPlayer.getInventory().setItemInOffHand(offHand.getAmount() > 0 ? offHand : null);
                            triggered = true;
                        }
                        if (triggered) {
                            otherPlayer.playEffect(org.bukkit.EntityEffect.TOTEM_RESURRECT);
                            net.kyori.adventure.text.Component stolenMsg = configManager.getItemMessage("void_mace", "messages.totem-stolen");
                            if (stolenMsg != null) otherPlayer.sendMessage(stolenMsg);
                        }
                    }
                }

                // Schedule end of resurrection state
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    resurrectionEnds.remove(uuid);
                }, durationSeconds * 20L);
            }
        }
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        LivingEntity target = context.target();
        if (target == null) return;
        UUID attackerUuid = attacker.getUniqueId();

        // 1. If "Resurrected from the Abyss" is active, 50% chance to cause Mind Detachment (Freeze + Blindness) for 5s
        if (resurrectionEnds.containsKey(attackerUuid) && System.currentTimeMillis() < resurrectionEnds.get(attackerUuid)) {
            double mdChance = configManager.getItemEffectDouble("void_mace", "effects.resurrect.mind_detachment_chance", 0.50D);
            if (random.nextDouble() < mdChance) {
                int mdDur = configManager.getItemEffectInt("void_mace", "effects.resurrect.mind_detachment_duration", 5);
                target.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, mdDur * 20, 0));
                target.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, mdDur * 20, 0));
                freezeService.freeze(target, mdDur * 20);

                net.kyori.adventure.text.Component msg = configManager.getItemMessage("void_mace", "messages.skill-mind-detached");
                if (msg != null) attacker.sendMessage(msg);
            }
        }

        // 2. Passive: Devour Matter. 10% chance to lock 2 random hotbar slots for 5s
        double devourChance = configManager.getItemEffectDouble("void_mace", "effects.devour.chance", 0.10D);
        if (random.nextDouble() < devourChance) {
            if (target instanceof Player victim) {
                UUID victimUuid = victim.getUniqueId();
                if (!lockedSlots.containsKey(victimUuid)) {
                    int devourDur = configManager.getItemEffectInt("void_mace", "effects.devour.duration", 5);
                    lockHotbarSlots(victim, devourDur);
                }
            }
        }
    }

    private void lockHotbarSlots(Player player, int durationSeconds) {
        UUID uuid = player.getUniqueId();
        List<Integer> slots = new ArrayList<>(List.of(0, 1, 2, 3, 4, 5, 6, 7, 8));
        Collections.shuffle(slots);
        int slot1 = slots.get(0);
        int slot2 = slots.get(1);

        Map<Integer, ItemStack> savedItems = new HashMap<>();
        savedItems.put(slot1, player.getInventory().getItem(slot1));
        savedItems.put(slot2, player.getInventory().getItem(slot2));

        lockedSlots.put(uuid, savedItems);

        ItemStack placeholder = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = placeholder.getItemMeta();
        if (meta != null) {
            net.kyori.adventure.text.Component displayName = configManager.getItemMessage("void_mace", "messages.placeholder-name");
            meta.displayName(displayName != null ? displayName : net.kyori.adventure.text.Component.text("VOID", net.kyori.adventure.text.format.NamedTextColor.DARK_GRAY));
            placeholder.setItemMeta(meta);
        }

        player.getInventory().setItem(slot1, placeholder);
        player.getInventory().setItem(slot2, placeholder);
        net.kyori.adventure.text.Component lockMsg = configManager.getItemMessage("void_mace", "messages.hotbar-locked");
        if (lockMsg != null) player.sendMessage(lockMsg);
        player.playSound(player.getLocation(), Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 0.8f, 0.5f);

        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            restoreSlots(player);
        }, durationSeconds * 20L);
    }

    private void restoreSlots(Player player) {
        UUID uuid = player.getUniqueId();
        Map<Integer, ItemStack> saved = lockedSlots.remove(uuid);
        if (saved == null) return;

        for (Map.Entry<Integer, ItemStack> entry : saved.entrySet()) {
            ItemStack current = player.getInventory().getItem(entry.getKey());
            if (isPlaceholder(current)) {
                player.getInventory().setItem(entry.getKey(), entry.getValue());
            } else {
                // If placeholder is somehow gone, put the saved item in inventory
                if (entry.getValue() != null) {
                    player.getInventory().addItem(entry.getValue());
                }
            }
        }
        net.kyori.adventure.text.Component releaseMsg = configManager.getItemMessage("void_mace", "messages.hotbar-released");
        if (releaseMsg != null) player.sendMessage(releaseMsg);
    }

    private boolean isPlaceholder(ItemStack item) {
        if (item == null || item.getType() != Material.GRAY_STAINED_GLASS_PANE || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        net.kyori.adventure.text.Component nameComponent = meta.displayName();
        if (nameComponent == null) return false;
        String name = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(nameComponent);
        return name.contains("HƯ VÔ");
    }

    @EventHandler
    public void onPlaceholderClick(InventoryClickEvent event) {
        if (isPlaceholder(event.getCurrentItem()) || isPlaceholder(event.getCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlaceholderDrop(PlayerDropItemEvent event) {
        if (isPlaceholder(event.getItemDrop().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlaceholderSwap(PlayerSwapHandItemsEvent event) {
        if (isPlaceholder(event.getMainHandItem()) || isPlaceholder(event.getOffHandItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        restoreSlots(event.getPlayer());
        resurrectionEnds.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        restoreSlots(event.getEntity());
        resurrectionEnds.remove(event.getEntity().getUniqueId());
    }

    public void restoreAll() {
        for (UUID uuid : new HashSet<>(lockedSlots.keySet())) {
            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                restoreSlots(player);
            }
        }
        lockedSlots.clear();
        resurrectionEnds.clear();
    }
}
