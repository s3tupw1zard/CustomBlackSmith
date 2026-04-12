package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class AdminCategoryGUI implements InventoryHolder {

    private final HorizonBlacksmith plugin;
    private final Inventory inventory;
    private final Player player;
    private final Map<Integer, String> slotCategoryMap = new HashMap<>();

    public AdminCategoryGUI(HorizonBlacksmith plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        String title = plugin.getConfigManager().getMessage("admin-category-gui-title", false);
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("admin-category-gui");
        int rows = guiConfig != null ? guiConfig.getInt("rows", 3) : 3;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public void open() {
        updateItems();
        player.openInventory(inventory);
    }

    private void updateItems() {
        inventory.clear();
        ConfigurationSection adminGuiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("admin-category-gui");
        if (adminGuiConfig == null) return;

        if (adminGuiConfig.getBoolean("use-player-gui-categories", true)) {
            ConfigurationSection categoriesConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui.categories");
            if (categoriesConfig != null) {

                Set<String> existingCategories = plugin.getRecipeManager().getAllRecipes().stream()
                        .map(r -> r.getCategory().toLowerCase())
                        .collect(Collectors.toSet());

                for (String key : categoriesConfig.getKeys(false)) {

                    if (existingCategories.contains(key.toLowerCase())) {
                        ConfigurationSection catSection = categoriesConfig.getConfigurationSection(key);
                        if (catSection != null) {
                            ItemStack item = new ItemBuilder(catSection).build();
                            int slot = catSection.getInt("slot");
                            inventory.setItem(slot, item);
                            slotCategoryMap.put(slot, key); 
                        }
                    }
                }
            }
        }

        if (adminGuiConfig.getBoolean("enable-decorations", true) && adminGuiConfig.contains("fill-item")) {
            String fillMaterialName = adminGuiConfig.getString("fill-item");
            if (adminGuiConfig.isConfigurationSection("fill-item")) {
                fillMaterialName = adminGuiConfig.getString("fill-item.material");
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

    public String getCategoryForSlot(int slot) {
        return slotCategoryMap.get(slot);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}