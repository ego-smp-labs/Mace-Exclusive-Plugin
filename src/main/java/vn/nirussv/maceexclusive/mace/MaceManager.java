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

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MaceManager {

    private final MaceRepository repository;
    private final ConfigManager configManager;
    private final ItemMatcher itemMatcher;
    private final ItemRegistry itemRegistry;
    private final PdcKeys keys;

    public MaceManager(MaceRepository repository, ConfigManager configManager, ItemMatcher itemMatcher, ItemRegistry itemRegistry, PdcKeys keys) {
        this.repository = repository;
        this.configManager = configManager;
        this.itemMatcher = itemMatcher;
        this.itemRegistry = itemRegistry;
        this.keys = keys;
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

    public boolean claimIfAllowed(ItemStack item, Player player) {
        Optional<String> matched = getExclusiveItemKey(item);
        if (matched.isEmpty()) return true;
        String id = matched.get();
        if (!configManager.isSingletonItem(id)) return true;
        UUID holder = repository.getHolder(id);
        if (holder == null) return register(item, player.getUniqueId(), id);
        return holder.equals(player.getUniqueId());
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
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, true));
        player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.5f);
        broadcastOwnership(player, location, id);
        showAcquisitionUI(player, id);
    }

    private void broadcastOwnership(Player player, Location location, String id) {
        boolean chaos = "chaos_mace".equals(id);
        String playerDisplay = chaos ? "&k" + player.getName() + "&r" : player.getName();
        Map<String, String> placeholders = Map.of(
            "player", playerDisplay,
            "x", String.valueOf(location.getBlockX()),
            "y", String.valueOf(location.getBlockY()),
            "z", String.valueOf(location.getBlockZ()),
            "world", location.getWorld().getName()
        );
        Bukkit.broadcast(configManager.getMessage(chaos ? "chaos.crafted" : "mace.crafted", placeholders));
    }

    private void showAcquisitionUI(Player player, String id) {
        boolean chaos = "chaos_mace".equals(id);
        Component title = configManager.getMessage(chaos ? "chaos.title" : "mace.title");
        Component subtitle = configManager.getMessage(chaos ? "chaos.subtitle" : "mace.subtitle");
        player.showTitle(Title.title(title, subtitle, Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))));
        player.sendMessage(configManager.getPrefixedMessage(chaos ? "chaos.warning" : "mace.warning"));
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
