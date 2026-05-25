package vn.nirussv.maceexclusive.mace;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.item.ExclusiveItemId;
import vn.nirussv.maceexclusive.item.PdcKeys;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class MaceManager {

    private final MaceExclusivePlugin plugin;
    private final MaceRepository repository;
    private final ConfigManager configManager;
    private final MaceFactory factory;

    public MaceManager(MaceExclusivePlugin plugin, MaceRepository repository, ConfigManager configManager, MaceFactory factory) {
        this.plugin = plugin;
        this.repository = repository;
        this.configManager = configManager;
        this.factory = factory;
    }

    public MaceType getMaceType(ItemStack item) {
        return factory.getMaceType(item);
    }

    public Optional<ExclusiveItemId> getExclusiveItemId(ItemStack item) {
        return factory.getExclusiveItemId(item);
    }

    public boolean isExclusiveItem(ItemStack item) {
        return getExclusiveItemId(item).isPresent();
    }

    public boolean isRegisteredMace(ItemStack item) {
        return getMaceType(item) != null;
    }

    public boolean isPowerMace(ItemStack item) {
        return getMaceType(item) == MaceType.POWER;
    }

    public boolean isChaosMace(ItemStack item) {
        return getMaceType(item) == MaceType.CHAOS;
    }

    public boolean canCraft(MaceType type) {
        return ExclusiveItemId.fromMaceType(type).map(this::canCreate).orElse(false);
    }

    public boolean canCreate(ExclusiveItemId id) {
        return !configManager.isSingletonWeapon(id) || !repository.isRegistered(id);
    }

    public boolean register(ItemStack item, UUID owner, MaceType type) {
        return ExclusiveItemId.fromMaceType(type).map(id -> register(item, owner, id)).orElse(false);
    }

    public boolean register(ItemStack item, UUID owner, ExclusiveItemId id) {
        if (id == null || owner == null) {
            return false;
        }
        if (item == null || item.getType() != id.material()) {
            return false;
        }
        if (configManager.isSingletonWeapon(id) && repository.isRegistered(id)) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }
        
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(new NamespacedKey(PdcKeys.ROOT_NAMESPACE, PdcKeys.ITEM_ID_KEY), PersistentDataType.STRING, id.id());
        pdc.set(new NamespacedKey(PdcKeys.ROOT_NAMESPACE, "owner"), PersistentDataType.STRING, owner.toString());

        id.legacyMaceType().ifPresent(type -> {
            pdc.set(new NamespacedKey(plugin, type.getPdcKey()), PersistentDataType.BYTE, (byte) 1);
            pdc.set(new NamespacedKey(plugin, type.getPdcKey() + "_owner"), PersistentDataType.STRING, owner.toString());
        });
        
        item.setItemMeta(meta);
        if (configManager.isSingletonWeapon(id)) {
            repository.setHolder(id, owner);
        }
        return true;
    }

    public boolean claimIfAllowed(ItemStack item, Player player) {
        Optional<ExclusiveItemId> matched = getExclusiveItemId(item);
        if (matched.isEmpty()) {
            return true;
        }
        ExclusiveItemId id = matched.get();
        if (!configManager.isSingletonWeapon(id)) {
            return true;
        }

        UUID holder = repository.getHolder(id);
        if (holder == null) {
            return register(item, player.getUniqueId(), id);
        }
        return holder.equals(player.getUniqueId());
    }

    public boolean isOwnedByAnother(ItemStack item, Player player) {
        Optional<ExclusiveItemId> matched = getExclusiveItemId(item);
        if (matched.isEmpty()) {
            return false;
        }
        ExclusiveItemId id = matched.get();
        if (!configManager.isSingletonWeapon(id)) {
            return false;
        }
        UUID holder = repository.getHolder(id);
        return holder != null && !holder.equals(player.getUniqueId());
    }

    public void onPlayerBecameHolder(Player player, Location location, MaceType type) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0, false, false, true));
        player.playSound(location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.5f);
        
        broadcastOwnership(player, location, type);
        showAcquisitionUI(player, type);
    }

    private void broadcastOwnership(Player player, Location location, MaceType type) {
        String playerDisplay = type == MaceType.CHAOS ? "&k" + player.getName() + "&r" : player.getName();
        
        Map<String, String> placeholders = Map.of(
            "player", playerDisplay,
            "x", String.valueOf(location.getBlockX()),
            "y", String.valueOf(location.getBlockY()),
            "z", String.valueOf(location.getBlockZ()),
            "world", location.getWorld().getName()
        );
        
        String messageKey = type == MaceType.CHAOS ? "chaos.crafted" : "mace.crafted";
        Component msg = configManager.getMessage(messageKey, placeholders);
        Bukkit.broadcast(msg);
    }
    
    private void showAcquisitionUI(Player player, MaceType type) {
        String titleKey = type == MaceType.CHAOS ? "chaos.title" : "mace.title";
        String subtitleKey = type == MaceType.CHAOS ? "chaos.subtitle" : "mace.subtitle";
        String warningKey = type == MaceType.CHAOS ? "chaos.warning" : "mace.warning";
        
        Component title = configManager.getMessage(titleKey);
        Component subtitle = configManager.getMessage(subtitleKey);
        
        Title titleObj = Title.title(
            title,
            subtitle,
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofMillis(500))
        );
        player.showTitle(titleObj);
        player.sendMessage(configManager.getPrefixedMessage(warningKey));
    }

    public boolean reset(MaceType type) {
        return ExclusiveItemId.fromMaceType(type).map(this::reset).orElse(false);
    }

    public boolean reset(ExclusiveItemId id) {
        if (!repository.isRegistered(id)) {
            return false;
        }
        repository.reset(id);
        return true;
    }

    public boolean resetAll() {
        repository.resetAll();
        return true;
    }
    
    public String getHolderName(MaceType type) {
        return ExclusiveItemId.fromMaceType(type).map(this::getHolderName).orElse(null);
    }

    public String getHolderName(ExclusiveItemId id) {
        UUID uuid = repository.getHolder(id);
        if (uuid == null) {
            return null;
        }
        Player p = Bukkit.getPlayer(uuid);
        return (p != null) ? p.getName() : Bukkit.getOfflinePlayer(uuid).getName();
    }

    @Deprecated
    public boolean canCraftMace() {
        return canCraft(MaceType.POWER);
    }

    @Deprecated
    public boolean registerMace(ItemStack item, UUID owner) {
        return register(item, owner, MaceType.POWER);
    }

    @Deprecated
    public void onPlayerBecameHolder(Player player, Location location) {
        onPlayerBecameHolder(player, location, MaceType.POWER);
    }

    @Deprecated
    public boolean reset() {
        return reset(MaceType.POWER);
    }

    @Deprecated
    public String getCurrentHolderName() {
        return getHolderName(MaceType.POWER);
    }
}
