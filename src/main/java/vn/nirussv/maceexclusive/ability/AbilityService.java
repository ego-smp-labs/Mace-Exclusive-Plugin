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
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.effect.FreezeService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class AbilityService {

    private final MaceExclusivePlugin plugin;
    private final ItemMatcher itemMatcher;
    private final CooldownService cooldownService;
    private final Map<String, List<ActiveAbility>> activeAbilities = new HashMap<>();
    private final Map<String, List<PassiveAbility>> passiveAbilities = new HashMap<>();

    public AbilityService(MaceExclusivePlugin plugin, ConfigManager configManager, ItemMatcher itemMatcher, FreezeService freezeService) {
        this.plugin = plugin;
        this.itemMatcher = itemMatcher;
        this.cooldownService = new CooldownService(configManager);
        registerDefaults(configManager, freezeService);
    }

    public CooldownService cooldownService() { return cooldownService; }
    public void registerActive(ActiveAbility ability) { activeAbilities.computeIfAbsent(ability.weaponId(), ignored -> new ArrayList<>()).add(ability); }
    public void registerPassive(PassiveAbility ability) { passiveAbilities.computeIfAbsent(ability.weaponId(), ignored -> new ArrayList<>()).add(ability); }

    public void handleInteract(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.LEFT_CLICK_AIR && action != Action.LEFT_CLICK_BLOCK) return;
        Player player = event.getPlayer();
        if (!player.isSneaking()) return;
        ItemStack weapon = event.getItem();
        Optional<String> weaponId = itemMatcher.match(weapon);
        if (weaponId.isEmpty()) return;
        LivingEntity target = action == Action.LEFT_CLICK_AIR ? findLookTarget(player, 8.0D) : null;
        if (target == null) {
            applyMissCurse(player, weaponId.get());
            return;
        }
        AbilityContext context = new AbilityContext(player, player.getLocation(), weapon, weaponId.get(), target, null);
        for (ActiveAbility ability : activeAbilities.getOrDefault(weaponId.get(), List.of())) {
            if (!ability.canActivate(context)) continue;
            if (!cooldownService.checkAndNotify(player, ability.id())) return;
            ability.activate(context);
            event.setCancelled(true);
            return;
        }
    }

    private void applyMissCurse(Player player, String weaponId) {
        if ("power_mace".equals(weaponId)) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 20 * 60, 0, false, true, true));
            return;
        }
        if ("vampiric_mace".equals(weaponId)) {
            player.damage(6.0D);
        }
    }

    public void handleAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof LivingEntity target)) return;
        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        Optional<String> weaponId = itemMatcher.match(weapon);
        if (weaponId.isEmpty()) return;

        if (attacker.isSneaking()) {
            AbilityContext context = new AbilityContext(attacker, attacker.getLocation(), weapon, weaponId.get(), target, null);
            for (ActiveAbility ability : activeAbilities.getOrDefault(weaponId.get(), List.of())) {
                if (ability.canActivate(context)) {
                    if (!cooldownService.checkAndNotify(attacker, ability.id())) return;
                    ability.activate(context);
                    event.setCancelled(true);
                    return;
                }
            }
        }

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

    private void registerDefaults(ConfigManager configManager, FreezeService freezeService) {
        // Power Mace
        PowerGroundPulseAbility powerAbility = new PowerGroundPulseAbility(plugin, configManager, cooldownService);
        registerPassive(new PowerStoredMomentumAbility(configManager, powerAbility));
        registerActive(powerAbility);

        // Chaos Mace
        ChaosMaceAbility chaosMaceAbility = new ChaosMaceAbility(plugin, configManager, cooldownService);
        registerActive(chaosMaceAbility);
        registerPassive(chaosMaceAbility);

        // Void Mace
        VoidMaceAbility voidMaceAbility = new VoidMaceAbility(plugin, configManager, cooldownService, freezeService);
        registerActive(voidMaceAbility);
        registerPassive(voidMaceAbility);
        plugin.getServer().getPluginManager().registerEvents(voidMaceAbility, plugin);

        // Vampiric Mace
        VampiricMaceAbility vampiricMaceAbility = new VampiricMaceAbility(plugin, configManager, cooldownService);
        registerActive(vampiricMaceAbility);
        registerPassive(vampiricMaceAbility);
        plugin.getServer().getPluginManager().registerEvents(vampiricMaceAbility, plugin);

        // Gravity Mace
        GravityMaceAbility gravityMaceAbility = new GravityMaceAbility(plugin, configManager, cooldownService);
        registerActive(gravityMaceAbility);
        registerPassive(gravityMaceAbility);
        plugin.getServer().getPluginManager().registerEvents(gravityMaceAbility, plugin);

        // Sonic Mace
        SonicWardenMaceAbility sonicMaceAbility = new SonicWardenMaceAbility(plugin, configManager, cooldownService);
        registerActive(sonicMaceAbility);
        registerPassive(sonicMaceAbility);
        plugin.getServer().getPluginManager().registerEvents(sonicMaceAbility, plugin);

        // Soulfire Mace
        SoulfirePyreMaceAbility soulfireMaceAbility = new SoulfirePyreMaceAbility(plugin, configManager, cooldownService);
        registerActive(soulfireMaceAbility);
        registerPassive(soulfireMaceAbility);
        plugin.getServer().getPluginManager().registerEvents(soulfireMaceAbility, plugin);
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
