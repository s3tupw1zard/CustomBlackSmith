package me.allync.horizonblacksmith.commands;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.ActiveCraft;
import me.allync.horizonblacksmith.data.CraftingRecipe;
import me.allync.horizonblacksmith.managers.CraftingManager;
import me.allync.horizonblacksmith.utils.ChatUtil;
import me.allync.horizonblacksmith.utils.TimeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class StatusCommand implements CommandExecutor {

    private final HorizonBlacksmith plugin;

    public StatusCommand(HorizonBlacksmith plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            plugin.getConfigManager().sendMessage(sender, "player-only");
            return true;
        }

        CraftingManager craftingManager = plugin.getCraftingManager();
        List<ActiveCraft> crafts = craftingManager.getActiveCrafts(player.getUniqueId());

        if (crafts.isEmpty()) {
            plugin.getConfigManager().sendMessage(player, "status-no-crafts");
            return true;
        }

        sender.sendMessage(ChatUtil.color(plugin.getConfigManager().getMessage("status-header", true)));
        long currentTime = System.currentTimeMillis();

        for (ActiveCraft craft : crafts) {
            CraftingRecipe recipe = plugin.getRecipeManager().getRecipe(craft.getRecipeId());
            if (recipe == null) continue;

            long remainingMillis = craft.getFinishTime() - currentTime;
            String timeRemaining = TimeUtil.formatDuration(remainingMillis / 1000);
            String itemName = recipe.getOutputItem().getItemMeta().hasDisplayName() ?
                    recipe.getOutputItem().getItemMeta().getDisplayName() :
                    recipe.getId();

            sender.sendMessage(ChatUtil.color(plugin.getConfigManager().getMessage("status-entry", false,
                    "{item_name}", itemName,
                    "{time_left}", timeRemaining
            )));
        }

        return true;
    }
}
