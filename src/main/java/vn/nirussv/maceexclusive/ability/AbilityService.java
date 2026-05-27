package vn.nirussv.maceexclusive.ability;

import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.RayTraceResult;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AbilityService {

    private final ItemMatcher itemMatcher;
    private final CooldownService cooldownService;
    private final Map<String, List<ActiveAbility>> activeAbilities = new HashMap<>();
    private final Map<String, List<PassiveAbility>> passiveAbilities = new HashMap<>();

    public AbilityService(ConfigManager configManager, ItemMatcher itemMatcher) {
        this.itemMatcher = itemMatcher;
        this.cooldownService = new CooldownService(configManager);
        registerDefaults(configManager);
    }

    public CooldownService cooldownService() { return cooldownService; }
    public void registerActive(ActiveAbility ability) { activeAbilities.computeIfAbsent(ability.weaponId(), ignored -> new ArrayList<>()).add(ability); }
    public void registerPassive(PassiveAbility ability) { passiveAbilities.computeIfAbsent(ability.weaponId(), ignored -> new ArrayList<>()).add(ability); }

    public void handleInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        ItemStack weapon = event.getItem();
        Optional<String> weaponId = itemMatcher.match(weapon);
        if (weaponId.isEmpty()) return;
        LivingEntity target = findLookTarget(player, 8.0D);
        AbilityContext context = new AbilityContext(player, player.getLocation(), weapon, weaponId.get(), target, null);
        for (ActiveAbility ability : activeAbilities.getOrDefault(weaponId.get(), List.of())) {
            if (!ability.canActivate(context)) continue;
            ability.activate(context);
            event.setCancelled(true);
            return;
        }
    }

    public void handleAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        Optional<String> weaponId = itemMatcher.match(weapon);
        if (weaponId.isEmpty()) return;
        AbilityContext context = new AbilityContext(attacker, attacker.getLocation(), weapon, weaponId.get(), target, target);
        for (PassiveAbility ability : passiveAbilities.getOrDefault(weaponId.get(), List.of())) ability.onAttack(context, event);
    }

    public void handleDamaged(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack weapon = player.getInventory().getItemInMainHand();
        Optional<String> weaponId = itemMatcher.match(weapon);
        if (weaponId.isEmpty()) return;
        AbilityContext context = new AbilityContext(player, player.getLocation(), weapon, weaponId.get(), null, event.getDamager());
        for (PassiveAbility ability : passiveAbilities.getOrDefault(weaponId.get(), List.of())) ability.onDamaged(context, event);
    }

    public void handleDeath(EntityDeathEvent event) {
        AbilityContext context = new AbilityContext(null, event.getEntity().getLocation(), null, null, event.getEntity(), null);
        for (List<PassiveAbility> abilities : passiveAbilities.values()) for (PassiveAbility ability : abilities) ability.onDeath(context, event);
    }

    private void registerDefaults(ConfigManager configManager) {
        registerPassive(new PowerStoredMomentumAbility(configManager));
        registerActive(new PowerGroundPulseAbility(configManager, cooldownService));
        registerPassive(new ChaosFracturedStepAbility(configManager, cooldownService));
        ChaosRiftReversalAbility riftReversal = new ChaosRiftReversalAbility(configManager, cooldownService);
        registerActive(riftReversal);
        registerPassive(riftReversal);
    }

    private LivingEntity findLookTarget(Player player, double range) {
        Location eye = player.getEyeLocation();
        RayTraceResult result = player.getWorld().rayTraceEntities(eye, eye.getDirection(), range, 0.6D,
            entity -> entity instanceof LivingEntity && !entity.getUniqueId().equals(player.getUniqueId()));
        if (result != null && result.getHitEntity() instanceof LivingEntity livingEntity) return livingEntity;
        RayTraceResult blockTrace = player.getWorld().rayTraceBlocks(eye, eye.getDirection(), range, FluidCollisionMode.NEVER, true);
        return blockTrace == null ? null : null;
    }
}
