package me.allync.horizonblacksmith.managers;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.CraftingRecipe;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class RecipeManager {

    private final HorizonBlacksmith plugin;
    private final Map<String, CraftingRecipe> recipes = new HashMap<>();

    public RecipeManager(HorizonBlacksmith plugin) {
        this.plugin = plugin;
    }

    public void loadRecipes() {
        recipes.clear();
        FileConfiguration config = plugin.getConfigManager().getRecipesConfig();
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section != null) {
                try {
                    CraftingRecipe recipe = new CraftingRecipe(id, section);
                    recipes.put(id.toLowerCase(), recipe);
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load recipe '" + id + "'. Error: " + e.getMessage());
                }
            }
        }
        plugin.getLogger().info("Loaded " + recipes.size() + " crafting recipes.");
    }

    public void saveRecipe(CraftingRecipe recipe) {
        FileConfiguration config = plugin.getConfigManager().getRecipesConfig();
        ConfigurationSection section = config.createSection(recipe.getId());
        recipe.saveTo(section);
        try {
            config.save(plugin.getConfigManager().getRecipesFile());
            recipes.put(recipe.getId().toLowerCase(), recipe);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save recipe to recipes.yml", e);
        }
    }

    public void deleteRecipe(String id) {
        FileConfiguration config = plugin.getConfigManager().getRecipesConfig();
        config.set(id, null);
        try {
            config.save(plugin.getConfigManager().getRecipesFile());
            recipes.remove(id.toLowerCase());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete recipe from recipes.yml", e);
        }
    }

    public CraftingRecipe getRecipe(String id) {
        return recipes.get(id.toLowerCase());
    }

    public CraftingRecipe getRecipeByDisplayItem(ItemStack displayItem) {
        if (displayItem == null || !displayItem.hasItemMeta()) {
            return null;
        }
        for (CraftingRecipe recipe : recipes.values()) {
            if (recipe.getOutputItem().isSimilar(displayItem)) {
                return recipe;
            }
        }
        return null;
    }

    public Collection<CraftingRecipe> getAllRecipes() {
        return recipes.values();
    }

    public static void saveItemStackToSection(ItemStack item, ConfigurationSection section) {
        section.set("material", item.getType().toString());
        section.set("amount", item.getAmount());

        if (item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                section.set("display-name", meta.getDisplayName().replace('\u00A7', '&'));
            }
            if (meta.hasLore()) {
                section.set("lore", meta.getLore().stream()
                        .map(line -> line.replace('\u00A7', '&'))
                        .collect(Collectors.toList()));
            }
            if (meta.hasEnchants()) {
                List<String> enchantList = new ArrayList<>();
                for (Map.Entry<Enchantment, Integer> entry : meta.getEnchants().entrySet()) {
                    enchantList.add(entry.getKey().getName() + ":" + entry.getValue());
                }
                section.set("enchants", enchantList);
            }
            if (!meta.getItemFlags().isEmpty()) {
                section.set("flags", meta.getItemFlags().stream()
                        .map(ItemFlag::name)
                        .collect(Collectors.toList()));
            }
        }
    }
}
