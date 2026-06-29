package vn.nirussv.maceexclusive.ability;

import net.kyori.adventure.text.Component;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.effect.SafeParticleSpawner;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class VoidEdgeAbility implements ActiveAbility, PassiveAbility, Listener {

    private static final String WEAPON_ID = "void_edge";
    private static final String ID = WEAPON_ID + ".void_blink";

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final CooldownService cooldownService;
    private final NamespacedKey maxHealthKey;
    private final Map<UUID, Long> backstabExpiries = new HashMap<>();
    private final Map<UUID, Long> lastMoveMillis = new HashMap<>();
    private final Random random = new Random();

    public VoidEdgeAbility(MaceExclusivePlugin plugin, ConfigManager configManager, CooldownService cooldownService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.cooldownService = cooldownService;
        this.maxHealthKey = new NamespacedKey(plugin, "void_edge_max_health");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String weaponId() {
        return WEAPON_ID;
    }

    @Override
    public boolean canActivate(AbilityContext context) {
        return context.player().isSneaking();
    }

    @Override
    public void activate(AbilityContext context) {
        Player player = context.player();
        if (!cooldownService.checkAndNotify(player, id())) return;

        Location destination = findBlinkDestination(player, context.target());
        player.teleport(destination);
        int windowTicks = configManager.getItemEffectInt(WEAPON_ID, "effects.active.backstab_window_ticks", 60);
        backstabExpiries.put(player.getUniqueId(), System.currentTimeMillis() + windowTicks * 50L);
        cooldownService.setCooldown(player, id(), configManager.getItemEffectInt(WEAPON_ID, "cooldowns.void_blink", 15) * 1000L);
        SafeParticleSpawner.spawn(player.getWorld(), Particle.PORTAL, player.getLocation().add(0.0D, 1.0D, 0.0D), 32, 0.4D, 0.6D, 0.4D, 0.08D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.35f);
        Component message = configManager.getItemMessage(WEAPON_ID, "messages.skill-void-blink");
        if (message != null) player.sendMessage(message);
    }

    @Override
    public void onAttack(AbilityContext context, EntityDamageByEntityEvent event) {
        removeInvisibility(context.player());
        if (!consumeBackstab(context.player())) return;
        double multiplier = configManager.getItemEffectDouble(WEAPON_ID, "effects.active.backstab_multiplier", 2.0D);
        event.setDamage(event.getDamage() * multiplier);
        Component message = configManager.getItemMessage(WEAPON_ID, "messages.skill-backstab");
        if (message != null) context.player().sendActionBar(message);
    }

    @Override
    public void onDamaged(AbilityContext context, EntityDamageByEntityEvent event) {
        applyMaxHealthCurse(context.player());
        double chance = configManager.getItemEffectDouble(WEAPON_ID, "effects.passive.phase_chance", 0.15D);
        if (random.nextDouble() > chance) return;
        event.setCancelled(true);
        phasePlayer(context.player());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player) || !hasVoidEdge(player)) return;
        applyMaxHealthCurse(player);
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            double multiplier = configManager.getItemEffectDouble(WEAPON_ID, "effects.curses.void_damage_multiplier", 1.50D);
            event.setDamage(event.getDamage() * multiplier);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onShieldUse(PlayerInteractEvent event) {
        if (!isRightClick(event.getAction()) || !hasVoidEdge(event.getPlayer())) return;
        if (event.getPlayer().getInventory().getItemInOffHand().getType() == Material.SHIELD) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!hasVoidEdge(player)) {
            cleanupMaxHealthCurse(player);
            removeInvisibility(player);
            return;
        }
        applyMaxHealthCurse(player);
        updateStationaryInvisibility(event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (hasVoidEdge(player)) applyMaxHealthCurse(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        if (isVoidEdge(newItem) || isVoidEdge(player.getInventory().getItemInOffHand())) {
            applyMaxHealthCurse(player);
            return;
        }
        cleanupMaxHealthCurse(player);
        removeInvisibility(player);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        cleanupPlayer(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        cleanupPlayer(event.getEntity());
    }

    private Location findBlinkDestination(Player player, LivingEntity target) {
        double range = configManager.getItemEffectDouble(WEAPON_ID, "effects.active.range", 8.0D);
        if (target != null) return safeLocation(behindTarget(player, target));
        Location eye = player.getEyeLocation();
        Vector direction = eye.getDirection().normalize();
        RayTraceResult trace = player.getWorld().rayTraceBlocks(eye, direction, range, FluidCollisionMode.NEVER, true);
        double distance = trace == null ? range : Math.max(1.0D, trace.getHitPosition().distance(eye.toVector()) - 0.7D);
        return safeLocation(player.getLocation().add(direction.multiply(distance)));
    }

    private Location behindTarget(Player player, LivingEntity target) {
        Vector reverse = player.getEyeLocation().getDirection().normalize().multiply(-1.5D);
        Location destination = target.getLocation().add(reverse);
        destination.setYaw(player.getLocation().getYaw());
        destination.setPitch(player.getLocation().getPitch());
        return destination;
    }

    private Location safeLocation(Location preferred) {
        Location base = preferred.getBlock().getLocation().add(0.5D, 0.0D, 0.5D);
        base.setYaw(preferred.getYaw());
        base.setPitch(preferred.getPitch());
        for (int y = 0; y <= 2; y++) {
            Location candidate = base.clone().add(0.0D, y, 0.0D);
            if (isPassable(candidate) && isPassable(candidate.clone().add(0.0D, 1.0D, 0.0D))) return candidate;
        }
        return preferred;
    }

    private boolean consumeBackstab(Player player) {
        Long expiry = backstabExpiries.remove(player.getUniqueId());
        return expiry != null && expiry >= System.currentTimeMillis();
    }

    private void phasePlayer(Player player) {
        double distance = configManager.getItemEffectDouble(WEAPON_ID, "effects.passive.phase_distance", 2.0D);
        double angle = random.nextDouble() * Math.PI * 2.0D;
        Location destination = safeLocation(player.getLocation().add(Math.cos(angle) * distance, 0.0D, Math.sin(angle) * distance));
        player.teleport(destination);
        SafeParticleSpawner.spawn(player.getWorld(), Particle.PORTAL, player.getLocation().add(0.0D, 1.0D, 0.0D), 28, 0.4D, 0.6D, 0.4D, 0.1D);
        player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.8f);
    }

    private void updateStationaryInvisibility(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (hasChangedBlock(event)) {
            lastMoveMillis.put(player.getUniqueId(), System.currentTimeMillis());
            removeInvisibility(player);
            return;
        }
        long stillTicks = configManager.getItemEffectInt(WEAPON_ID, "effects.passive.still_invisibility_ticks", 60);
        long lastMove = lastMoveMillis.getOrDefault(player.getUniqueId(), System.currentTimeMillis());
        if (System.currentTimeMillis() - lastMove >= stillTicks * 50L) applyInvisibility(player);
    }

    private boolean hasChangedBlock(PlayerMoveEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        return to != null && (from.getBlockX() != to.getBlockX() || from.getBlockY() != to.getBlockY() || from.getBlockZ() != to.getBlockZ());
    }

    private void applyInvisibility(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 80, 0, false, false, true));
    }

    private void removeInvisibility(Player player) {
        player.removePotionEffect(PotionEffectType.INVISIBILITY);
    }

    private void applyMaxHealthCurse(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null || hasModifier(attribute)) return;
        double penalty = configManager.getItemEffectDouble(WEAPON_ID, "effects.curses.max_health_penalty", 4.0D);
        attribute.addModifier(new AttributeModifier(maxHealthKey, -penalty, AttributeModifier.Operation.ADD_NUMBER));
        player.setHealth(Math.min(player.getHealth(), attribute.getValue()));
    }

    private void cleanupMaxHealthCurse(Player player) {
        AttributeInstance attribute = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (attribute == null) return;
        for (AttributeModifier modifier : List.copyOf(attribute.getModifiers())) {
            if (maxHealthKey.equals(modifier.getKey())) attribute.removeModifier(modifier);
        }
    }

    private boolean hasModifier(AttributeInstance attribute) {
        return attribute.getModifiers().stream().anyMatch(modifier -> maxHealthKey.equals(modifier.getKey()));
    }

    private void cleanupPlayer(Player player) {
        cleanupMaxHealthCurse(player);
        removeInvisibility(player);
        backstabExpiries.remove(player.getUniqueId());
        lastMoveMillis.remove(player.getUniqueId());
    }

    private boolean isRightClick(Action action) {
        return action == Action.RIGHT_CLICK_AIR || action == Action.RIGHT_CLICK_BLOCK;
    }

    private boolean hasVoidEdge(Player player) {
        return isVoidEdge(player.getInventory().getItemInMainHand()) || isVoidEdge(player.getInventory().getItemInOffHand());
    }

    private boolean isVoidEdge(ItemStack item) {
        return item != null && plugin.getMaceManager().getExclusiveItemKey(item).filter(WEAPON_ID::equals).isPresent();
    }

    private boolean isPassable(Location location) {
        return location.getBlock().isPassable();
    }
}
