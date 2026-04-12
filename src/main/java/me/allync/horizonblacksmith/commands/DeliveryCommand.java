package me.allync.horizonblacksmith.commands;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.gui.DeliveryGUI;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DeliveryCommand implements CommandExecutor {

    private final HorizonBlacksmith plugin;

    public DeliveryCommand(HorizonBlacksmith plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getConfigManager().sendMessage(sender, "player-only");
            return true;
        }

        DeliveryGUI deliveryGUI = new DeliveryGUI(plugin, player);
        deliveryGUI.open(player);

        return true;
    }
}
