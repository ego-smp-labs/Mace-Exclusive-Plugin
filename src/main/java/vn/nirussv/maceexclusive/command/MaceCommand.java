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
import vn.nirussv.maceexclusive.item.ExclusiveItemFactory;
import vn.nirussv.maceexclusive.item.ItemRegistry;
import vn.nirussv.maceexclusive.mace.MaceManager;
import vn.nirussv.maceexclusive.mace.MaceManager.AcquisitionReason;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class MaceCommand implements CommandExecutor, TabCompleter {

    private final ConfigManager configManager;
    private final MaceManager maceManager;
    private final ExclusiveItemFactory itemFactory;
    private final ItemRegistry itemRegistry;
    private final MaceInfoMenu infoMenu;

    public MaceCommand(MaceExclusivePlugin plugin, MaceManager maceManager, ConfigManager configManager, ExclusiveItemFactory itemFactory, ItemRegistry itemRegistry, MaceInfoMenu infoMenu) {
        this.maceManager = maceManager;
        this.configManager = configManager;
        this.itemFactory = itemFactory;
        this.itemRegistry = itemRegistry;
        this.infoMenu = infoMenu;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("help")) { sendHelp(sender); return true; }
        String sub = args[0].toLowerCase();
        switch (sub) {
            case "info" -> handleInfo(sender, args);
            case "reset" -> { if (checkPerm(sender, "mace.admin")) handleReset(sender, args); }
            case "give" -> {
                if (!checkPerm(sender, "mace.admin")) return true;
                if (!(sender instanceof Player player)) { sender.sendMessage(configManager.getPrefixedMessage("only-player")); return true; }
                handleGive(player, args);
            }
            case "reload" -> {
                if (!checkPerm(sender, "mace.admin")) return true;
                configManager.reload();
                itemRegistry.reload();
                sender.sendMessage(configManager.getPrefixedMessage("config-reloaded"));
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleGive(Player player, String[] args) {
        Optional<String> parsed = parseItemId(args, 1);
        if (parsed.isEmpty()) { sendItemUsage(player); return; }
        String id = parsed.get();
        if (!maceManager.canCreate(id)) {
            String holderName = maceManager.getHolderName(id);
            player.sendMessage(configManager.getPrefixedMessage(messageKey(id, "already-exists"), Map.of("player", holderName != null ? holderName : "Unknown", "name", maceManager.displayName(id))));
            return;
        }
        ItemStack item = itemFactory.create(id);
        maceManager.register(item, player.getUniqueId(), id);
        player.getInventory().addItem(item);
        maceManager.notifyAcquisition(player, player.getLocation(), id, AcquisitionReason.RECEIVED);
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
        if (arg.equals("power")) arg = "power_mace";
        if (arg.equals("chaos")) arg = "chaos_mace";
        return itemRegistry.find(arg).map(def -> def.id());
    }

    private String messageKey(String id, String suffix) { return "chaos_mace".equals(id) ? "chaos." + suffix : "mace." + suffix; }
    private void sendItemUsage(CommandSender sender) { sender.sendMessage("Usage: /macee <give|reset|info> <item_id>"); }
    private void sendHelp(CommandSender sender) { sender.sendMessage(configManager.getMessage("help.header")); sender.sendMessage(configManager.getMessage("help.link")); sender.sendMessage(configManager.getMessage("help.commands")); sender.sendMessage(configManager.getMessage("help.footer")); }
    private boolean checkPerm(CommandSender sender, String perm) { if (sender.hasPermission(perm)) return true; sender.sendMessage(configManager.getPrefixedMessage("no-permission")); return false; }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) {
            List<String> subs = new ArrayList<>(List.of("info", "help"));
            if (sender.hasPermission("mace.admin")) subs.addAll(List.of("give", "reset", "reload"));
            return subs.stream().filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (args.length == 2 && List.of("info", "give", "reset").contains(args[0].toLowerCase())) {
            return itemRegistry.ids().stream().filter(id -> id.startsWith(args[1].toLowerCase())).toList();
        }
        return List.of();
    }
}
