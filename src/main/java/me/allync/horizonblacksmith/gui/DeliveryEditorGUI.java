package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.DeliveryJob;
import me.allync.horizonblacksmith.utils.ChatUtil;
import me.allync.horizonblacksmith.utils.CurrencyUtil;
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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class DeliveryEditorGUI implements InventoryHolder {
    private final Inventory inventory;
    private final HorizonBlacksmith plugin;

    public static final int GUI_ITEM_SLOT = 13;
    public static final List<Integer> REQUIREMENT_SLOTS = Arrays.asList(28, 29, 30, 31, 32, 33, 34);
    public static final List<Integer> REWARD_SLOTS = Arrays.asList(46, 47, 48, 49, 50, 51, 52);

    public static final int BACK_SLOT = 0;
    public static final int ID_SLOT = 1;
    public static final int TIME_SLOT = 2;
    public static final int COOLDOWN_SLOT = 3;
    public static final int PERMISSION_SLOT = 4;
    public static final int MONEY_SLOT = 5;
    public static final int POINTS_SLOT = 6;
    public static final int SAVE_SLOT = 8;

    private String jobId;
    private double rewardMoney;
    private int rewardPoints;
    private int deliveryTime = 60; 
    private String permission;
    private int cooldown;
    private final boolean isEditing;

    public DeliveryEditorGUI(HorizonBlacksmith plugin, DeliveryJob jobToEdit) {
        this.plugin = plugin;
        this.isEditing = (jobToEdit != null);
        String title = plugin.getConfigManager().getMessage("delivery-editor-gui-title", false);
        this.inventory = Bukkit.createInventory(this, 54, title);

        if (isEditing) {
            this.jobId = jobToEdit.getId();
            this.rewardMoney = jobToEdit.getRewardMoney();
            this.rewardPoints = jobToEdit.getRewardPoints();
            this.deliveryTime = jobToEdit.getDeliveryTime();
            this.permission = jobToEdit.getPermission().orElse(null);
            this.cooldown = jobToEdit.getCooldown();

            this.inventory.setItem(GUI_ITEM_SLOT, jobToEdit.getGuiItem());

            List<ItemStack> requirements = jobToEdit.getRequirements();
            for (int i = 0; i < requirements.size(); i++) {
                if (i < REQUIREMENT_SLOTS.size()) {
                    inventory.setItem(REQUIREMENT_SLOTS.get(i), requirements.get(i));
                }
            }
            List<ItemStack> rewards = jobToEdit.getRewardItems();
            for (int i = 0; i < rewards.size(); i++) {
                if (i < REWARD_SLOTS.size()) {
                    inventory.setItem(REWARD_SLOTS.get(i), rewards.get(i));
                }
            }
        }
        updateItems();
    }

    public DeliveryEditorGUI(HorizonBlacksmith plugin) {
        this(plugin, null);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void updateItems() {
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("delivery-editor-gui");
        if(guiConfig == null) {
            plugin.getLogger().severe("delivery-editor-gui section is missing from config.yml!");
            return;
        }

        ItemStack currentGuiItem = getGuiItem();
        List<ItemStack> currentRequirements = getRequirements();
        List<ItemStack> currentRewardItems = getRewardItems();

        inventory.clear();

        inventory.setItem(GUI_ITEM_SLOT, new ItemBuilder(guiConfig.getConfigurationSection("gui-item-placeholder")).build());
        for (int slot : REQUIREMENT_SLOTS) {
            inventory.setItem(slot, new ItemBuilder(guiConfig.getConfigurationSection("requirement-placeholder")).build());
        }
        for (int slot : REWARD_SLOTS) {
            inventory.setItem(slot, new ItemBuilder(guiConfig.getConfigurationSection("reward-placeholder")).build());
        }
        updateControlButtons();

        if (currentGuiItem != null) inventory.setItem(GUI_ITEM_SLOT, currentGuiItem);
        for (int i = 0; i < currentRequirements.size(); i++) {
            if (i < REQUIREMENT_SLOTS.size()) inventory.setItem(REQUIREMENT_SLOTS.get(i), currentRequirements.get(i));
        }
        for (int i = 0; i < currentRewardItems.size(); i++) {
            if (i < REWARD_SLOTS.size()) inventory.setItem(REWARD_SLOTS.get(i), currentRewardItems.get(i));
        }

        if (guiConfig.getBoolean("enable-decorations", true) && guiConfig.contains("filler-item")) {
            String fillMaterialName = guiConfig.getString("filler-item.material", guiConfig.getString("filler-item"));
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

    public void updateControlButtons() {
        ConfigurationSection buttonsConfig = plugin.getConfigManager().getConfig().getConfigurationSection("delivery-editor-gui.buttons");
        if (buttonsConfig == null) {
            plugin.getLogger().severe("delivery-editor-gui.buttons section is missing from config.yml!");
            return;
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(buttonsConfig.getConfigurationSection("back")).build());
        inventory.setItem(ID_SLOT, buildButton("id", jobId == null ? "Not Set" : jobId));
        inventory.setItem(TIME_SLOT, buildButton("time", TimeUtil.formatDuration(deliveryTime)));
        inventory.setItem(COOLDOWN_SLOT, buildButton("cooldown", TimeUtil.formatDuration(cooldown)));
        inventory.setItem(PERMISSION_SLOT, buildButton("permission", permission == null ? "None" : permission));

        inventory.setItem(MONEY_SLOT, buildButton("money", CurrencyUtil.formatMoney(rewardMoney)));
        inventory.setItem(POINTS_SLOT, buildButton("points", CurrencyUtil.formatPoints(rewardPoints)));

        boolean isReady = jobId != null && getGuiItem() != null && !getRequirements().isEmpty();
        ConfigurationSection saveButtonConfig = buttonsConfig.getConfigurationSection(isReady ? "save-ready" : "save-not-ready");
        ItemBuilder saveBuilder = new ItemBuilder(saveButtonConfig);
        if (!isReady) {
            saveBuilder.setLore(getIncompleteLore());
        }
        inventory.setItem(SAVE_SLOT, saveBuilder.build());
    }

    private ItemStack buildButton(String key, String value) {
        ConfigurationSection buttonConfig = plugin.getConfigManager().getConfig().getConfigurationSection("delivery-editor-gui.buttons." + key);
        ItemBuilder builder = new ItemBuilder(buttonConfig);
        List<String> finalLore = new ArrayList<>();
        for (String line : buttonConfig.getStringList("lore")) {
            if(line.contains("{is_editing_lore}") && isEditing) {
                finalLore.add(ChatUtil.color(buttonConfig.getString("is_editing_text", "&cCannot be changed.")));
            } else if (!line.contains("{is_editing_lore}")) {
                finalLore.add(ChatUtil.color(line.replace("{value}", "&f" + value)));
            }
        }
        builder.setLore(finalLore);
        return builder.build();
    }

    private List<String> getIncompleteLore() {
        ConfigurationSection notReadyConfig = plugin.getConfigManager().getConfig().getConfigurationSection("delivery-editor-gui.buttons.save-not-ready");
        List<String> lore = new ArrayList<>(notReadyConfig.getStringList("lore"));
        if (jobId == null) lore.add(ChatUtil.color("&7- &cJob ID"));
        if (getGuiItem() == null) lore.add(ChatUtil.color("&7- &cGUI Item"));
        if (getRequirements().isEmpty()) lore.add(ChatUtil.color("&7- &cRequirements"));
        return lore;
    }

    public List<ItemStack> getRequirements() {
        return REQUIREMENT_SLOTS.stream().map(inventory::getItem).filter(i -> i != null && !i.getType().isAir() && !(i.hasItemMeta() && i.getItemMeta().getDisplayName().contains("Requirement Slot"))).map(ItemStack::clone).collect(Collectors.toList());
    }

    public List<ItemStack> getRewardItems() {
        return REWARD_SLOTS.stream().map(inventory::getItem).filter(i -> i != null && !i.getType().isAir() && !(i.hasItemMeta() && i.getItemMeta().getDisplayName().contains("Reward Item Slot"))).map(ItemStack::clone).collect(Collectors.toList());
    }

    public ItemStack getGuiItem() {
        ItemStack item = inventory.getItem(GUI_ITEM_SLOT);
        if (item != null && !item.getType().isAir() && !(item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("GUI Item"))) {
            return item.clone();
        }
        return null;
    }

    @NotNull @Override public Inventory getInventory() { return inventory; }

    public boolean isEditing() { return isEditing; }
    public String getJobId() { return jobId; }
    public void setJobId(String jobId) { this.jobId = jobId; }
    public double getRewardMoney() { return rewardMoney; }
    public void setRewardMoney(double rewardMoney) { this.rewardMoney = rewardMoney; }
    public int getRewardPoints() { return rewardPoints; }
    public void setRewardPoints(int rewardPoints) { this.rewardPoints = rewardPoints; }
    public int getDeliveryTime() { return deliveryTime; }
    public void setDeliveryTime(int deliveryTime) { this.deliveryTime = deliveryTime; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public int getCooldown() { return cooldown; }
    public void setCooldown(int cooldown) { this.cooldown = cooldown; }

    public List<String> getRewardCommands() { return Collections.emptyList(); }
}