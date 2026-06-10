package vn.nirussv.maceexclusive.mace;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.ItemMatcher;
import vn.nirussv.maceexclusive.item.ItemRegistry;
import vn.nirussv.maceexclusive.item.PdcKeys;
import vn.nirussv.maceexclusive.discord.DiscordWebhookService;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MaceManager {

    public enum ClaimResult { NOT_EXCLUSIVE, NEWLY_CLAIMED, ALREADY_OWNER, DENIED }
    public enum AcquisitionReason { CRAFTED, PICKUP, RECEIVED }

    private final MaceRepository repository;
    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;
    private final ItemRegistry itemRegistry;
    private final PdcKeys keys;
    private final MaceTrackerService trackerService;
    private final DiscordWebhookService webhookService;

    public MaceManager(MaceRepository repository, ConfigManager configManager, ItemMatcher itemMatcher, ItemRegistry itemRegistry, PdcKeys keys, MaceTrackerService trackerService, DiscordWebhookService webhookService) {
        this.repository = repository;
        this.configManager = configManager;
        this.itemMatcher = itemMatcher;
        this.itemRegistry = itemRegistry;
        this.keys = keys;
        this.trackerService = trackerService;
        this.webhookService = webhookService;
    }

    public Optional<String> getExclusiveItemKey(ItemStack item) {
        return itemMatcher.match(item);
    }

    public boolean isExclusiveItem(ItemStack item) {
        return getExclusiveItemKey(item).isPresent() || itemMatcher.matchCore(item).isPresent();
    }

    public boolean isRegisteredMace(ItemStack item) { return getExclusiveItemKey(item).isPresent(); }
    public boolean isPowerMace(ItemStack item) { return itemMatcher.is(item, "power_mace"); }
    public boolean isChaosMace(ItemStack item) { return itemMatcher.is(item, "chaos_mace"); }

    public boolean canCraft(String id) { return canCreate(id); }

    public boolean canCreate(String id) {
        return id != null && (!configManager.isSingletonItem(id) || !repository.isRegistered(id));
    }

    public boolean register(ItemStack item, UUID owner, String id) {
        if (id == null || owner == null || item == null) return false;
        if (itemRegistry.find(id).isEmpty()) return false;
        if (configManager.isSingletonItem(id) && repository.isRegistered(id)) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        meta.getPersistentDataContainer().set(keys.itemId(), PersistentDataType.STRING, id.toLowerCase());
        meta.getPersistentDataContainer().set(keys.owner(), PersistentDataType.STRING, owner.toString());
        item.setItemMeta(meta);
        if (configManager.isSingletonItem(id)) repository.setHolder(id, owner);
        return true;
    }

    public ClaimResult claimIfAllowed(ItemStack item, Player player) {
        Optional<String> matched = getExclusiveItemKey(item);
        if (matched.isEmpty()) return ClaimResult.NOT_EXCLUSIVE;
        String id = matched.get();
        if (!configManager.isSingletonItem(id)) return ClaimResult.ALREADY_OWNER;
        UUID holder = repository.getHolder(id);
        if (holder == null || !holder.equals(player.getUniqueId())) {
            ItemMeta meta = item.getItemMeta();
            if (meta == null) return ClaimResult.DENIED;
            meta.getPersistentDataContainer().set(keys.itemId(), PersistentDataType.STRING, id.toLowerCase());
            meta.getPersistentDataContainer().set(keys.owner(), PersistentDataType.STRING, player.getUniqueId().toString());
            item.setItemMeta(meta);
            repository.setHolder(id, player.getUniqueId());
            return ClaimResult.NEWLY_CLAIMED;
        }
        return ClaimResult.ALREADY_OWNER;
    }

    public boolean canPickup(ItemStack item, Player player) {
        return true;
    }

    public boolean isOwnedByAnother(ItemStack item, Player player) {
        Optional<String> matched = getExclusiveItemKey(item);
        if (matched.isEmpty()) return false;
        String id = matched.get();
        if (!configManager.isSingletonItem(id)) return false;
        UUID holder = repository.getHolder(id);
        return holder != null && !holder.equals(player.getUniqueId());
    }

    public void onPlayerBecameHolder(Player player, Location location, String id) {
        notifyAcquisition(player, location, id, AcquisitionReason.CRAFTED);
    }

    public void notifyAcquisition(Player player, Location location, String id, AcquisitionReason reason) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, true));
        player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.5f);
        if (reason == AcquisitionReason.RECEIVED) {
            player.sendMessage(configManager.getPrefixedMessage(messagePrefix(id) + "received", Map.of("name", displayName(id), "player", player.getName())));
        } else {
            broadcastOwnership(player, location, id, reason);
            if (configManager.isSingletonItem(id)) {
                if (reason == AcquisitionReason.CRAFTED) {
                    trackerService.startTracking(id);
                }
                webhookService.sendMaceNotification(player.getName(), displayName(id), reason.name(), location, id);
            }
        }
        showAcquisitionUI(player, id);
    }

    private void broadcastOwnership(Player player, Location location, String id, AcquisitionReason reason) {
        boolean chaos = "chaos_mace".equals(id);
        String playerDisplay = chaos ? "&k" + player.getName() + "&r" : player.getName();
        String messageAction = switch (reason) {
            case PICKUP -> "pickup";
            case RECEIVED -> "received";
            case CRAFTED -> "crafted";
        };
        Map<String, String> placeholders = Map.of(
            "player", playerDisplay,
            "name", displayName(id),
            "x", String.valueOf(location.getBlockX()),
            "y", String.valueOf(location.getBlockY()),
            "z", String.valueOf(location.getBlockZ()),
            "world", location.getWorld().getName()
        );
        Bukkit.broadcast(configManager.getMessage(messagePrefix(id) + messageAction, placeholders));
    }

    private void showAcquisitionUI(Player player, String id) {
        boolean chaos = "chaos_mace".equals(id);
        Map<String, String> placeholders = Map.of("name", displayName(id), "player", player.getName());
        Component title = configManager.getMessage(chaos ? "chaos.title" : "mace.title", placeholders);
        Component subtitle = configManager.getMessage(chaos ? "chaos.subtitle" : "mace.subtitle", placeholders);
        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        player.sendMessage(configManager.getPrefixedMessage(chaos ? "chaos.warning" : "mace.warning", placeholders));
    }

    public String displayName(String id) {
        var cfg = configManager.getItemConfig(id);
        return cfg == null ? id : cfg.name();
    }

    private String messagePrefix(String id) {
        return "chaos_mace".equals(id) ? "chaos." : "mace.";
    }

    public boolean reset(String id) {
        if (!repository.isRegistered(id)) return false;
        repository.reset(id);
        return true;
    }

    public boolean resetAll() { repository.resetAll(); return true; }

    public String getHolderName(String id) {
        UUID uuid = repository.getHolder(id);
        if (uuid == null) return null;
        Player player = Bukkit.getPlayer(uuid);
        return player != null ? player.getName() : Bukkit.getOfflinePlayer(uuid).getName();
    }
}
