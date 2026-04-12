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

import java.util.List;

public class MyItemsGUI implements InventoryHolder {

    private final HorizonBlacksmith plugin;
    private final Player player;
    private Inventory inventory;
    private int currentPage = 0;
    private final List<ItemStack> itemsToDisplay;
    private static final int ITEMS_PER_PAGE = 45; 

    public MyItemsGUI(HorizonBlacksmith plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
        this.itemsToDisplay = plugin.getCraftingManager().getPendingItems(player.getUniqueId());

        String title = plugin.getConfigManager().getMessage("my-items-gui-title", false);
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("my-items-gui");
        int rows = guiConfig != null ? guiConfig.getInt("rows", 6) : 6;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);

        open();
    }

    private void open() {
        updateItems();
        player.openInventory(inventory);
    }

    public void updateItems() {
        inventory.clear();
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("my-items-gui");
        if (guiConfig == null) return;

        int startIndex = currentPage * ITEMS_PER_PAGE;
        for (int i = 0; i < ITEMS_PER_PAGE; i++) {
            int itemIndex = startIndex + i;
            if (itemIndex < itemsToDisplay.size()) {
                inventory.setItem(i, itemsToDisplay.get(itemIndex));
            } else {
                break;
            }
        }

        ConfigurationSection backButtonConfig = guiConfig.getConfigurationSection("back-button");
        if (backButtonConfig != null) {
            ItemStack backButton = new ItemBuilder(backButtonConfig).build();
            inventory.setItem(backButtonConfig.getInt("slot"), backButton);
        }

        ConfigurationSection claimAllButtonConfig = guiConfig.getConfigurationSection("claim-all-button");
        if (claimAllButtonConfig != null && !itemsToDisplay.isEmpty()) {
            ItemStack claimAllButton = new ItemBuilder(claimAllButtonConfig).build();
            inventory.setItem(claimAllButtonConfig.getInt("slot"), claimAllButton);
        }

        int maxPages = (int) Math.ceil((double) itemsToDisplay.size() / ITEMS_PER_PAGE);
        if (currentPage > 0) {
            ConfigurationSection prevButtonConfig = guiConfig.getConfigurationSection("prev-page-button");
            if (prevButtonConfig != null) {
                ItemStack prevPage = new ItemBuilder(prevButtonConfig).build();
                inventory.setItem(prevButtonConfig.getInt("slot"), prevPage);
            }
        }
        if (currentPage < maxPages - 1) {
            ConfigurationSection nextButtonConfig = guiConfig.getConfigurationSection("next-page-button");
            if (nextButtonConfig != null) {
                ItemStack nextPage = new ItemBuilder(nextButtonConfig).build();
                inventory.setItem(nextButtonConfig.getInt("slot"), nextPage);
            }
        }

        if (guiConfig.getBoolean("enable-decorations", true) && guiConfig.contains("fill-item")) {
            String fillMaterialName = guiConfig.getString("fill-item.material", guiConfig.getString("fill-item"));
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
        int maxPages = (int) Math.ceil((double) itemsToDisplay.size() / ITEMS_PER_PAGE);
        if (currentPage < maxPages - 1) {
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

    public ItemStack getItemAt(int slot) {
        if (slot < 0 || slot >= ITEMS_PER_PAGE) return null;
        int index = (currentPage * ITEMS_PER_PAGE) + slot;
        if (index >= 0 && index < itemsToDisplay.size()) {
            return itemsToDisplay.get(index);
        }
        return null;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}