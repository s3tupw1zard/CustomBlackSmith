package me.allync.horizonblacksmith.commands;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.gui.AdminCategoryGUI;
import me.allync.horizonblacksmith.gui.DeliveryAdminGUI;
import me.allync.horizonblacksmith.gui.PlayerGUI;
import me.allync.horizonblacksmith.managers.CraftingManager;
import me.allync.horizonblacksmith.utils.NumberUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class AdminCommand implements CommandExecutor, TabCompleter {

    private final HorizonBlacksmith plugin;
    private static final List<String> TOP_LEVEL_COMMANDS = Arrays.asList("reload", "delivery", "set", "give", "open");
    private static final List<String> MOD_TYPE_COMMANDS = Arrays.asList("level", "exp");

    public AdminCommand(HorizonBlacksmith plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0) {
            openAdminGui(sender);
            return true;
        }

        String subCommand = args[0].toLowerCase();
        switch (subCommand) {
            case "reload":
                handleReload(sender);
                return true;

            case "delivery":
                handleDelivery(sender);
                return true;

            case "open":
                handleOpen(sender, args);
                return true;

            case "set":
            case "give":
                handleLevelExpCommand(sender, subCommand, args);
                return true;

            default:
                plugin.getConfigManager().sendMessage(sender, "admin-command-usage");
                return true;
        }
    }

    private void openAdminGui(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getConfigManager().sendMessage(sender, "player-only");
            return;
        }
        if (!player.hasPermission("customblacksmith.admin.open")) {
            plugin.getConfigManager().sendMessage(player, "no-permission");
            return;
        }
        new AdminCategoryGUI(plugin, player).open();
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("customblacksmith.admin.reload")) {
            plugin.getConfigManager().sendMessage(sender, "no-permission");
            return;
        }
        plugin.getConfigManager().loadConfigs();
        plugin.getRecipeManager().loadRecipes();
        plugin.getDeliveryManager().loadDeliveries();
        plugin.getConfigManager().sendMessage(sender, "reload");
    }

    private void handleDelivery(CommandSender sender) {
        if (!(sender instanceof Player player)) {
            plugin.getConfigManager().sendMessage(sender, "player-only");
            return;
        }
        if (!player.hasPermission("customblacksmith.admin.delivery")) {
            plugin.getConfigManager().sendMessage(player, "no-permission");
            return;
        }
        new DeliveryAdminGUI(plugin, player).open();
    }

    private void handleOpen(CommandSender sender, String[] args) {
        if (!sender.hasPermission("customblacksmith.admin.open")) {
            plugin.getConfigManager().sendMessage(sender, "no-permission");
            return;
        }
        if (args.length != 2) {
            plugin.getConfigManager().sendMessage(sender, "admin-open-usage");
            return;
        }

        Player target = Bukkit.getPlayer(args[1]);
        if (target == null) {
            plugin.getConfigManager().sendMessage(sender, "player-not-online", "{player}", args[1]);
            return;
        }

        new PlayerGUI(plugin, target).openMainMenu();
        plugin.getConfigManager().sendMessage(sender, "admin-open-success", "{player}", target.getName());
    }

    private void handleLevelExpCommand(CommandSender sender, String action, String[] args) {
        if (!sender.hasPermission("customblacksmith.admin.modifydata")) {
            plugin.getConfigManager().sendMessage(sender, "no-permission");
            return;
        }
        if (args.length != 4) {
            plugin.getConfigManager().sendMessage(sender, "admin-set-give-usage");
            return;
        }

        String type = args[1].toLowerCase();
        if (!type.equals("level") && !type.equals("exp")) {
            plugin.getConfigManager().sendMessage(sender, "admin-set-give-usage");
            return;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[2]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            plugin.getConfigManager().sendMessage(sender, "player-not-found", "{player}", args[2]);
            return;
        }

        try {
            double amountDouble = Double.parseDouble(args[3]);
            if (amountDouble < 0) {
                plugin.getConfigManager().sendMessage(sender, "negative-number-not-allowed");
                return;
            }
            int amountInt = (int) amountDouble;
            CraftingManager cm = plugin.getCraftingManager();
            UUID targetUUID = target.getUniqueId();

            switch (action) {
                case "set":
                    if (type.equals("level")) {
                        cm.setForgingLevel(targetUUID, amountInt);
                        plugin.getConfigManager().sendMessage(sender, "admin-set-level-success", "{player}", target.getName(), "{level}", NumberUtil.format(amountInt));
                    } else { 
                        cm.setForgingExp(targetUUID, amountDouble);
                        plugin.getConfigManager().sendMessage(sender, "admin-set-exp-success", "{player}", target.getName(), "{exp}", NumberUtil.format(amountDouble));
                    }
                    break;
                case "give":
                    if (type.equals("level")) {
                        cm.giveForgingLevel(targetUUID, amountInt);
                        int newLevel = cm.getForgingLevel(targetUUID);
                        plugin.getConfigManager().sendMessage(sender, "admin-give-level-success", "{player}", target.getName(), "{amount}", NumberUtil.format(amountInt), "{level}", NumberUtil.format(newLevel));
                    } else { 
                        cm.addForgingExp(targetUUID, amountDouble);
                        double finalExp = cm.getForgingExp(targetUUID);
                        int finalLevel = cm.getForgingLevel(targetUUID);
                        plugin.getConfigManager().sendMessage(sender, "admin-give-exp-success", "{player}", target.getName(), "{amount}", NumberUtil.format(amountDouble), "{exp}", NumberUtil.format(finalExp), "{level}", NumberUtil.format(finalLevel));
                    }
                    break;
            }
        } catch (NumberFormatException e) {
            plugin.getConfigManager().sendMessage(sender, "invalid-number-format", "{input}", args[3]);
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (!sender.hasPermission("customblacksmith.admin.open")) {
            return Collections.emptyList();
        }
        final List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], TOP_LEVEL_COMMANDS, completions);
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("open")) {
                if (sender.hasPermission("customblacksmith.admin.open")) {
                    return null; 
                }
            } else if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give")) {
                if (sender.hasPermission("customblacksmith.admin.modifydata")) {
                    StringUtil.copyPartialMatches(args[1], MOD_TYPE_COMMANDS, completions);
                }
            }
        } else if (args.length == 3) {
            if (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("give")) {
                if (sender.hasPermission("customblacksmith.admin.modifydata")) {
                    return null;
                }
            }
        }
        return completions;
    }
}
