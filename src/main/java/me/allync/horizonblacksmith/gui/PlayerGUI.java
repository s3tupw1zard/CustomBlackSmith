package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.ActiveCraft;
import me.allync.horizonblacksmith.data.CraftingRecipe;
import me.allync.horizonblacksmith.managers.CraftingManager;
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
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class PlayerGUI implements InventoryHolder {

    private final HorizonBlacksmith plugin;
    private final Player player;
    private Inventory inventory;
    private BukkitTask updateTask;
    private final Map<Integer, CraftingRecipe> slotRecipeMap = new HashMap<>();
    private int currentPage = 0;
    private String currentCategory;
    private List<CraftingRecipe> categoryRecipes;

    public PlayerGUI(HorizonBlacksmith plugin, Player player) {
        this.plugin = plugin;
        this.player = player;
    }

    public void openMainMenu() {
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui");
        assert guiConfig != null;
        String title = plugin.getConfigManager().getMessage( "player-gui-main-title", false);
        int rows = guiConfig.getInt("rows", 6);
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
        this.slotRecipeMap.clear();

        if (guiConfig.getBoolean("enable-decorations", true)) {
            ConfigurationSection decorations = guiConfig.getConfigurationSection("decorations");
            if (decorations != null) {
                for (String materialName : decorations.getKeys(false)) {
                    try {
                        Material material = Material.valueOf(materialName.toUpperCase());
                        List<Integer> slots = decorations.getIntegerList(materialName);
                        ItemStack decoItem = new ItemBuilder(material).setName(" ").build();
                        for (int slot : slots) {
                            if (slot >= 0 && slot < inventory.getSize()) {
                                inventory.setItem(slot, decoItem);
                            }
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.getLogger().warning("Invalid material name in player-gui decorations: " + materialName);
                    }
                }
            }
        }

        ConfigurationSection myItemsButtonConfig = guiConfig.getConfigurationSection("my-items-button");
        if (myItemsButtonConfig != null && myItemsButtonConfig.getBoolean("enabled", true)) {
            int amount = plugin.getCraftingManager().getPendingItems(player.getUniqueId()).size();
            ItemBuilder builder = new ItemBuilder(myItemsButtonConfig);
            builder.setAmount(Math.max(1, amount));

            List<String> lore = myItemsButtonConfig.getStringList("lore");
            List<String> processedLore = lore.stream()
                    .map(line -> line.replace("{amount}", String.valueOf(amount)))
                    .collect(Collectors.toList());
            builder.setLore(processedLore);
            inventory.setItem(myItemsButtonConfig.getInt("slot"), builder.build());
        }

        ConfigurationSection levelConfig = plugin.getConfigManager().getConfig().getConfigurationSection("forging-level-system");
        ConfigurationSection levelItemConfig = guiConfig.getConfigurationSection("level-status-item");
        if (levelConfig != null && levelConfig.getBoolean("enabled", false) &&
                levelItemConfig != null && levelItemConfig.getBoolean("enabled", true)) {
            inventory.setItem(levelItemConfig.getInt("slot"), createLevelStatusItem());
        }

        ConfigurationSection categories = guiConfig.getConfigurationSection("categories");
        if (categories != null) {
            for (String key : categories.getKeys(false)) {
                ConfigurationSection catSection = categories.getConfigurationSection(key);
                assert catSection != null;
                ItemStack item = new ItemBuilder(catSection).build();
                inventory.setItem(catSection.getInt("slot"), item);
            }
        }

        drawCraftingQueue();
        player.openInventory(inventory);
        startMainMenuUpdateTask();
    }

    public void openRecipeMenu(String category) {
        this.currentCategory = category;
        this.currentPage = 0;
        this.categoryRecipes = plugin.getRecipeManager().getAllRecipes().stream()
                .filter(r -> r.getCategory().equalsIgnoreCase(category))
                .filter(r -> !r.getPermission().isPresent() || player.hasPermission(r.getPermission().get()))
                .sorted(Comparator.comparingInt(CraftingRecipe::getPriority).reversed()) 
                .collect(Collectors.toList());

        updateRecipePage();
        startRecipeMenuUpdateTask();
    }

    private void updateRecipePage() {
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui");
        assert guiConfig != null;
        String title = plugin.getConfigManager().getMessage("player-gui-category-title", false, "{category}", currentCategory);
        int rows = guiConfig.getInt("rows", 6);
        this.inventory = Bukkit.createInventory(this, rows * 9, title);
        this.slotRecipeMap.clear();

        List<Integer> recipeSlots = guiConfig.getIntegerList("recipe-slots");
        if (recipeSlots.isEmpty()) {
            plugin.getLogger().warning("'player-gui.recipe-slots' is not defined in config.yml! Recipes will not be displayed.");
        }
        final int recipesPerPage = recipeSlots.size();

        ConfigurationSection backButtonConfig = guiConfig.getConfigurationSection("back-button");
        if (backButtonConfig != null && backButtonConfig.getBoolean("enabled", true)) {
            inventory.setItem(backButtonConfig.getInt("slot"), new ItemBuilder(backButtonConfig).build());
        }

        if (currentPage > 0) {
            ConfigurationSection prevButtonConfig = guiConfig.getConfigurationSection("prev-page-button");
            if (prevButtonConfig != null) {
                inventory.setItem(prevButtonConfig.getInt("slot"), new ItemBuilder(prevButtonConfig).build());
            }
        }
        if ((currentPage + 1) * recipesPerPage < categoryRecipes.size()) {
            ConfigurationSection nextButtonConfig = guiConfig.getConfigurationSection("next-page-button");
            if (nextButtonConfig != null) {
                inventory.setItem(nextButtonConfig.getInt("slot"), new ItemBuilder(nextButtonConfig).build());
            }
        }

        int startIndex = currentPage * recipesPerPage;
        for (int i = 0; i < recipesPerPage; i++) {
            int recipeIndex = startIndex + i;
            if (recipeIndex < categoryRecipes.size()) {
                CraftingRecipe recipe = categoryRecipes.get(recipeIndex);
                int slot = recipeSlots.get(i);
                inventory.setItem(slot, createRecipeItem(recipe));
                slotRecipeMap.put(slot, recipe);
            }
        }

        if (guiConfig.getBoolean("enable-decorations", true)) {
            ItemStack filler = new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName(" ").build();
            for (int i = 0; i < inventory.getSize(); i++) {
                if (inventory.getItem(i) == null) {
                    inventory.setItem(i, filler);
                }
            }
        }

        player.openInventory(inventory);
    }

    public void nextPage() {
        List<Integer> recipeSlots = plugin.getConfigManager().getConfig().getIntegerList("player-gui.recipe-slots");
        final int recipesPerPage = recipeSlots.isEmpty() ? 1 : recipeSlots.size(); 

        if ((currentPage + 1) * recipesPerPage < categoryRecipes.size()) {
            currentPage++;
            updateRecipePage();
        }
    }

    public void prevPage() {
        if (currentPage > 0) {
            currentPage--;
            updateRecipePage();
        }
    }

    private void startMainMenuUpdateTask() {
        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof PlayerGUI)
                        || !player.getOpenInventory().getTitle().equals(plugin.getConfigManager().getMessage("player-gui-main-title", false))) {
                    this.cancel();
                    return;
                }
                drawCraftingQueue();

                ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui");
                ConfigurationSection levelConfig = plugin.getConfigManager().getConfig().getConfigurationSection("forging-level-system");
                ConfigurationSection levelItemConfig = guiConfig.getConfigurationSection("level-status-item");
                if (levelConfig != null && levelConfig.getBoolean("enabled", false) &&
                        levelItemConfig != null && levelItemConfig.getBoolean("enabled", true)) {
                    inventory.setItem(levelItemConfig.getInt("slot"), createLevelStatusItem());
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    private void startRecipeMenuUpdateTask() {
        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof PlayerGUI)
                        || player.getOpenInventory().getTitle().equals(plugin.getConfigManager().getMessage("player-gui-main-title", false))) {
                    this.cancel();
                    return;
                }
                for (Map.Entry<Integer, CraftingRecipe> entry : slotRecipeMap.entrySet()) {
                    inventory.setItem(entry.getKey(), createRecipeItem(entry.getValue()));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void cancelUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
    }

    public CraftingRecipe getRecipeForSlot(int slot) {
        return slotRecipeMap.get(slot);
    }

    private void drawCraftingQueue() {
        CraftingManager cm = plugin.getCraftingManager();
        ConfigurationSection slotConfig = plugin.getConfigManager().getConfig().getConfigurationSection("crafting-slots");
        if (slotConfig == null) return;

        List<Integer> guiSlots = slotConfig.getIntegerList("gui-slots");
        List<ActiveCraft> activeCrafts = cm.getActiveCrafts(player.getUniqueId());
        int unlockedSlots = cm.getUnlockedSlots(player.getUniqueId());
        int maxSlots = slotConfig.getInt("max-slots", 8);

        for (int i = 0; i < guiSlots.size(); i++) {
            if (i >= maxSlots) break;

            int guiSlot = guiSlots.get(i);
            ItemStack displayItem;

            if (i < activeCrafts.size()) {
                displayItem = createInProgressItem(activeCrafts.get(i));
            } else if (i < unlockedSlots) {
                displayItem = createEmptySlotItem();
            } else if (i == unlockedSlots) {
                displayItem = createUnlockableSlotItem(i + 1);
            } else {
                displayItem = createLockedSlotItem();
            }
            inventory.setItem(guiSlot, displayItem);
        }
    }

    private ItemStack createLevelStatusItem() {
        ConfigurationSection itemConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui.level-status-item");
        ItemBuilder builder = new ItemBuilder(itemConfig);

        CraftingManager cm = plugin.getCraftingManager();
        int level = cm.getForgingLevel(player.getUniqueId());
        double currentXp = cm.getForgingExp(player.getUniqueId());
        double neededXp = cm.getExpForNextLevel(player.getUniqueId());

        builder.setName(itemConfig.getString("display-name").replace("{level}", String.valueOf(level)));

        String xpBar = "";
        ConfigurationSection barConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui.xp-bar");
        if (barConfig != null && barConfig.getBoolean("enabled")) {
            xpBar = ChatUtil.generateProgressBar((long)currentXp, (long)neededXp,
                    barConfig.getInt("length"), barConfig.getString("symbol"),
                    barConfig.getString("color-full"), barConfig.getString("color-empty")
            );
        }

        List<String> lore = new ArrayList<>();
        for (String line : itemConfig.getStringList("lore")) {
            lore.add(line.replace("{xp_bar}", xpBar)
                    .replace("{current_xp}", NumberUtil.format(currentXp))
                    .replace("{needed_xp}", NumberUtil.format(neededXp)));
        }
        builder.setLore(lore);

        return builder.build();
    }

    private ItemStack createInProgressItem(ActiveCraft craft) {
        CraftingRecipe recipe = plugin.getRecipeManager().getRecipe(craft.getRecipeId());
        if (recipe == null) return new ItemStack(Material.BARRIER);

        ConfigurationSection itemConfig = plugin.getConfigManager().getConfig().getConfigurationSection("crafting-slots.item-display.in-progress");
        ItemBuilder builder = new ItemBuilder(recipe.getOutputItem());

        long remainingMillis = craft.getFinishTime() - System.currentTimeMillis();
        long totalMillis = (long) recipe.getCraftingTime() * 1000;
        String progressBar = "";
        ConfigurationSection barConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui.craft-progress-bar");
        if (barConfig != null && barConfig.getBoolean("enabled")) {
            progressBar = ChatUtil.generateProgressBar(
                    totalMillis - remainingMillis, totalMillis,
                    barConfig.getInt("length"), barConfig.getString("symbol"),
                    barConfig.getString("color-full"), barConfig.getString("color-empty")
            );
        }

        builder.setName(itemConfig.getString("display-name").replace("{item_name}", getItemName(recipe.getOutputItem())));
        List<String> lore = new ArrayList<>();
        for (String line : itemConfig.getStringList("lore")) {
            lore.add(line.replace("{time_left}", TimeUtil.formatDuration(Math.max(0, remainingMillis / 1000)))
                    .replace("{craft_progress_bar}", progressBar));
        }
        builder.setLore(lore);

        return builder.build();
    }

    private ItemStack createEmptySlotItem() {
        return new ItemBuilder(plugin.getConfig().getConfigurationSection("crafting-slots.item-display.empty-slot")).build();
    }

    private ItemStack createLockedSlotItem() {
        return new ItemBuilder(plugin.getConfig().getConfigurationSection("crafting-slots.item-display.locked-slot")).build();
    }

    private ItemStack createUnlockableSlotItem(int slotNumber) {
        ConfigurationSection costSection = plugin.getConfig().getConfigurationSection("crafting-slots.unlock-costs." + slotNumber);
        if (costSection == null) {
            return new ItemBuilder(plugin.getConfig().getConfigurationSection("crafting-slots.item-display.max-slots-locked")).build();
        }

        ConfigurationSection itemConfig = plugin.getConfig().getConfigurationSection("crafting-slots.item-display.unlockable-slot");
        ItemBuilder builder = new ItemBuilder(itemConfig);
        builder.setName(itemConfig.getString("display-name").replace("{slot_number}", String.valueOf(slotNumber)));

        String costLore = "";
        double moneyCost = costSection.getDouble("money");
        int pointsCost = costSection.getInt("points");

        if (moneyCost > 0 && pointsCost > 0) {
            costLore = "&7- &a" + CurrencyUtil.formatMoney(moneyCost) + " &7and &b" + CurrencyUtil.formatPoints(pointsCost);
        } else if (moneyCost > 0) {
            costLore = "&7- &a" + CurrencyUtil.formatMoney(moneyCost);
        } else if (pointsCost > 0) {
            costLore = "&7- &b" + CurrencyUtil.formatPoints(pointsCost);
        }

        List<String> finalLore = new ArrayList<>();
        for (String line : itemConfig.getStringList("lore")) {
            finalLore.add(line.replace("{cost_lore}", costLore));
        }
        builder.setLore(finalLore);

        return builder.build();
    }

    private ItemStack createRecipeItem(CraftingRecipe recipe) {
        int playerLevel = plugin.getCraftingManager().getForgingLevel(player.getUniqueId());

        if (playerLevel < recipe.getForgingLevelRequirement()) {
            ConfigurationSection unknownItemConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui.unknown-recipe-item");
            if (unknownItemConfig == null) return new ItemStack(Material.BARRIER);

            ItemBuilder builder = new ItemBuilder(unknownItemConfig);

            List<String> finalLore = new ArrayList<>();
            for (String line : unknownItemConfig.getStringList("lore")) {
                finalLore.add(line.replace("{level}", String.valueOf(recipe.getForgingLevelRequirement())));
            }
            builder.setLore(finalLore);
            return builder.build();
        } else {

            ItemStack displayItem = recipe.getOutputItem();
            ItemBuilder builder = new ItemBuilder(displayItem);

            ConfigurationSection recipeItemConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui.recipe-item");
            if (recipeItemConfig == null) return displayItem;

            Optional<ActiveCraft> currentCraftOpt = plugin.getCraftingManager()
                    .getActiveCrafts(player.getUniqueId()).stream()
                    .filter(ac -> ac.getRecipeId().equalsIgnoreCase(recipe.getId()))
                    .findFirst();

            List<String> finalLore = new ArrayList<>();
            List<String> loreFormat = recipeItemConfig.getStringList("lore");

            long remainingCooldown = plugin.getCraftingManager().getRemainingCooldownMillis(player.getUniqueId(), recipe);

            for (String line : loreFormat) {
                String processedLine = line.replace("{time}", TimeUtil.formatDuration(recipe.getCraftingTime()))
                        .replace("{cooldown}", TimeUtil.formatDuration(remainingCooldown / 1000));

                if (line.contains("{item_lore}")) {
                    if (displayItem.hasItemMeta() && displayItem.getItemMeta().hasLore()) {
                        finalLore.addAll(Objects.requireNonNull(displayItem.getItemMeta().getLore()));
                    }
                    continue;
                }

                if (processedLine.contains("{level_requirement}")) {
                    if (recipe.getForgingLevelRequirement() > 0) {
                        finalLore.add(plugin.getConfigManager().getMessage("level-requirement-lore", false,
                                "{level}", String.valueOf(recipe.getForgingLevelRequirement())));
                    }
                    continue;
                }

                if (processedLine.contains("{xp_reward}")) {
                    double xpReward = recipe.getXpReward();
                    if (xpReward > 0) {
                        finalLore.add(plugin.getConfigManager().getMessage("xp-reward-lore", false,
                                "{xp}", NumberUtil.format(xpReward)));
                    }
                    continue;
                }

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

                        ConfigurationSection barConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui.craft-progress-bar");
                        if (barConfig != null && barConfig.getBoolean("enabled")) {
                            String progressBar = ChatUtil.generateProgressBar(
                                    totalMillis - remainingMillis, totalMillis,
                                    barConfig.getInt("length"), barConfig.getString("symbol"),
                                    barConfig.getString("color-full"), barConfig.getString("color-empty")
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
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String name = item.getType().toString().replace("_", " ").toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return this.inventory;
    }
}