package vn.nirussv.maceexclusive.ability;

import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class AvariceSwordAbility implements ActiveAbility, PassiveAbility, Listener {
    private static final String WEAPON_ID = "avarice_sword";
    private static final String ID = WEAPON_ID + ".bounty_hunt";
    private static final Set<Material> PRIORITY = Set.of(Material.DIAMOND, Material.EMERALD, Material.GOLD_INGOT, Material.IRON_INGOT, Material.COPPER_INGOT, Material.COAL, Material.ARROW, Material.COOKED_BEEF, Material.COOKED_PORKCHOP, Material.BREAD, Material.GOLDEN_CARROT);

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;
    private final CooldownService cooldownService;
    private final Random random = new Random();
    private final Map<UUID, Long> stealCooldowns = new HashMap<>();
    private final Map<UUID, Integer> rage = new HashMap<>();
    private final Set<UUID> minions = new HashSet<>();

    public AvariceSwordAbility(MaceExclusivePlugin plugin, ConfigManager configManager, ItemMatcher itemMatcher, CooldownService cooldownService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemMatcher = itemMatcher;
        this.cooldownService = cooldownService;
        startCurseTask();
    }

    public String id() { return ID; }
    public String weaponId() { return WEAPON_ID; }
    public boolean canActivate(AbilityContext context) { return context.target() instanceof LivingEntity && rage.getOrDefault(context.player().getUniqueId(), 0) >= minRage(); }

    public void activate(AbilityContext context) {
        Player player = context.player();
        if (!(context.target() instanceof LivingEntity target)) return;
        rage.put(player.getUniqueId(), Math.max(0, rage.getOrDefault(player.getUniqueId(), 0) - minRage()));
        int count = minionsMin() + random.nextInt(Math.max(1, minionsMax() - minionsMin() + 1));
        for (int i = 0; i < count; i++) summonMinion(player, target, i % 2 == 0 ? EntityType.PILLAGER : EntityType.VINDICATOR);
        player.sendMessage(configManager.getMessage("avarice.bounty", Map.of("target", target.getName())));
        cooldownService.setCooldown(player, id(), configManager.getItemEffectInt(WEAPON_ID, "cooldowns.bounty_hunt", 120) * 1000L);
    }

    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        Player attacker = context.player();
        if (!isCritical(attacker) || random.nextDouble() >= stealChance()) return;
        long now = System.currentTimeMillis();
        long next = stealCooldowns.getOrDefault(attacker.getUniqueId(), 0L);
        if (now < next) return;
        ItemStack stolen = stealOne(context.target());
        if (stolen == null) return;
        stealCooldowns.put(attacker.getUniqueId(), now + stealCooldownSeconds() * 1000L);
        giveOrDrop(attacker, stolen);
        int newRage = Math.min(99, rage.getOrDefault(attacker.getUniqueId(), 0) + 1);
        rage.put(attacker.getUniqueId(), newRage);
        attacker.sendMessage(configManager.getMessage("avarice.stolen", Map.of("item", stolen.getType().name(), "rage", String.valueOf(newRage))));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onRegen(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player) || !holding(player)) return;
        if (player.hasPotionEffect(PotionEffectType.WITHER)) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTarget(EntityTargetEvent event) {
        if (!minions.contains(event.getEntity().getUniqueId()) || event.getTarget() instanceof Player) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) { rage.remove(event.getPlayer().getUniqueId()); }

    private void startCurseTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : plugin.getServer().getOnlinePlayers()) if (holding(player)) payTribute(player);
        }, 20L * 30L, 20L * configManager.getItemEffectInt(WEAPON_ID, "effects.curses.tribute_interval_seconds", 30));
    }

    private void payTribute(Player player) {
        if (consume(player, Material.EMERALD) || consume(player, Material.GOLD_INGOT)) {
            player.sendActionBar(configManager.getMessage("avarice.curse-paid"));
            return;
        }
        player.damage(configManager.getItemEffectDouble(WEAPON_ID, "effects.curses.starvation_damage", 4.0D));
        player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 20 * 5, 0, false, false, true));
        player.sendActionBar(configManager.getMessage("avarice.curse-starved"));
    }

    private ItemStack stealOne(LivingEntity target) {
        if (target instanceof Player player) return stealFromPlayer(player);
        ItemStack hand = target.getEquipment() == null ? null : target.getEquipment().getItemInMainHand();
        if (!eligible(hand)) return null;
        ItemStack one = hand.clone(); one.setAmount(1);
        hand.setAmount(hand.getAmount() - 1);
        target.getEquipment().setItemInMainHand(hand.getAmount() > 0 ? hand : null);
        return one;
    }

    private ItemStack stealFromPlayer(Player target) {
        List<Integer> slots = new ArrayList<>();
        for (int slot = 0; slot < target.getInventory().getSize(); slot++) if (eligible(target.getInventory().getItem(slot))) slots.add(slot);
        if (slots.isEmpty()) return null;
        slots.sort(Comparator.comparingInt(slot -> PRIORITY.contains(target.getInventory().getItem(slot).getType()) ? 0 : 1));
        int slot = slots.get(PRIORITY.contains(target.getInventory().getItem(slots.get(0)).getType()) ? 0 : random.nextInt(slots.size()));
        ItemStack stack = target.getInventory().getItem(slot);
        ItemStack one = stack.clone(); one.setAmount(1);
        stack.setAmount(stack.getAmount() - 1);
        target.getInventory().setItem(slot, stack.getAmount() > 0 ? stack : null);
        return one;
    }

    private boolean eligible(ItemStack item) { return item != null && !item.getType().isAir() && item.getAmount() > 0 && itemMatcher.match(item).isEmpty() && itemMatcher.matchCore(item).isEmpty(); }
    private boolean holding(Player player) { return itemMatcher.is(player.getInventory().getItemInMainHand(), WEAPON_ID) || itemMatcher.is(player.getInventory().getItemInOffHand(), WEAPON_ID); }
    private boolean isCritical(Player player) { return player.getFallDistance() > 0.0F && !player.isOnGround() && !player.isInsideVehicle(); }
    private double stealChance() { return configManager.getItemEffectDouble(WEAPON_ID, "effects.passive.steal_chance", 0.10D); }
    private int stealCooldownSeconds() { return configManager.getItemEffectInt(WEAPON_ID, "effects.passive.steal_cooldown_seconds", 30); }
    private int minRage() { return configManager.getItemEffectInt(WEAPON_ID, "effects.active.min_rage", 5); }
    private int minionsMin() { return configManager.getItemEffectInt(WEAPON_ID, "effects.active.minions_min", 3); }
    private int minionsMax() { return configManager.getItemEffectInt(WEAPON_ID, "effects.active.minions_max", 5); }

    private void summonMinion(Player owner, LivingEntity target, EntityType type) {
        if (!(owner.getWorld().spawnEntity(target.getLocation(), type) instanceof Mob mob)) return;
        mob.customName(Component.text("Bounty Ghost " + configManager.getItemEffectInt(WEAPON_ID, "effects.active.duration_seconds", 120) + "s"));
        mob.setCustomNameVisible(true);
        mob.setTarget(target);
        minions.add(mob.getUniqueId());
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> { minions.remove(mob.getUniqueId()); if (mob.isValid()) mob.remove(); }, 20L * configManager.getItemEffectInt(WEAPON_ID, "effects.active.duration_seconds", 120));
    }

    private boolean consume(Player player, Material material) {
        for (int slot = 0; slot < player.getInventory().getSize(); slot++) {
            ItemStack item = player.getInventory().getItem(slot);
            if (item == null || item.getType() != material) continue;
            item.setAmount(item.getAmount() - 1);
            player.getInventory().setItem(slot, item.getAmount() > 0 ? item : null);
            return true;
        }
        return false;
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) player.getWorld().dropItemNaturally(player.getLocation(), leftover);
    }
}
