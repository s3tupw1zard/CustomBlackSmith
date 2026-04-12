package me.allync.horizonblacksmith.data;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.api.CustomItemsHook;
import me.allync.horizonblacksmith.utils.ItemBuilder;
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

public class DeliveryJob {

    private final String id;
    private final ItemStack guiItem;
    private final String permission;
    private final List<ItemStack> requirements;
    private final List<ItemStack> rewardItems;
    private final List<String> rewardCommands;
    private final double rewardMoney;
    private final int rewardPoints;
    private final int deliveryTime; 
    private final int cooldown; 

    public DeliveryJob(String id, ConfigurationSection section) {
        this.id = id;

        ConfigurationSection guiSection = section.getConfigurationSection("gui-item");
        if (guiSection == null) throw new IllegalArgumentException("GUI Item section is missing for delivery " + id);
        this.guiItem = new ItemBuilder(guiSection).build();

        this.permission = section.getString("permission");

        this.requirements = loadItemsFromConfig(section, "requirements");

        ConfigurationSection rewardSection = section.getConfigurationSection("rewards");
        if (rewardSection != null) {
            this.rewardMoney = rewardSection.getDouble("money", 0.0);
            this.rewardPoints = rewardSection.getInt("points", 0);
            this.rewardCommands = rewardSection.getStringList("commands");
            this.rewardItems = loadItemsFromConfig(rewardSection, "items");
        } else {
            this.rewardMoney = 0;
            this.rewardPoints = 0;
            this.rewardCommands = new ArrayList<>();
            this.rewardItems = new ArrayList<>();
        }

        this.deliveryTime = section.getInt("settings.delivery-time", 0);
        this.cooldown = section.getInt("settings.cooldown", 0);
    }

    public DeliveryJob(String id, ItemStack guiItem, String permission, List<ItemStack> requirements,
                       List<ItemStack> rewardItems, List<String> rewardCommands, double rewardMoney,
                       int rewardPoints, int deliveryTime, int cooldown) {
        this.id = id;
        this.guiItem = guiItem;
        this.permission = permission;
        this.requirements = requirements;
        this.rewardItems = rewardItems;
        this.rewardCommands = rewardCommands;
        this.rewardMoney = rewardMoney;
        this.rewardPoints = rewardPoints;
        this.deliveryTime = deliveryTime;
        this.cooldown = cooldown;
    }

    private List<ItemStack> loadItemsFromConfig(ConfigurationSection parentSection, String path) {
        if (!parentSection.contains(path)) return new ArrayList<>();

        List<ItemStack> items = new ArrayList<>();
        List<Map<?, ?>> mapList = parentSection.getMapList(path);

        for (Map<?, ?> map : mapList) {
            @SuppressWarnings("unchecked")
            Map<String, Object> castedMap = new HashMap<>((Map<String, Object>) map); 

            if (castedMap.containsKey("v") || castedMap.containsKey("id") || castedMap.containsKey("DataVersion")) {

                castedMap.remove("amount"); 
                items.add(ItemStack.deserialize(castedMap));
            } else {

                ConfigurationSection itemSection = HorizonBlacksmith.getInstance().getConfig().createSection("temp", castedMap);
                ItemStack item = HorizonBlacksmith.getInstance().getCustomItemsHook().getItemStack(itemSection);
                if (item != null) {
                    item.setAmount(itemSection.getInt("amount", 1));
                    ItemBuilder.fromConfig(item, itemSection);
                    items.add(item);
                }
            }
        }
        return items;
    }

    public void saveTo(ConfigurationSection section) {
        section.set("gui-item", serializeItemStack(guiItem, false));

        if (permission != null && !permission.isEmpty()) {
            section.set("permission", permission);
        }

        if (!requirements.isEmpty()) {
            section.set("requirements", requirements.stream().map(item -> serializeItemStack(item, true)).collect(Collectors.toList()));
        }

        if (rewardMoney > 0) section.set("rewards.money", rewardMoney);
        if (rewardPoints > 0) section.set("rewards.points", rewardPoints);
        if (rewardCommands != null && !rewardCommands.isEmpty()) section.set("rewards.commands", rewardCommands);
        if (!rewardItems.isEmpty()) {
            section.set("rewards.items", rewardItems.stream().map(item -> serializeItemStack(item, true)).collect(Collectors.toList()));
        }

        section.set("settings.delivery-time", deliveryTime);
        section.set("settings.cooldown", cooldown);
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
    public ItemStack getGuiItem() { return guiItem.clone(); }
    public Optional<String> getPermission() { return Optional.ofNullable(permission); }
    public List<ItemStack> getRequirements() { return requirements; }
    public List<ItemStack> getRewardItems() { return rewardItems; }
    public List<String> getRewardCommands() { return rewardCommands; }
    public double getRewardMoney() { return rewardMoney; }
    public int getRewardPoints() { return rewardPoints; }
    public int getDeliveryTime() { return deliveryTime; }
    public int getCooldown() { return cooldown; }
}

