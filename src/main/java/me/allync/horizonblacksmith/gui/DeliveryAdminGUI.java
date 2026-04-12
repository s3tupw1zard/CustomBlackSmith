package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.DeliveryJob;
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

public class DeliveryAdminGUI implements InventoryHolder {

    private final HorizonBlacksmith plugin;
    private final Player admin;
    private final Inventory inventory;
    private int currentPage = 0;

    public DeliveryAdminGUI(HorizonBlacksmith plugin, Player admin) {
        this.plugin = plugin;
        this.admin = admin;
        String title = plugin.getConfigManager().getMessage("delivery-admin-gui-title", false);
        this.inventory = Bukkit.createInventory(this, 54, title);
    }

    public void open() {
        updateItems();
        admin.openInventory(inventory);
    }

    private void updateItems() {
        inventory.clear();
        List<DeliveryJob> jobs = new ArrayList<>(plugin.getDeliveryManager().getAllJobs());

        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("delivery-admin-gui");
        if (guiConfig == null) {
            plugin.getLogger().severe("delivery-admin-gui section is missing from config.yml!");
            return;
        }

        int startIndex = currentPage * 45;
        for (int i = 0; i < 45; i++) {
            int jobIndex = startIndex + i;
            if (jobIndex < jobs.size()) {
                DeliveryJob job = jobs.get(jobIndex);
                ItemStack item = new ItemBuilder(job.getGuiItem().clone())
                        .setLore(List.of(
                                "&7ID: &f" + job.getId(),
                                "",
                                "&eLeft-click to Edit",
                                "&cRight-click to Delete"
                        )).build();
                inventory.setItem(i, item);
            } else {
                break; 
            }
        }

        inventory.setItem(guiConfig.getInt("create-button.slot"), new ItemBuilder(guiConfig.getConfigurationSection("create-button")).build());
        if(currentPage > 0) {
            inventory.setItem(guiConfig.getInt("prev-page-button.slot"), new ItemBuilder(guiConfig.getConfigurationSection("prev-page-button")).build());
        }
        if(jobs.size() > (currentPage + 1) * 45) {
            inventory.setItem(guiConfig.getInt("next-page-button.slot"), new ItemBuilder(guiConfig.getConfigurationSection("next-page-button")).build());
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
        currentPage++;
        updateItems();
    }

    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            updateItems();
        }
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}