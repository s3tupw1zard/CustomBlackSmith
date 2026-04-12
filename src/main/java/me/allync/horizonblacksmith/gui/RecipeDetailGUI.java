package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.ActiveCraft;
import me.allync.horizonblacksmith.data.CraftingRecipe;
import me.allync.horizonblacksmith.utils.ChatUtil;
import me.allync.horizonblacksmith.utils.ItemBuilder;
import me.allync.horizonblacksmith.utils.NumberUtil;
import me.allync.horizonblacksmith.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RecipeDetailGUI implements InventoryHolder {
    private final HorizonBlacksmith plugin;
    private final Player player;
    private final CraftingRecipe recipe;
    private final Inventory inventory;
    private BukkitTask updateTask;

    public RecipeDetailGUI(HorizonBlacksmith plugin, Player player, CraftingRecipe recipe) {
        this.plugin = plugin;
        this.player = player;
        this.recipe = recipe;

        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("recipe-detail-gui");
        String title = plugin.getConfigManager().getMessage("recipe-detail-gui-title", false);
        int rows = guiConfig != null ? guiConfig.getInt("rows", 5) : 5;
        this.inventory = Bukkit.createInventory(this, rows * 9, title);

        startUpdateTask();
    }

    public void startUpdateTask() {
        updateItems();
        player.openInventory(inventory);
        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof RecipeDetailGUI)) {
                    this.cancel();
                    return;
                }
                ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("recipe-detail-gui");
                if (guiConfig != null) {
                    inventory.setItem(guiConfig.getInt("output-slot"), createCraftButtonItem());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void cancelUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
    }

    public void updateItems() {
        inventory.clear();
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("recipe-detail-gui");
        if (guiConfig == null) {
            player.sendMessage(ChatColor.RED + "Recipe detail GUI is not configured!");
            return;
        }

        List<Integer> ingredientSlots = guiConfig.getIntegerList("ingredient-slots");
        List<ItemStack> ingredients = recipe.getIngredients();
        for (int i = 0; i < Math.min(ingredients.size(), ingredientSlots.size()); i++) {
            inventory.setItem(ingredientSlots.get(i), ingredients.get(i));
        }

        ConfigurationSection backButtonConfig = guiConfig.getConfigurationSection("back-button");
        if (backButtonConfig != null) {
            ItemStack backButton = new ItemBuilder(backButtonConfig).build();
            inventory.setItem(backButtonConfig.getInt("slot"), backButton);
        }
        inventory.setItem(guiConfig.getInt("output-slot"), createCraftButtonItem());

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

    private ItemStack createCraftButtonItem() {
        ItemStack displayItem = recipe.getOutputItem();
        ItemBuilder builder = new ItemBuilder(displayItem);

        ConfigurationSection loreConfig = plugin.getConfigManager().getConfig().getConfigurationSection("recipe-detail-gui");
        if (loreConfig == null) return displayItem;

        List<String> loreFormat = loreConfig.getStringList("output-item-lore");
        List<String> finalLore = new ArrayList<>();

        Optional<ActiveCraft> currentCraftOpt = plugin.getCraftingManager()
                .getActiveCrafts(player.getUniqueId()).stream()
                .filter(ac -> ac.getRecipeId().equalsIgnoreCase(recipe.getId()))
                .findFirst();

        long remainingCooldown = plugin.getCraftingManager().getRemainingCooldownMillis(player.getUniqueId(), recipe);
        long totalCooldown = plugin.getCraftingManager().getTotalCooldownMillis(recipe);
        String cooldownBar = "";
        ConfigurationSection barConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui.cooldown-bar");
        if (barConfig != null && barConfig.getBoolean("enabled", true) && totalCooldown > 0) {
            cooldownBar = ChatUtil.generateProgressBar(
                    totalCooldown - remainingCooldown, totalCooldown, barConfig.getInt("length"),
                    barConfig.getString("symbol"), barConfig.getString("color-full"), barConfig.getString("color-empty")
            );
        }

        String moneyFormat = loreConfig.getString("money-format");
        String pointsFormat = loreConfig.getString("points-format");
        String levelFormat = loreConfig.getString("level-format");
        String xpFormat = loreConfig.getString("xp-format");

        for (String line : loreFormat) {
            String processedLine = line;

            if (processedLine.contains("{item_lore}")) {
                if (displayItem.hasItemMeta() && displayItem.getItemMeta().hasLore()) {
                    finalLore.addAll(Objects.requireNonNull(displayItem.getItemMeta().getLore()));
                }
                continue;
            }
            if (processedLine.contains("{money}")) {
                if (recipe.getMoneyRequirement() > 0 && moneyFormat != null) {
                    finalLore.add(ChatUtil.color(moneyFormat.replace("{money}", NumberUtil.format(recipe.getMoneyRequirement()))));
                }
                continue;
            }
            if (processedLine.contains("{points}")) {
                if (recipe.getPointsRequirement() > 0 && pointsFormat != null) {
                    finalLore.add(ChatUtil.color(pointsFormat.replace("{points}", NumberUtil.format(recipe.getPointsRequirement()))));
                }
                continue;
            }
            if (processedLine.contains("{level}")) {
                if (recipe.getForgingLevelRequirement() > 0 && levelFormat != null) {
                    finalLore.add(ChatUtil.color(levelFormat.replace("{level}", String.valueOf(recipe.getForgingLevelRequirement()))));
                }
                continue;
            }
            if (processedLine.contains("{xp}")) {
                if (recipe.getXpReward() > 0 && xpFormat != null) {
                    finalLore.add(ChatUtil.color(xpFormat.replace("{xp}", NumberUtil.format(recipe.getXpReward()))));
                }
                continue;
            }
            if (processedLine.contains("{speed_up_status}")) {
                if (currentCraftOpt.isPresent() && plugin.getConfigManager().getConfig().getBoolean("craft-speed-up.enabled", false)) {
                    finalLore.add(ChatUtil.color(loreConfig.getString("speed-up-prompt", "&b&lRIGHT-CLICK TO SPEED UP")));
                }
                continue;
            }

            processedLine = processedLine.replace("{time}", TimeUtil.formatDuration(recipe.getCraftingTime()))
                    .replace("{cooldown}", TimeUtil.formatDuration(remainingCooldown / 1000))
                    .replace("{cooldown_bar}", cooldownBar);

            if (processedLine.contains("{status}")) {
                if (currentCraftOpt.isPresent()) {
                    long remainingMillis = currentCraftOpt.get().getFinishTime() - System.currentTimeMillis();
                    processedLine = processedLine.replace("{status}", plugin.getConfigManager().getMessage("craft-status-in-progress", false, "{time}", TimeUtil.formatDuration(remainingMillis / 1000)));
                } else if (plugin.getCraftingManager().canCraft(player, recipe)) {
                    processedLine = processedLine.replace("{status}", plugin.getConfigManager().getMessage("craft-status-can-craft", false));
                } else {
                    processedLine = processedLine.replace("{status}", plugin.getConfigManager().getMessage("craft-status-cannot-craft", false));
                }
            }

            if (processedLine.contains("{craft_progress_bar}")) {
                if (currentCraftOpt.isPresent()) {
                    ActiveCraft craft = currentCraftOpt.get();
                    long remainingMillis = craft.getFinishTime() - System.currentTimeMillis();
                    long totalMillis = (long) recipe.getCraftingTime() * 1000;
                    if (remainingMillis < 0) remainingMillis = 0;

                    ConfigurationSection craftBarConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui.craft-progress-bar");
                    if (craftBarConfig != null && craftBarConfig.getBoolean("enabled")) {
                        String progressBar = ChatUtil.generateProgressBar(
                                totalMillis - remainingMillis, totalMillis,
                                craftBarConfig.getInt("length"), craftBarConfig.getString("symbol"),
                                craftBarConfig.getString("color-full"), craftBarConfig.getString("color-empty")
                        );
                        processedLine = processedLine.replace("{craft_progress_bar}", progressBar);
                    } else {
                        processedLine = processedLine.replace("{craft_progress_bar}", "");
                    }
                } else {
                    processedLine = processedLine.replace("{craft_progress_bar}", "");
                }
            }

            finalLore.add(ChatUtil.color(processedLine));
        }
        builder.setLore(finalLore);
        return builder.build();
    }

    public CraftingRecipe getRecipe() {
        return recipe;
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}