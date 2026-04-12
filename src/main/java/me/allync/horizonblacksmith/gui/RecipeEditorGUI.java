package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.CraftingRecipe;
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
import java.util.List;
import java.util.stream.Collectors;

public class RecipeEditorGUI implements InventoryHolder {
    private final Inventory inventory;
    private final HorizonBlacksmith plugin;
    public static final int OUTPUT_SLOT = 33;
    public static final List<Integer> INGREDIENT_SLOTS = Arrays.asList(
            20, 21, 22,
            29, 30, 31,
            38, 39, 40
    );

    public static final int ID_SLOT = 0;
    public static final int CATEGORY_SLOT = 1;
    public static final int TIME_SLOT = 2;
    public static final int COOLDOWN_SLOT = 3;
    public static final int PERMISSION_SLOT = 4;
    public static final int MONEY_SLOT = 5;
    public static final int POINTS_SLOT = 6;
    public static final int LEVEL_SLOT = 7;
    public static final int XP_SLOT = 8;
    public static final int PRIORITY_SLOT = 9; 

    public static final int BACK_SLOT = 45;
    public static final int SAVE_SLOT = 53;

    private String recipeId;
    private String category;
    private double moneyRequirement;
    private int pointsRequirement;
    private int craftingTime = 30; 
    private String permission;
    private int cooldown;
    private int forgingLevelRequirement;
    private double xpReward;
    private int priority; 
    private final boolean isEditing;
    private final String originalCategory;

    public RecipeEditorGUI(HorizonBlacksmith plugin, CraftingRecipe recipeToEdit) {
        this.plugin = plugin;
        this.isEditing = (recipeToEdit != null);
        String title = plugin.getConfigManager().getMessage("recipe-editor-gui-title", false);
        this.inventory = Bukkit.createInventory(this, 54, title);

        if (isEditing) {

            this.recipeId = recipeToEdit.getId();
            this.category = recipeToEdit.getCategory();
            this.moneyRequirement = recipeToEdit.getMoneyRequirement();
            this.pointsRequirement = recipeToEdit.getPointsRequirement();
            this.craftingTime = recipeToEdit.getCraftingTime();
            this.permission = recipeToEdit.getPermission().orElse(null);
            this.cooldown = recipeToEdit.getCooldown();
            this.forgingLevelRequirement = recipeToEdit.getForgingLevelRequirement();
            this.xpReward = recipeToEdit.getXpReward();
            this.priority = recipeToEdit.getPriority(); 

            this.inventory.setItem(OUTPUT_SLOT, recipeToEdit.getOutputItem());
            List<ItemStack> ingredients = recipeToEdit.getIngredients();
            for (int i = 0; i < ingredients.size(); i++) {
                if (i < INGREDIENT_SLOTS.size()) {
                    inventory.setItem(INGREDIENT_SLOTS.get(i), ingredients.get(i));
                }
            }
        }
        this.originalCategory = this.category;
        updateItems();
    }

    public RecipeEditorGUI(HorizonBlacksmith plugin, String category) {
        this(plugin, (CraftingRecipe) null);
        this.category = category;
        updateItems();
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public void updateItems() {
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("recipe-editor-gui");
        if(guiConfig == null) {
            plugin.getLogger().severe("recipe-editor-gui section is missing from config.yml!");
            return;
        }

        List<ItemStack> currentIngredients = getIngredients();
        ItemStack currentOutput = getOutputItem();

        if (guiConfig.getBoolean("enable-decorations", true)) {
            ItemStack filler = new ItemBuilder(guiConfig.getConfigurationSection("filler-item")).build();
            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, filler);
            }
        } else {

            for (int i = 0; i < inventory.getSize(); i++) {
                inventory.setItem(i, null);
            }
        }

        inventory.setItem(OUTPUT_SLOT, new ItemBuilder(guiConfig.getConfigurationSection("output-placeholder")).build());

        for (int slot : INGREDIENT_SLOTS) {
            inventory.setItem(slot, new ItemBuilder(guiConfig.getConfigurationSection("ingredient-placeholder")).build());
        }

        if (currentOutput != null) {
            inventory.setItem(OUTPUT_SLOT, currentOutput);
        }
        for (int i = 0; i < currentIngredients.size(); i++) {
            if (i < INGREDIENT_SLOTS.size()) {
                inventory.setItem(INGREDIENT_SLOTS.get(i), currentIngredients.get(i));
            }
        }

        updateControlButtons();
    }

    public void updateControlButtons() {
        ConfigurationSection buttonsConfig = plugin.getConfigManager().getConfig().getConfigurationSection("recipe-editor-gui.buttons");
        if (buttonsConfig == null) {
            plugin.getLogger().severe("recipe-editor-gui.buttons section is missing from config.yml!");
            return;
        }

        inventory.setItem(BACK_SLOT, new ItemBuilder(buttonsConfig.getConfigurationSection("back")).build());
        inventory.setItem(ID_SLOT, buildButton("id", recipeId == null ? "Not Set" : recipeId));
        inventory.setItem(CATEGORY_SLOT, buildButton("category", category == null ? "Not Set" : category));
        inventory.setItem(TIME_SLOT, buildButton("time", TimeUtil.formatDuration(craftingTime)));
        inventory.setItem(COOLDOWN_SLOT, buildButton("cooldown", TimeUtil.formatDuration(cooldown)));
        inventory.setItem(PERMISSION_SLOT, buildButton("permission", permission == null ? "None" : permission));

        inventory.setItem(MONEY_SLOT, buildButton("money", CurrencyUtil.formatMoney(moneyRequirement)));
        inventory.setItem(POINTS_SLOT, buildButton("points", CurrencyUtil.formatPoints(pointsRequirement)));
        inventory.setItem(LEVEL_SLOT, buildButton("level", "Lv. " + forgingLevelRequirement));
        inventory.setItem(XP_SLOT, buildButton("xp", NumberUtil.format(xpReward) + " XP"));
        inventory.setItem(PRIORITY_SLOT, buildButton("priority", String.valueOf(priority))); 

        boolean isReady = recipeId != null && category != null && getOutputItem() != null && !getIngredients().isEmpty();
        ConfigurationSection saveButtonConfig = buttonsConfig.getConfigurationSection(isReady ? "save-ready" : "save-not-ready");
        ItemBuilder saveBuilder = new ItemBuilder(saveButtonConfig);
        if (!isReady) {
            saveBuilder.setLore(getIncompleteLore());
        }
        inventory.setItem(SAVE_SLOT, saveBuilder.build());
    }

    private ItemStack buildButton(String key, String value) {
        ConfigurationSection buttonConfig = plugin.getConfigManager().getConfig().getConfigurationSection("recipe-editor-gui.buttons." + key);
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
        ConfigurationSection notReadyConfig = plugin.getConfigManager().getConfig().getConfigurationSection("recipe-editor-gui.buttons.save-not-ready");
        List<String> lore = new ArrayList<>(notReadyConfig.getStringList("lore"));
        if (recipeId == null) lore.add(ChatUtil.color("&7- &cRecipe ID"));
        if (category == null) lore.add(ChatUtil.color("&7- &cCategory"));
        if (getOutputItem() == null) lore.add(ChatUtil.color("&7- &cOutput Item"));
        if (getIngredients().isEmpty()) lore.add(ChatUtil.color("&7- &cIngredients"));
        return lore;
    }

    public List<ItemStack> getIngredients() {
        return INGREDIENT_SLOTS.stream()
                .map(inventory::getItem)
                .filter(item -> item != null && !item.getType().isAir() && !(item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("Ingredient Slot")))
                .map(ItemStack::clone)
                .collect(Collectors.toList());
    }

    public ItemStack getOutputItem() {
        ItemStack item = inventory.getItem(OUTPUT_SLOT);
        if (item != null && !item.getType().isAir() && !(item.hasItemMeta() && item.getItemMeta().getDisplayName().contains("Output Slot"))) {
            return item.clone();
        }
        return null;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }

    public boolean isEditing() { return isEditing; }
    public String getRecipeId() { return recipeId; }
    public void setRecipeId(String recipeId) { this.recipeId = recipeId; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public double getMoneyRequirement() { return moneyRequirement; }
    public void setMoneyRequirement(double moneyRequirement) { this.moneyRequirement = moneyRequirement; }
    public int getPointsRequirement() { return pointsRequirement; }
    public void setPointsRequirement(int pointsRequirement) { this.pointsRequirement = pointsRequirement; }
    public int getCraftingTime() { return craftingTime; }
    public void setCraftingTime(int craftingTime) { this.craftingTime = craftingTime; }
    public String getPermission() { return permission; }
    public void setPermission(String permission) { this.permission = permission; }
    public int getCooldown() { return cooldown; }
    public void setCooldown(int cooldown) { this.cooldown = cooldown; }
    public int getForgingLevelRequirement() { return forgingLevelRequirement; }
    public void setForgingLevelRequirement(int level) { this.forgingLevelRequirement = level; }
    public double getXpReward() { return xpReward; }
    public void setXpReward(double xp) { this.xpReward = xp; }
    public int getPriority() { return priority; } 
    public void setPriority(int priority) { this.priority = priority; } 
    public String getOriginalCategory() { return originalCategory; }
}