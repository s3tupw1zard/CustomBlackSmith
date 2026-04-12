package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.CraftingRecipe;
import me.allync.horizonblacksmith.utils.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class AdminGUI implements InventoryHolder {

    private final HorizonBlacksmith plugin;
    private final Player admin;
    private final Inventory inventory;
    private final String category; 
    private int currentPage = 0;
    private static final int ITEMS_PER_PAGE = 45;

    public AdminGUI(HorizonBlacksmith plugin, Player admin, String category) {
        this.plugin = plugin;
        this.admin = admin;
        this.category = category;
        String title = plugin.getConfigManager().getMessage("admin-gui-title", false, "{category}", category);
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("admin-recipe-list-gui");
        int rows = guiConfig != null ? guiConfig.getInt("rows", 6) : 6;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
    }

    public void open() {
        updateItems();
        admin.openInventory(inventory);
    }

    private void updateItems() {
        inventory.clear();
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("admin-recipe-list-gui");
        if (guiConfig == null) return;

        List<CraftingRecipe> recipes = plugin.getRecipeManager().getAllRecipes().stream()
                .filter(r -> r.getCategory().equalsIgnoreCase(this.category))
                .collect(Collectors.toList());

        int startIndex = currentPage * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int recipeIndex = startIndex + i;
            if (recipeIndex < recipes.size()) {
                CraftingRecipe recipe = recipes.get(recipeIndex);
                ItemStack item = new ItemBuilder(recipe.getOutputItem().clone())
                        .setLore(List.of(
                                "&7ID: &f" + recipe.getId(),
                                "",
                                "&eLeft-click to Edit",
                                "&cRight-click to Delete"
                        )).build();
                inventory.setItem(i, item);
            } else {
                break; 
            }
        }

        ConfigurationSection backBtnCfg = guiConfig.getConfigurationSection("back-button");
        if (backBtnCfg != null) {
            inventory.setItem(backBtnCfg.getInt("slot"), new ItemBuilder(backBtnCfg).build());
        }
        ConfigurationSection createBtnCfg = guiConfig.getConfigurationSection("create-button");
        if (createBtnCfg != null) {
            inventory.setItem(createBtnCfg.getInt("slot"), new ItemBuilder(createBtnCfg).build());
        }
        ConfigurationSection prevBtnCfg = guiConfig.getConfigurationSection("prev-page-button");
        if (prevBtnCfg != null && currentPage > 0) {
            inventory.setItem(prevBtnCfg.getInt("slot"), new ItemBuilder(prevBtnCfg).build());
        }
        ConfigurationSection nextBtnCfg = guiConfig.getConfigurationSection("next-page-button");
        if (nextBtnCfg != null && recipes.size() > (currentPage + 1) * ITEMS_PER_PAGE) {
            inventory.setItem(nextBtnCfg.getInt("slot"), new ItemBuilder(nextBtnCfg).build());
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

    public void nextPage() {
        List<CraftingRecipe> recipes = new ArrayList<>(plugin.getRecipeManager().getAllRecipes());
        if(recipes.size() > (currentPage + 1) * ITEMS_PER_PAGE) {
            currentPage++;
            updateItems();
        }
    }

    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            updateItems();
        }
    }

    public String getCategory() {
        return category;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}