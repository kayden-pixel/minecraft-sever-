package yt.kingez341.kingfeatures.command;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import yt.kingez341.kingfeatures.KingFeaturesPlugin;
import yt.kingez341.kingfeatures.items.FeatureBottleService;
import yt.kingez341.kingfeatures.util.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class KingFeaturesCommand implements CommandExecutor, TabCompleter {
    private final KingFeaturesPlugin plugin;

    public KingFeaturesCommand(KingFeaturesPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            help(sender);
            return true;
        }
        String root = args[0].toLowerCase(Locale.ROOT);
        switch (root) {
            case "troll", "trolllab" -> {
                if (!(sender instanceof Player player)) return fail(sender, "Players only.");
                if (!player.hasPermission("kingfeatures.trolllab")) return fail(sender, "No Troll Lab permission.");
                if (!plugin.isTrollLabAvailable()) return fail(sender, "Troll Lab failed to start. Run /kf status for the exact error.");
                plugin.getTrollLab().openTargets(player);
                return true;
            }
            case "ranks", "rankvisuals", "rank-visuals" -> {
                if (!(sender instanceof Player player)) return fail(sender, "Players only.");
                if (!plugin.isRankVisualsAvailable()) return fail(sender, "Rank visual module failed to start. Run /kf status.");
                plugin.getRankVisuals().open(player);
                return true;
            }
            case "bottle" -> { return bottle(sender, args); }
            case "pack" -> { return pack(sender, args); }
            case "status", "diag", "diagnostics" -> {
                sender.sendMessage(Text.color("&d&lKingFeatures &8• &7v" + plugin.getDescription().getVersion()));
                sender.sendMessage(Text.color("&7Core: &aenabled"));
                sender.sendMessage(Text.color("&7UEP: &f" + plugin.economyStatus()));
                sender.sendMessage(Text.color("&7Bottles: &f" + (plugin.isBottleAvailable() ? "ready" : "unavailable")));
                sender.sendMessage(Text.color("&7Troll Lab: &f" + plugin.trollStatus()));
                sender.sendMessage(Text.color("&7OmniPanel: &f" + plugin.omniPanelStatus()));
                sender.sendMessage(Text.color("&7Rank visuals: &f" + plugin.rankVisualStatus()));
                sender.sendMessage(Text.color("&7Pack: &f" + plugin.packStatus()));
                if (!plugin.moduleFailures().isEmpty()) {
                    sender.sendMessage(Text.color("&cStartup failures:"));
                    for (Map.Entry<String, String> entry : plugin.moduleFailures().entrySet()) {
                        sender.sendMessage(Text.color("&8- &c" + entry.getKey() + "&7: &f" + entry.getValue()));
                    }
                }
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("kingfeatures.admin")) return fail(sender, "No permission.");
                plugin.reloadKingFeatures();
                sender.sendMessage(Text.color("&aKingFeatures reload completed. Run &f/kf status &ato verify every module."));
                return true;
            }
            default -> {
                help(sender);
                return true;
            }
        }
    }

    private boolean bottle(CommandSender sender, String[] args) {
        if (!plugin.isBottleAvailable()) return fail(sender, "Bottle module failed to start. Run /kf status.");
        if (args.length < 2) {
            sender.sendMessage(Text.color("&e/kf bottle buy &7- buy a King's Favor bottle"));
            sender.sendMessage(Text.color("&e/kf bottle template [amount] &7- crate/shop-safe template"));
            sender.sendMessage(Text.color("&e/kf bottle give <player> [amount] &7- direct timed bottle"));
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("buy")) {
            if (!(sender instanceof Player player)) return fail(sender, "Players only.");
            if (!player.hasPermission("kingfeatures.bottle.buy")) return fail(sender, "No permission.");
            FeatureBottleService.BuyResult result = plugin.getBottleService().buy(player);
            player.sendMessage(Text.color((result.success() ? "&a" : "&c") + result.message()));
            return true;
        }
        if (action.equals("template")) {
            if (!(sender instanceof Player player)) return fail(sender, "Players only.");
            if (!player.hasPermission("kingfeatures.bottle.give")) return fail(sender, "No permission.");
            int amount = parseAmount(args.length >= 3 ? args[2] : "1");
            ItemStack item = plugin.getBottleService().createBottle(false, "admin_template");
            item.setAmount(amount);
            player.getInventory().addItem(item);
            player.sendMessage(Text.color("&aAdded crate/shop-safe King's Favor template x" + amount + "."));
            return true;
        }
        if (action.equals("give")) {
            if (!sender.hasPermission("kingfeatures.bottle.give")) return fail(sender, "No permission.");
            if (args.length < 3) return fail(sender, "Usage: /kf bottle give <player> [amount]");
            Player target = Bukkit.getPlayerExact(args[2]);
            if (target == null) return fail(sender, "Player not found.");
            int amount = parseAmount(args.length >= 4 ? args[3] : "1");
            ItemStack item = plugin.getBottleService().createBottle(true, "admin_give");
            item.setAmount(amount);
            target.getInventory().addItem(item);
            sender.sendMessage(Text.color("&aGave " + target.getName() + " King's Favor x" + amount + "."));
            return true;
        }
        return fail(sender, "Unknown bottle action.");
    }

    private boolean pack(CommandSender sender, String[] args) {
        if (!sender.hasPermission("kingfeatures.admin")) return fail(sender, "No permission.");
        if (!plugin.isPackAvailable()) return fail(sender, "Resource-pack module failed to start. Run /kf status.");
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(Text.color("&dPack: &f" + plugin.packStatus()));
            return true;
        }
        if (args[1].equalsIgnoreCase("resend")) {
            Player target = args.length >= 3 ? Bukkit.getPlayerExact(args[2]) : (sender instanceof Player p ? p : null);
            if (target == null) return fail(sender, "Player not found.");
            plugin.getPackManager().send(target);
            sender.sendMessage(Text.color("&aPack refresh requested for " + target.getName() + "."));
            return true;
        }
        return fail(sender, "Usage: /kf pack <status|resend [player]>");
    }

    private void help(CommandSender sender) {
        sender.sendMessage(Text.color("&d&lKingFeatures &8• &fSMP Feature Hub"));
        sender.sendMessage(Text.color("&d/kf ranks &7- OmniPanel rank badge settings/view"));
        sender.sendMessage(Text.color("&d/kf troll &7- open Troll Lab"));
        sender.sendMessage(Text.color("&d/kf bottle buy &7- buy King's Favor (&6255&7)"));
        sender.sendMessage(Text.color("&d/kf status &7- integration/startup diagnostics"));
        if (sender.hasPermission("kingfeatures.admin")) sender.sendMessage(Text.color("&d/kf pack &7| &d/kf reload"));
    }

    private static int parseAmount(String raw) {
        try { return Math.max(1, Math.min(64, Integer.parseInt(raw))); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private static boolean fail(CommandSender sender, String message) {
        sender.sendMessage(Text.color("&c" + message));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> values = new ArrayList<>();
        if (args.length == 1) values.addAll(List.of("ranks", "troll", "bottle", "pack", "status", "reload"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("bottle")) values.addAll(List.of("buy", "template", "give"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("pack")) values.addAll(List.of("status", "resend"));
        else if (args.length == 3 && args[0].equalsIgnoreCase("bottle") && args[1].equalsIgnoreCase("give")) {
            for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
        } else if (args.length == 3 && args[0].equalsIgnoreCase("pack") && args[1].equalsIgnoreCase("resend")) {
            for (Player player : Bukkit.getOnlinePlayers()) values.add(player.getName());
        }
        String current = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        values.removeIf(value -> !value.toLowerCase(Locale.ROOT).startsWith(current));
        return values;
    }
}
