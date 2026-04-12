package me.allync.horizonblacksmith.utils;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Collections;
import java.util.List;

public class ItemBuilder {

    private final ItemStack itemStack;
    private ItemMeta itemMeta;

    public ItemBuilder(Material material) {
        this.itemStack = new ItemStack(material);
        this.itemMeta = itemStack.getItemMeta();
    }

    public ItemBuilder(ItemStack itemStack) {
        if (itemStack == null || itemStack.getType().isAir()) {
            this.itemStack = new ItemStack(Material.BARRIER);
            this.itemMeta = Bukkit.getItemFactory().getItemMeta(Material.BARRIER);
            if (this.itemMeta != null) {
                this.itemMeta.setDisplayName(ChatUtil.color("&cERROR: Null/Air Item"));
                this.itemMeta.setLore(Collections.singletonList(ChatUtil.color("&7An item was not properly loaded.")));
            }
        } else {
            this.itemStack = itemStack.clone();

            if (this.itemStack.hasItemMeta()) {
                this.itemMeta = this.itemStack.getItemMeta();
            } else {
                this.itemMeta = Bukkit.getItemFactory().getItemMeta(this.itemStack.getType());
            }
        }
    }

    public ItemBuilder(ConfigurationSection section) {
        Material material = Material.matchMaterial(section.getString("material", "STONE"));
        if (material == null) {
            material = Material.STONE;
        }
        this.itemStack = new ItemStack(material);
        this.itemMeta = itemStack.getItemMeta();

        if (section.contains("display-name")) {
            setName(section.getString("display-name"));
        }
        if (section.contains("lore")) {
            setLore(section.getStringList("lore"));
        }
        if (section.contains("custom-model-data")) {
            setCustomModelData(section.getInt("custom-model-data"));
        }
        if (section.contains("enchants")) {
            for (String enchantString : section.getStringList("enchants")) {
                String[] parts = enchantString.split(":");
                Enchantment enchantment = Enchantment.getByName(parts[0].toUpperCase());
                int level = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                if (enchantment != null) {
                    addEnchant(enchantment, level, true);
                }
            }
        }
        if (section.contains("flags")) {
            for (String flagString : section.getStringList("flags")) {
                try {
                    ItemFlag flag = ItemFlag.valueOf(flagString.toUpperCase());
                    addItemFlags(flag);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public ItemBuilder setName(String name) {
        itemMeta.setDisplayName(ChatUtil.color(name));
        return this;
    }

    public ItemBuilder setLore(List<String> lore) {
        itemMeta.setLore(ChatUtil.color(lore));
        return this;
    }

    public ItemBuilder setCustomModelData(int data) {
        if (data > 0) {
            itemMeta.setCustomModelData(data);
        }
        return this;
    }

    public ItemBuilder setAmount(int amount) {
        itemStack.setAmount(amount);
        return this;
    }

    public ItemBuilder addEnchant(Enchantment enchantment, int level, boolean ignoreLevelRestriction) {
        itemMeta.addEnchant(enchantment, level, ignoreLevelRestriction);
        return this;
    }

    public ItemBuilder addItemFlags(ItemFlag... flags) {
        itemMeta.addItemFlags(flags);
        return this;
    }

    public ItemStack build() {
        itemStack.setItemMeta(itemMeta);
        return itemStack;
    }

    public static void fromConfig(ItemStack item, ConfigurationSection section) {
        ItemBuilder builder = new ItemBuilder(item);
        if (section.contains("display-name")) {
            builder.setName(section.getString("display-name"));
        }
        if (section.contains("lore")) {
            builder.setLore(section.getStringList("lore"));
        }
        if (section.contains("custom-model-data")) {
            builder.setCustomModelData(section.getInt("custom-model-data"));
        }
        if (section.contains("enchants")) {
            for (String enchantString : section.getStringList("enchants")) {
                String[] parts = enchantString.split(":");
                Enchantment enchantment = Enchantment.getByName(parts[0].toUpperCase());
                int level = parts.length > 1 ? Integer.parseInt(parts[1]) : 1;
                if (enchantment != null) {
                    builder.addEnchant(enchantment, level, true);
                }
            }
        }
        if (section.contains("flags")) {
            for (String flagString : section.getStringList("flags")) {
                try {
                    ItemFlag flag = ItemFlag.valueOf(flagString.toUpperCase());
                    builder.addItemFlags(flag);
                } catch (IllegalArgumentException ignored) {}
            }
        }
        builder.build(); 
    }
}

