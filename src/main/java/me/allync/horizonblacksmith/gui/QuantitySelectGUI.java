package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.CraftingRecipe;
import me.allync.horizonblacksmith.utils.ChatUtil;
import me.allync.horizonblacksmith.utils.CurrencyUtil;
import me.allync.horizonblacksmith.utils.ItemBuilder;
import me.allync.horizonblacksmith.utils.NumberUtil;
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

public class QuantitySelectGUI implements InventoryHolder {

    private final HorizonBlacksmith plugin;
    private final Player player;
    private final CraftingRecipe recipe;
    private final Inventory inventory;
    private final Map<Integer, Integer> slotQuantityMap = new HashMap<>(); 

    public QuantitySelectGUI(HorizonBlacksmith plugin, Player player, CraftingRecipe recipe) {
        this.plugin = plugin;
        this.player = player;
        this.recipe = recipe;

        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("quantity-select-gui");
        String title = plugin.getConfigManager().getMessage("quantity-select-gui-title", false);
        int rows = guiConfig != null ? guiConfig.getInt("rows", 3) : 3;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);

        open();
    }

    private void open() {
        generateItems();
        player.openInventory(inventory);
    }

    private void generateItems() {
        inventory.clear();
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("quantity-select-gui");
        if (guiConfig == null) return;

        ConfigurationSection backButtonConfig = guiConfig.getConfigurationSection("back-button");
        if (backButtonConfig != null) {
            ItemStack backButton = new ItemBuilder(backButtonConfig).build();
            inventory.setItem(backButtonConfig.getInt("slot"), backButton);
        }

        ConfigurationSection optionsConfig = guiConfig.getConfigurationSection("options");
        if (optionsConfig != null) {
            for (String key : optionsConfig.getKeys(false)) {
                ConfigurationSection optionConfig = optionsConfig.getConfigurationSection(key);
                if (optionConfig == null) continue;

                int quantity = key.equalsIgnoreCase("fill") ? -1 : Integer.parseInt(key);
                int slot = optionConfig.getInt("slot");

                ItemBuilder builder = new ItemBuilder(optionConfig);
                List<String> finalLore = new ArrayList<>();
                List<String> configLore = optionConfig.getStringList("lore");

                for (String line : configLore) {
                    if (line.contains("{cost_lore}")) {
                        finalLore.addAll(getCostLore(quantity));
                    } else {
                        finalLore.add(ChatUtil.color(line));
                    }
                }
                builder.setLore(finalLore);
                inventory.setItem(slot, builder.build());
                slotQuantityMap.put(slot, quantity);
            }
        }

        if (guiConfig.getBoolean("enable-decorations", true) && guiConfig.contains("fill-item")) {
            String fillMaterialName = guiConfig.getString("fill-item");
            if (guiConfig.isConfigurationSection("fill-item")) {
                fillMaterialName = guiConfig.getString("fill-item.material");
            }

            if (fillMaterialName != null && !fillMaterialName.equalsIgnoreCase("AIR") && !fillMaterialName.equalsIgnoreCase("NONE") && !fillMaterialName.isEmpty()) {
                Material fillMaterial = Material.matchMaterial(fillMaterialName);
                if (fillMaterial != null && fillMaterial != Material.AIR) {
                    ItemStack filler = new ItemBuilder(fillMaterial).setName(" ").build();
                    for (int i = 0; i < inventory.getSize(); i++) {
                        if (inventory.getItem(i) == null) {
                            inventory.setItem(i, filler);
                        }
                    }
                }
            }
        }
    }

    private List<String> getCostLore(int quantity) {
        List<String> costLore = new ArrayList<>();
        if (quantity == -1) { 
            costLore.add(ChatUtil.color("&7- Varies"));
            return costLore;
        }

        if (recipe.getMoneyRequirement() > 0) {

            costLore.add(ChatUtil.color("&7- &a" + CurrencyUtil.formatMoney(recipe.getMoneyRequirement() * quantity)));
        }
        if (recipe.getPointsRequirement() > 0) {

            costLore.add(ChatUtil.color("&7- &b" + CurrencyUtil.formatPoints(recipe.getPointsRequirement() * quantity)));
        }
        if (costLore.isEmpty()) {
            costLore.add(ChatUtil.color("&7- &fNo special costs"));
        }
        return costLore;
    }

    public CraftingRecipe getRecipe() {
        return recipe;
    }

    public int getQuantityForSlot(int slot) {
        return slotQuantityMap.getOrDefault(slot, 0);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}