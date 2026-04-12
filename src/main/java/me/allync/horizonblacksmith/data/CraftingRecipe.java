package me.allync.horizonblacksmith.data;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.api.CustomItemsHook;
import me.allync.horizonblacksmith.utils.ItemBuilder;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class CraftingRecipe {

    private final String id;
    private final ItemStack outputItem;
    private final String category;
    private final List<ItemStack> ingredients;
    private final String permission;
    private final double moneyRequirement;
    private final int pointsRequirement;
    private final int forgingLevelRequirement; 
    private final double xpReward; 
    private final int cooldown; 
    private final int craftingTime; 
    private final int priority; 

    public CraftingRecipe(String id, ConfigurationSection section) {
        this.id = id;

        ConfigurationSection outputSection = section.getConfigurationSection("output");
        if (outputSection == null) throw new IllegalArgumentException("Output section is missing for recipe " + id);
        Map<String, Object> outputMap = outputSection.getValues(false);

        if (outputMap.containsKey("v") || outputMap.containsKey("id") || outputMap.containsKey("DataVersion")) {

            outputMap.remove("amount"); 
            this.outputItem = ItemStack.deserialize(outputMap);
        } else {

            this.outputItem = HorizonBlacksmith.getInstance().getCustomItemsHook().getItemStack(outputSection);
            if (this.outputItem != null && this.outputItem.getType() != Material.AIR) {
                ItemBuilder.fromConfig(this.outputItem, outputSection);
            } else {
                throw new IllegalArgumentException("Invalid output item for recipe " + id);
            }
        }
        if (this.outputItem == null || this.outputItem.getType() == Material.AIR) {
            throw new IllegalArgumentException("Invalid output item for recipe " + id);
        }

        this.category = section.getString("category", "Misc");

        this.ingredients = new ArrayList<>();
        List<Map<?, ?>> ingredientMapList = section.getMapList("ingredients");
        for (Map<?, ?> map : ingredientMapList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> castedMap = new HashMap<>((Map<String, Object>) map); 

            if (castedMap.containsKey("v") || castedMap.containsKey("id") || castedMap.containsKey("DataVersion")) {

                int amount = 1;

                if (castedMap.containsKey("amount")) {
                    amount = ((Number) castedMap.get("amount")).intValue();
                }
                castedMap.remove("amount"); 

                ItemStack item = ItemStack.deserialize(castedMap);

                if (amount > item.getAmount()) {
                    item.setAmount(amount);
                }
                this.ingredients.add(item);

            } else {

                ConfigurationSection ingredientSection = HorizonBlacksmith.getInstance().getConfig().createSection("temp", castedMap);
                ItemStack item = HorizonBlacksmith.getInstance().getCustomItemsHook().getItemStack(ingredientSection);
                if (item != null) {
                    item.setAmount(ingredientSection.getInt("amount", 1));
                    ItemBuilder.fromConfig(item, ingredientSection);
                    this.ingredients.add(item);
                }
            }
        }

        this.permission = section.getString("requirements.permission");
        this.moneyRequirement = section.getDouble("requirements.money", 0);
        this.pointsRequirement = section.getInt("requirements.points", 0);
        this.forgingLevelRequirement = section.getInt("requirements.forging-level", 0); 
        this.xpReward = section.getDouble("settings.xp-reward", 0); 
        this.cooldown = section.getInt("settings.cooldown", 0);
        this.craftingTime = section.getInt("settings.crafting-time", 10);
        this.priority = section.getInt("settings.priority", 0); 
    }

    public CraftingRecipe(String id, ItemStack outputItem, String category, List<ItemStack> ingredients,
                          String permission, double moneyRequirement, int pointsRequirement,
                          int forgingLevelRequirement, double xpReward, int cooldown, int craftingTime, int priority) {
        this.id = id;
        this.outputItem = outputItem;
        this.category = category;
        this.ingredients = ingredients;
        this.permission = permission;
        this.moneyRequirement = moneyRequirement;
        this.pointsRequirement = pointsRequirement;
        this.forgingLevelRequirement = forgingLevelRequirement; 
        this.xpReward = xpReward; 
        this.cooldown = cooldown;
        this.craftingTime = craftingTime;
        this.priority = priority; 
    }

    public void saveTo(ConfigurationSection section) {

        section.set("output", serializeItemStack(outputItem, false));

        section.set("category", category);

        List<Map<String, Object>> ingredientList = new ArrayList<>();
        for (ItemStack ingredient : ingredients) {
            ingredientList.add(serializeItemStack(ingredient, true));
        }
        section.set("ingredients", ingredientList);

        if (permission != null && !permission.isEmpty()) {
            section.set("requirements.permission", permission);
        }
        if (moneyRequirement > 0) {
            section.set("requirements.money", moneyRequirement);
        }
        if (pointsRequirement > 0) {
            section.set("requirements.points", pointsRequirement);
        }
        if (forgingLevelRequirement > 0) { 
            section.set("requirements.forging-level", forgingLevelRequirement);
        }

        if (cooldown > 0) {
            section.set("settings.cooldown", cooldown);
        }
        if (craftingTime > 0) {
            section.set("settings.crafting-time", craftingTime);
        }
        if (xpReward > 0) { 
            section.set("settings.xp-reward", xpReward);
        }
        if (priority != 0) { 
            section.set("settings.priority", priority);
        }
    }

    private Map<String, Object> serializeItemStack(ItemStack item, boolean includeAmount) {
        CustomItemsHook hook = HorizonBlacksmith.getInstance().getCustomItemsHook();

        if (hook.isItemsAdderItem(item) || hook.isMMOItemsItem(item) || item.hasItemMeta()) {
            Map<String, Object> serializedMap = item.serialize();

            if (includeAmount) {
                serializedMap.put("amount", item.getAmount());
            }
            return serializedMap;
        }

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("material", item.getType().toString());

        if (includeAmount) {
            map.put("amount", item.getAmount());
        }

        return map;
    }

    public String getId() { return id; }
    public ItemStack getOutputItem() { return outputItem.clone(); }
    public String getCategory() { return category; }
    public List<ItemStack> getIngredients() { return ingredients; }
    public Optional<String> getPermission() { return Optional.ofNullable(permission); }
    public double getMoneyRequirement() { return moneyRequirement; }
    public int getPointsRequirement() { return pointsRequirement; }
    public int getForgingLevelRequirement() { return forgingLevelRequirement; } 
    public double getXpReward() { return xpReward; } 
    public int getCooldown() { return cooldown; }
    public int getCraftingTime() { return craftingTime; }
    public int getPriority() { return priority; } 
}
