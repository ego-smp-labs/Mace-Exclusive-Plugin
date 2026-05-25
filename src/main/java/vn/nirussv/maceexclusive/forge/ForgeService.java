package vn.nirussv.maceexclusive.forge;

import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.mace.MaceFactory;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.mace.MaceType;
import vn.nirussv.maceexclusive.persistence.ForgeSessionStore;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ForgeService {

    private final MaceExclusivePlugin plugin;
    private final ConfigManager configManager;
    private final MaceFactory maceFactory;
    private final MaceManager maceManager;
    private final ForgeSessionStore store;
    private final Map<BlockKey, AwakeningSession> sessions = new HashMap<>();

    public ForgeService(
        MaceExclusivePlugin plugin,
        ConfigManager configManager,
        MaceFactory maceFactory,
        MaceManager maceManager,
        ForgeSessionStore store
    ) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.maceFactory = maceFactory;
        this.maceManager = maceManager;
        this.store = store;
    }

    public void start() {
        for (ForgeSessionStore.StoredForgeSession stored : store.load()) {
            if (stored.location().getBlock().getType() != configManager.getForgeBlockMaterial()) {
                continue;
            }
            restore(stored);
        }
        save();
    }

    public void shutdown() {
        save();
        for (AwakeningSession session : sessions.values()) {
            if (session.task() != null) {
                session.task().cancel();
            }
            removeHologram(session);
        }
        sessions.clear();
    }

    public boolean isForgeBlock(Block block) {
        return block != null && sessions.containsKey(BlockKey.of(block));
    }

    public boolean isValidForgeBase(Block block) {
        return block != null && block.getType() == configManager.getForgeBlockMaterial();
    }

    public boolean tryStart(Player player, Block block, ItemStack unawakenedItem) {
        if (!isValidForgeBase(block)) {
            return false;
        }

        Optional<MaceType> resultType = maceFactory.getAwakeningResult(unawakenedItem);
        if (resultType.isEmpty()) {
            return false;
        }

        MaceType type = resultType.get();
        if (!maceManager.canCraft(type) || sessions.containsKey(BlockKey.of(block))) {
            return false;
        }

        long now = System.currentTimeMillis();
        long endsAt = now + configManager.getForgeDurationSeconds() * 1000L;
        AwakeningSession session = new AwakeningSession(
            BlockKey.of(block),
            block.getLocation(),
            type,
            player == null ? null : player.getUniqueId(),
            now,
            endsAt,
            null,
            null
        );
        sessions.put(session.key(), session);
        createHologram(session);
        schedule(session);
        save();

        World world = block.getWorld();
        Location effectLocation = block.getLocation().add(0.5, 1.1, 0.5);
        world.spawnParticle(Particle.ENCHANT, effectLocation, 80, 0.45, 0.35, 0.45, 0.05);
        world.playSound(effectLocation, Sound.BLOCK_BEACON_ACTIVATE, 1f, 1.25f);
        return true;
    }

    public void abort(Block block, boolean explode, boolean dropUnawakened) {
        AwakeningSession session = sessions.remove(BlockKey.of(block));
        if (session == null) {
            return;
        }

        if (session.task() != null) {
            session.task().cancel();
        }
        removeHologram(session);

        Location center = session.blockLocation().clone().add(0.5, 1.0, 0.5);
        if (dropUnawakened) {
            session.blockLocation().getWorld().dropItemNaturally(center, maceFactory.createUnawakenedWeapon(session.type()));
        }
        if (explode && configManager.getForgeAbortExplosionPower() > 0f) {
            session.blockLocation().getWorld().createExplosion(center, configManager.getForgeAbortExplosionPower(), false, false);
        }
        save();
    }

    private void restore(ForgeSessionStore.StoredForgeSession stored) {
        AwakeningSession session = new AwakeningSession(
            BlockKey.of(stored.location().getBlock()),
            stored.location(),
            stored.type(),
            stored.owner(),
            stored.startedAtMillis(),
            Math.max(System.currentTimeMillis() + 1000L, stored.endsAtMillis()),
            null,
            null
        );
        sessions.put(session.key(), session);
        createHologram(session);
        schedule(session);
    }

    private void schedule(AwakeningSession session) {
        BukkitTask task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> tick(session.key()), 0L, 20L);
        session.task(task);
    }

    private void tick(BlockKey key) {
        AwakeningSession session = sessions.get(key);
        if (session == null) {
            return;
        }
        if (session.blockLocation().getBlock().getType() != configManager.getForgeBlockMaterial()) {
            abort(session.blockLocation().getBlock(), true, true);
            return;
        }

        long remainingMillis = session.endsAtMillis() - System.currentTimeMillis();
        if (remainingMillis <= 0L) {
            complete(session);
            return;
        }
        updateHologram(session, remainingMillis);
    }

    private void complete(AwakeningSession session) {
        sessions.remove(session.key());
        if (session.task() != null) {
            session.task().cancel();
        }
        removeHologram(session);

        Location spawnLocation = session.blockLocation().clone().add(0.5, 1.2, 0.5);
        World world = spawnLocation.getWorld();
        ItemStack result = maceFactory.createMace(session.type());
        if (session.owner() != null && maceManager.canCraft(session.type())) {
            maceManager.register(result, session.owner(), session.type());
        }

        Item item = world.dropItem(spawnLocation, result);
        item.setInvulnerable(true);
        item.setPickupDelay(20);
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (!item.isDead()) {
                item.setInvulnerable(false);
            }
        }, 20L);

        world.spawnParticle(Particle.TOTEM_OF_UNDYING, spawnLocation, 120, 0.6, 0.6, 0.6, 0.15);
        world.playSound(spawnLocation, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.8f);
        if (session.owner() != null) {
            Player owner = plugin.getServer().getPlayer(session.owner());
            if (owner != null) {
                maceManager.onPlayerBecameHolder(owner, spawnLocation, session.type());
            }
        }
        save();
    }

    private void createHologram(AwakeningSession session) {
        Location location = session.blockLocation().clone().add(0.5, 1.7, 0.5);
        try {
            TextDisplay textDisplay = location.getWorld().spawn(location, TextDisplay.class, display -> {
                display.setBillboard(Display.Billboard.CENTER);
                display.setSeeThrough(true);
                display.text(Component.text("Awakening..."));
            });
            session.hologram(textDisplay);
        } catch (Throwable ignored) {
            ArmorStand armorStand = location.getWorld().spawn(location, ArmorStand.class, stand -> {
                stand.setMarker(true);
                stand.setInvisible(true);
                stand.setCustomNameVisible(true);
                stand.customName(Component.text("Awakening..."));
            });
            session.hologram(armorStand);
        }
    }

    private void updateHologram(AwakeningSession session, long remainingMillis) {
        long seconds = Math.max(1L, (remainingMillis + 999L) / 1000L);
        Component text = Component.text("Awakening " + session.type().name() + " - " + format(seconds));
        Entity hologram = session.hologram();
        if (hologram instanceof TextDisplay textDisplay) {
            textDisplay.text(text);
        } else if (hologram instanceof ArmorStand armorStand) {
            armorStand.customName(text);
        }
    }

    private void removeHologram(AwakeningSession session) {
        Entity hologram = session.hologram();
        if (hologram != null && !hologram.isDead()) {
            hologram.remove();
        }
    }

    private String format(long seconds) {
        return String.format("%02d:%02d", seconds / 60L, seconds % 60L);
    }

    private void save() {
        Collection<ForgeSessionStore.StoredForgeSession> stored = sessions.values().stream()
            .map(session -> new ForgeSessionStore.StoredForgeSession(
                session.blockLocation(),
                session.type(),
                session.owner(),
                session.startedAtMillis(),
                session.endsAtMillis()
            ))
            .toList();
        store.save(stored);
    }

    public record BlockKey(UUID worldId, int x, int y, int z) {
        public static BlockKey of(Block block) {
            return new BlockKey(block.getWorld().getUID(), block.getX(), block.getY(), block.getZ());
        }
    }

    private static final class AwakeningSession {
        private final BlockKey key;
        private final Location blockLocation;
        private final MaceType type;
        private final UUID owner;
        private final long startedAtMillis;
        private final long endsAtMillis;
        private Entity hologram;
        private BukkitTask task;

        private AwakeningSession(
            BlockKey key,
            Location blockLocation,
            MaceType type,
            UUID owner,
            long startedAtMillis,
            long endsAtMillis,
            Entity hologram,
            BukkitTask task
        ) {
            this.key = key;
            this.blockLocation = blockLocation;
            this.type = type;
            this.owner = owner;
            this.startedAtMillis = startedAtMillis;
            this.endsAtMillis = endsAtMillis;
            this.hologram = hologram;
            this.task = task;
        }

        private BlockKey key() { return key; }
        private Location blockLocation() { return blockLocation; }
        private MaceType type() { return type; }
        private UUID owner() { return owner; }
        private long startedAtMillis() { return startedAtMillis; }
        private long endsAtMillis() { return endsAtMillis; }
        private Entity hologram() { return hologram; }
        private void hologram(Entity hologram) { this.hologram = hologram; }
        private BukkitTask task() { return task; }
        private void task(BukkitTask task) { this.task = task; }
    }
}
