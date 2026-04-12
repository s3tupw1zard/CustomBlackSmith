package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.ActiveCraft;
import me.allync.horizonblacksmith.utils.ItemBuilder;
import me.allync.horizonblacksmith.utils.NumberUtil;
import me.allync.horizonblacksmith.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpeedUpGUI implements InventoryHolder {

    private final HorizonBlacksmith plugin;
    private final Player player;
    private final ActiveCraft craft;
    private final Inventory inventory;
    private final Map<Integer, String> slotOptionMap = new HashMap<>();

    public SpeedUpGUI(HorizonBlacksmith plugin, Player player, ActiveCraft craft) {
        this.plugin = plugin;
        this.player = player;
        this.craft = craft;

        ConfigurationSection speedUpConfig = plugin.getConfigManager().getConfig().getConfigurationSection("craft-speed-up");
        String title = plugin.getConfigManager().getMessage("craft-speed-up-gui-title", false);
        this.inventory = Bukkit.createInventory(this, 27, title);

        generateItems();
        player.openInventory(inventory);
    }

    private void generateItems() {
        ConfigurationSection optionsSection = plugin.getConfigManager().getConfig().getConfigurationSection("craft-speed-up.options");
        if (optionsSection == null) {
            player.closeInventory();
            return;
        }

        long remainingMillis = Math.max(0, craft.getFinishTime() - System.currentTimeMillis());
        long remainingSeconds = remainingMillis / 1000;

        for (String optionKey : optionsSection.getKeys(false)) {
            ConfigurationSection option = optionsSection.getConfigurationSection(optionKey);
            if (option == null) continue;

            int slot = option.getInt("slot");
            Material material = Material.matchMaterial(option.getString("material", "STONE"));
            String displayName = option.getString("display-name");
            List<String> lore = new ArrayList<>(option.getStringList("lore"));

            int cost = 0;
            long timeToReduceSeconds = 0;

            if (option.contains("reduce-seconds")) {
                timeToReduceSeconds = option.getInt("reduce-seconds");
                cost = option.getInt("points-cost");
            } else if (option.contains("reduce-percentage")) {
                double percentage = option.getDouble("reduce-percentage");
                timeToReduceSeconds = (long) (remainingSeconds * (percentage / 100.0));
                cost = option.getInt("points-cost");
            } else if (option.contains("points-per-second")) {
                timeToReduceSeconds = remainingSeconds;
                double costPerSecond = option.getDouble("points-per-second");
                cost = (int) Math.ceil(remainingSeconds * costPerSecond);
            }

            for (int i = 0; i < lore.size(); i++) {
                lore.set(i, lore.get(i)
                        .replace("{cost}", NumberUtil.format(cost))
                        .replace("{time}", TimeUtil.formatDuration(option.getInt("reduce-seconds", 0)))
                        .replace("{percentage}", String.valueOf(option.getInt("reduce-percentage", 0)))
                        .replace("{time_skipped}", TimeUtil.formatDuration(timeToReduceSeconds))
                        .replace("{time_remaining}", TimeUtil.formatDuration(remainingSeconds))
                );
            }

            ItemStack item = new ItemBuilder(material)
                    .setName(displayName)
                    .setLore(lore)
                    .build();

            inventory.setItem(slot, item);
            slotOptionMap.put(slot, optionKey);
        }
    }

    public String getOptionId(int slot) {
        return slotOptionMap.get(slot);
    }

    public ActiveCraft getCraft() {
        return craft;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}
