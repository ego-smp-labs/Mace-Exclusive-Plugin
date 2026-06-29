package vn.nirussv.maceexclusive.ability;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import vn.nirussv.maceexclusive.carry.CarryViolationService;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.config.ItemConfig;
import vn.nirussv.maceexclusive.item.ItemMatcher;

import java.util.Map;
import java.util.Optional;

public final class AbilityHudService {

    private final Plugin plugin;
    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;
    private final CooldownService cooldownService;
    private final CarryViolationService carryViolationService;
    private final Map<String, String> abilityIds = Map.ofEntries(
        Map.entry("power_mace", "power_mace.ground_pulse"),
        Map.entry("void_mace", "void_mace.resurrection"),
        Map.entry("chaos_mace", "chaos_mace.rage"),
        Map.entry("gravity_mace", "gravity_mace.gravity_well"),
        Map.entry("soulfire_mace", "soulfire_mace.fire_storm"),
        Map.entry("vampiric_mace", "vampiric_mace.siphon"),
        Map.entry("sonic_spear", "sonic_spear.sonic_boom"),
        Map.entry("chronos_anchor_spear", "chronos_anchor_spear.time_pin"),
        Map.entry("avarice_sword", "avarice_sword.bounty_hunt"),
        Map.entry("void_edge", "void_edge.void_blink")
    );
    private BukkitTask task;

    public AbilityHudService(Plugin plugin, ConfigManager configManager, ItemMatcher itemMatcher,
                             CooldownService cooldownService, CarryViolationService carryViolationService) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.itemMatcher = itemMatcher;
        this.cooldownService = cooldownService;
        this.carryViolationService = carryViolationService;
    }

    public void start() {
        if (!configManager.isAbilityHudEnabled() || task != null) return;
        long interval = configManager.getAbilityHudIntervalTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, interval, interval);
    }

    public void shutdown() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private void tick() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (!player.isOnline() || player.isDead() || carryViolationService.isCountdownActive(player)) continue;
            heldWeaponId(player).ifPresent(weaponId -> sendHud(player, weaponId));
        }
    }

    private Optional<String> heldWeaponId(Player player) {
        Optional<String> mainHand = weaponId(player.getInventory().getItemInMainHand());
        if (mainHand.isPresent()) return mainHand;
        return weaponId(player.getInventory().getItemInOffHand());
    }

    private Optional<String> weaponId(ItemStack item) {
        return itemMatcher.match(item).filter(configManager::isWeaponItem);
    }

    private void sendHud(Player player, String weaponId) {
        String abilityId = abilityIds.get(weaponId);
        if (abilityId == null) return;
        long remainingMillis = cooldownService.remainingMillis(player.getUniqueId(), abilityId);
        String weaponName = displayName(weaponId);
        String abilityName = abilityId.substring(abilityId.lastIndexOf('.') + 1).replace('_', ' ');
        if (remainingMillis <= 0L) {
            player.sendActionBar(configManager.getMessage("ability.hud-ready", Map.of(
                "weapon", weaponName,
                "ability", abilityName
            )));
            return;
        }
        double seconds = Math.ceil(remainingMillis / 100.0D) / 10.0D;
        player.sendActionBar(configManager.getMessage("ability.hud-cooldown", Map.of(
            "weapon", weaponName,
            "ability", abilityName,
            "seconds", String.valueOf(seconds)
        )));
    }

    private String displayName(String weaponId) {
        ItemConfig itemConfig = configManager.getItemConfig(weaponId);
        return itemConfig == null ? weaponId : itemConfig.name();
    }
}
