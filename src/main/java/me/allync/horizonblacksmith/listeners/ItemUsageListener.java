package me.allync.horizonblacksmith.listeners;

import io.lumine.mythic.lib.api.item.NBTItem;
import me.allync.horizonblacksmith.HorizonBlacksmith;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.ItemStack;

public class ItemUsageListener implements Listener {

    private final HorizonBlacksmith plugin;

    public ItemUsageListener(HorizonBlacksmith plugin) {
        this.plugin = plugin;
    }

    private boolean shouldCancelUsage(Player player, ItemStack item) {

        if (item == null || item.getType() == Material.AIR || !plugin.getConfigManager().getConfig().getBoolean("forging-level-system.enabled", false)) {
            return false;
        }

        NBTItem nbtItem = NBTItem.get(item);

        if (nbtItem.hasTag("HORIZON_FORGE_LEVEL")) {
            int requiredLevel = nbtItem.getInteger("HORIZON_FORGE_LEVEL");
            int playerLevel = plugin.getCraftingManager().getForgingLevel(player.getUniqueId());

            if (playerLevel < requiredLevel) {

                plugin.getConfigManager().sendMessage(player, "level-requirement-not-met-use",
                        "{required_level}", String.valueOf(requiredLevel),
                        "{current_level}", String.valueOf(playerLevel)
                );
                return true; 
            }
        }

        return false; 
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem(); 

        if (shouldCancelUsage(player, item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player)) {
            return;
        }

        Player player = (Player) event.getDamager();
        ItemStack item = player.getInventory().getItemInMainHand(); 

        if (shouldCancelUsage(player, item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand(); 

        if (shouldCancelUsage(player, item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onItemConsume(PlayerItemConsumeEvent event) {
        Player player = event.getPlayer();
        ItemStack item = event.getItem(); 

        if (shouldCancelUsage(player, item)) {
            event.setCancelled(true);
        }
    }
}
