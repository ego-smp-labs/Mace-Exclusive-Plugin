package vn.nirussv.maceexclusive.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import vn.nirussv.maceexclusive.MaceExclusivePlugin;
import vn.nirussv.maceexclusive.config.ConfigManager;
import vn.nirussv.maceexclusive.core.CoreConfig;
import vn.nirussv.maceexclusive.core.CoreItemFactory;
import vn.nirussv.maceexclusive.core.CoreRegistry;
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ItemRegistry;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.mace.MaceManager.AcquisitionReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MaceCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION_INFO = "maceexclusive.command.info";
    private static final String PERMISSION_GIVE = "maceexclusive.command.give";
    private static final String PERMISSION_RELOAD = "maceexclusive.command.reload";
    private static final String PERMISSION_RESET = "maceexclusive.command.reset";
    private static final String PERMISSION_ADMIN = "maceexclusive.admin";
    private static final String LEGACY_PERMISSION_ADMIN = "mace.admin";

    private final ConfigManager configManager;
    private final MaceManager maceManager;
    private final ExclusiveItemFactory itemFactory;
    private final ItemRegistry itemRegistry;
    private final CoreRegistry coreRegistry;
    private final CoreItemFactory coreItemFactory;
    private final MaceInfoMenu infoMenu;

    public MaceCommand(MaceExclusivePlugin plugin, MaceManager maceManager, ConfigManager configManager, ExclusiveItemFactory itemFactory, ItemRegistry itemRegistry, CoreRegistry coreRegistry, CoreItemFactory coreItemFactory, MaceInfoMenu infoMenu) {
        this.maceManager = maceManager;
        this.configManager = configManager;
        this.itemFactory = itemFactory;
        this.itemRegistry = itemRegistry;
        this.coreRegistry = coreRegistry;
        this.coreItemFactory = coreItemFactory;
        this.infoMenu = infoMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(sender); return true; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "info" -> { if (checkPerm(sender, PERMISSION_INFO)) handleInfo(sender, args); }
            case "reset" -> { if (checkPerm(sender, PERMISSION_RESET)) handleReset(sender, args); }
            case "give" -> {
                if (!checkPerm(sender, PERMISSION_GIVE)) return true;
                if (!(sender instanceof Player player)) { sender.sendMessage(configManager.getPrefixedMessage("only-player")); return true; }
                handleGive(player, args);
            }
            case "reload" -> {
                if (!checkPerm(sender, PERMISSION_RELOAD)) return true;
                configManager.reload();
                itemRegistry.reload();
                coreRegistry.reload();
                sender.sendMessage(configManager.getPrefixedMessage("config-reloaded"));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleGive(Player player, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("all")) {
            handleGiveAll(player);
            return;
        }
        Optional<String> parsed = parseItemId(args, 1);
        Optional<String> parsedCore = parseCoreId(args, 1);
        if (parsed.isEmpty() && parsedCore.isEmpty()) { sendItemUsage(player); return; }
        if (parsedCore.isPresent()) {
            giveOrDrop(player, coreItemFactory.create(parsedCore.get()));
            player.sendMessage(configManager.getPrefixedMessage("core.received", Map.of("name", parsedCore.get())));
            return;
        }
        String id = parsed.orElseThrow();
        if (!maceManager.canCreate(id)) {
            String holderName = maceManager.getHolderName(id);
            player.sendMessage(configManager.getPrefixedMessage(messageKey(id, "already-exists"), Map.of("player", holderName != null ? holderName : "Unknown", "name", maceManager.displayName(id))));
            return;
        }
        ItemStack item = itemFactory.create(id);
        maceManager.register(item, player.getUniqueId(), id);
        giveOrDrop(player, item);
        maceManager.notifyAcquisition(player, player.getLocation(), id, AcquisitionReason.RECEIVED);
    }

    private void handleGiveAll(Player player) {
        int given = 0;
        int skipped = 0;
        for (String id : itemRegistry.ids()) {
            if (!maceManager.canCreate(id)) {
                skipped++;
                continue;
            }
            ItemStack item = itemFactory.create(id);
            maceManager.register(item, player.getUniqueId(), id);
            giveOrDrop(player, item);
            given++;
        }
        for (CoreConfig core : coreRegistry.all()) {
            if (!core.enabled()) continue;
            giveOrDrop(player, coreItemFactory.create(core.id()));
            given++;
        }
        player.sendMessage(configManager.getPrefixedMessage("give-all-complete", Map.of("count", String.valueOf(given), "skipped", String.valueOf(skipped))));
    }

    private void giveOrDrop(Player player, ItemStack item) {
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        for (ItemStack leftover : leftovers.values()) {
            player.getWorld().dropItemNaturally(player.getLocation(), leftover);
        }
    }

    private void handleReset(CommandSender sender, String[] args) {
        Optional<String> parsed = parseItemId(args, 1);
        if (parsed.isEmpty()) { sendItemUsage(sender); return; }
        String id = parsed.get();
        sender.sendMessage(configManager.getPrefixedMessage(maceManager.reset(id) ? messageKey(id, "reset") : messageKey(id, "not-found"), Map.of("name", maceManager.displayName(id))));
    }

    private void handleInfo(CommandSender sender, String[] args) {
        if (args.length <= 1) {
            if (sender instanceof Player player) {
                infoMenu.openOverview(player);
            } else {
                sendItemUsage(sender);
            }
            return;
        }
        Optional<String> parsed = parseItemId(args, 1);
        if (parsed.isEmpty()) { sendItemUsage(sender); return; }
        String id = parsed.get();
        String holder = maceManager.getHolderName(id);
        if (holder != null) sender.sendMessage(configManager.getPrefixedMessage(messageKey(id, "holder-info"), Map.of("player", holder, "name", maceManager.displayName(id))));
        else sender.sendMessage(configManager.getPrefixedMessage(messageKey(id, "not-found"), Map.of("name", maceManager.displayName(id))));
    }

    private Optional<String> parseItemId(String[] args, int index) {
        if (args.length <= index) return Optional.empty();
        String arg = args[index].toLowerCase();
        switch (arg) {
            case "power" -> arg = "power_mace";
            case "void" -> arg = "void_mace";
            case "chaos" -> arg = "chaos_mace";
            case "vampiric" -> arg = "vampiric_mace";
            case "gravity" -> arg = "gravity_mace";
            case "soulfire" -> arg = "soulfire_mace";
            case "sonic" -> arg = "sonic_spear";
            case "chronos" -> arg = "chronos_anchor_spear";
        }
        return itemRegistry.find(arg).map(def -> def.id());
    }

    private Optional<String> parseCoreId(String[] args, int index) {
        if (args.length <= index) return Optional.empty();
        String arg = args[index].toLowerCase();
        return coreRegistry.find(arg).map(CoreConfig::id);
    }

    private String messageKey(String id, String suffix) { return "chaos_mace".equals(id) ? "chaos." + suffix : "mace." + suffix; }
    private void sendItemUsage(CommandSender sender) { sender.sendMessage(configManager.getMessage("help.item-usage")); }
    private void sendHelp(CommandSender sender) { sender.sendMessage(configManager.getMessage("help.header")); sender.sendMessage(configManager.getMessage("help.link")); sender.sendMessage(configManager.getMessage("help.commands")); sender.sendMessage(configManager.getMessage("help.footer")); }
    private boolean checkPerm(CommandSender sender, String perm) {
        if (!(sender instanceof Player)) return true;
        if (sender.hasPermission(perm) || sender.hasPermission(PERMISSION_ADMIN) || sender.hasPermission(LEGACY_PERMISSION_ADMIN)) return true;
        sender.sendMessage(configManager.getPrefixedMessage("no-permission"));
        return false;
    }

    private boolean canUse(CommandSender sender, String perm) {
        return !(sender instanceof Player) || sender.hasPermission(perm) || sender.hasPermission(PERMISSION_ADMIN) || sender.hasPermission(LEGACY_PERMISSION_ADMIN);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("info", "help"));
            subs.removeIf(sub -> sub.equals("info") && !canUse(sender, PERMISSION_INFO));
            if (canUse(sender, PERMISSION_GIVE)) subs.add("give");
            if (canUse(sender, PERMISSION_RESET)) subs.add("reset");
            if (canUse(sender, PERMISSION_RELOAD)) subs.add("reload");
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && List.of("info", "give", "reset").contains(args[0].toLowerCase())) {
            if (args[0].equalsIgnoreCase("info") && !canUse(sender, PERMISSION_INFO)) return List.of();
            if (args[0].equalsIgnoreCase("give") && !canUse(sender, PERMISSION_GIVE)) return List.of();
            if (args[0].equalsIgnoreCase("reset") && !canUse(sender, PERMISSION_RESET)) return List.of();
            if (args[0].equalsIgnoreCase("give")) {
                List<String> ids = new ArrayList<>(itemRegistry.ids());
                ids.add("all");
                ids.addAll(coreRegistry.all().stream().map(CoreConfig::id).toList());
                return ids.stream().filter(id -> id.startsWith(args[1].toLowerCase())).toList();
            }
            return itemRegistry.ids().stream().filter(id -> id.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
