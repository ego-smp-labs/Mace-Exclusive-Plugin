package vn.nirussv.maceexclusive.ability;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.SafeParticleSpawner;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class SoulSeverSpearAbility implements PassiveAbility, Listener {
    private static final String WEAPON_ID = "soul_sever_spear";
    private static final String ID = WEAPON_ID + ".soul_sever";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;
    private final Random random = new Random();
    private final Map<UUID, Mark> marks = new HashMap<>();

    public SoulSeverSpearAbility(MaceExclusivePlugin plugin, ConfigManager configManager, ItemMatcher itemMatcher) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemMatcher = itemMatcher;
        startParticleTask();
    }

    public String id() { return ID; }
    public String weaponId() { return WEAPON_ID; }

    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        if (context.target() == null || random.nextDouble() >= markChance()) return;
        marks.put(context.target().getUniqueId(), new Mark(context.player().getUniqueId(), System.currentTimeMillis() + markSeconds() * 1000L));
        context.player().sendMessage(configManager.getMessage("soul-sever.marked", Map.of("target", context.target().getName())));
    }

    public void onDamaged(AbilityContext context, EntityDamageByEntityEvent event) {
        Mark mark = activeMark(event.getDamager().getUniqueId());
        if (mark == null || random.nextDouble() >= linkChance()) return;
        if (event.getDamager() instanceof LivingEntity markedTarget) heal(markedTarget, event.getDamage() * 0.5D);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMarkedDamage(EntityDamageByEntityEvent event) {
        Mark mark = activeMark(event.getEntity().getUniqueId());
        if (mark == null || random.nextDouble() >= linkChance()) return;
        Player holder = plugin.getServer().getPlayer(mark.holder());
        if (holder != null && holder.isOnline()) heal(holder, event.getDamage() * 0.5D);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onHeal(EntityRegainHealthEvent event) {
        Mark mark = activeMark(event.getEntity().getUniqueId());
        if (mark == null || !(event.getEntity() instanceof LivingEntity living)) return;
        event.setCancelled(true);
        living.damage(Math.max(1.0D, event.getAmount()), plugin.getServer().getPlayer(mark.holder()));
        if (living instanceof Player player) player.sendActionBar(configManager.getMessage("soul-sever.heal-reversed"));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTotem(EntityResurrectEvent event) {
        Mark mark = activeMark(event.getEntity().getUniqueId());
        if (mark == null) return;
        event.setCancelled(true);
        consumeOneTotem(event.getEntity() instanceof Player player ? player : null);
        event.getEntity().getWorld().createExplosion(event.getEntity().getLocation(), 1.5F, false, false);
        event.getEntity().damage(8.0D, plugin.getServer().getPlayer(mark.holder()));
        if (event.getEntity() instanceof Player player) player.sendMessage(configManager.getMessage("soul-sever.totem"));
        marks.remove(event.getEntity().getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null || !holding(event.getPlayer())) return;
        if (event.getTo().getY() > event.getFrom().getY() + 0.03D && !event.getPlayer().isFlying()) {
            event.setCancelled(true);
            event.getPlayer().setVelocity(event.getPlayer().getVelocity().setY(-0.2D));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        marks.entrySet().removeIf(entry -> entry.getKey().equals(event.getPlayer().getUniqueId()) || entry.getValue().holder().equals(event.getPlayer().getUniqueId()));
    }

    private void startParticleTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            marks.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
            for (UUID uuid : marks.keySet()) {
                if (plugin.getServer().getEntity(uuid) instanceof LivingEntity living) {
                    SafeParticleSpawner.spawn(living.getWorld(), Particle.SOUL, living.getLocation().add(0, 0.1, 0), 5, 0.35, 0.05, 0.35, 0.01);
                }
            }
        }, 20L, 20L);
    }

    private Mark activeMark(UUID uuid) {
        Mark mark = marks.get(uuid);
        if (mark == null || mark.expiresAt() <= System.currentTimeMillis()) {
            marks.remove(uuid);
            return null;
        }
        return mark;
    }

    private boolean holding(Player player) { return itemMatcher.is(player.getInventory().getItemInMainHand(), WEAPON_ID) || itemMatcher.is(player.getInventory().getItemInOffHand(), WEAPON_ID); }
    private double markChance() { return configManager.getItemEffectDouble(WEAPON_ID, "effects.passive.mark_chance", 0.20D); }
    private int markSeconds() { return configManager.getItemEffectInt(WEAPON_ID, "effects.passive.mark_seconds", 10); }
    private double linkChance() { return configManager.getItemEffectDouble(WEAPON_ID, "effects.curses.linked_heal_chance", 0.20D); }

    private void heal(LivingEntity entity, double amount) { entity.setHealth(Math.min(entity.getMaxHealth(), entity.getHealth() + Math.max(0.0D, amount))); }

    private void consumeOneTotem(Player player) {
        if (player == null) return;
        ItemStack main = player.getInventory().getItemInMainHand();
        if (main != null && main.getType() == Material.TOTEM_OF_UNDYING) { decrement(player, true, main); return; }
        ItemStack off = player.getInventory().getItemInOffHand();
        if (off != null && off.getType() == Material.TOTEM_OF_UNDYING) decrement(player, false, off);
    }

    private void decrement(Player player, boolean mainHand, ItemStack item) {
        item.setAmount(item.getAmount() - 1);
        if (mainHand) player.getInventory().setItemInMainHand(item.getAmount() > 0 ? item : null);
        else player.getInventory().setItemInOffHand(item.getAmount() > 0 ? item : null);
    }

    private record Mark(UUID holder, long expiresAt) { }
}
