package me.allync.horizonblacksmith.listeners;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.ActiveCraft;
import me.allync.horizonblacksmith.data.CraftingRecipe;
import me.allync.horizonblacksmith.data.DeliveryJob;
import me.allync.horizonblacksmith.gui.*;
import me.allync.horizonblacksmith.managers.CraftingManager;
import me.allync.horizonblacksmith.utils.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class GUIListener implements Listener {

    private final HorizonBlacksmith plugin;
    private final Map<UUID, RecipeEditorGUI> editingPlayers = new HashMap<>();
    private final Map<UUID, DeliveryEditorGUI> editingDeliveryPlayers = new HashMap<>();
    private final Map<UUID, String> playerInputMode = new HashMap<>();

    public GUIListener(HorizonBlacksmith plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof PlayerGUI || holder instanceof MyItemsGUI || holder instanceof RecipeDetailGUI ||
                holder instanceof AdminGUI || holder instanceof DeliveryGUI || holder instanceof RecipeEditorGUI ||
                holder instanceof SpeedUpGUI || holder instanceof DeliveryAdminGUI || holder instanceof DeliveryEditorGUI ||
                holder instanceof AdminCategoryGUI || holder instanceof QuantitySelectGUI)) {
            return;
        }

        if (holder instanceof RecipeEditorGUI editor) {
            handleRecipeEditorClick(event, player, editor);
            return;
        }
        if (holder instanceof DeliveryEditorGUI editor) {
            handleDeliveryEditorClick(event, player, editor);
            return;
        }

        event.setCancelled(true);
        if (event.getClickedInventory() == null) return;

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

        if (holder instanceof PlayerGUI gui) {
            handlePlayerGUIClick(player, event.getView().getTitle(), event.getSlot(), event.getClick(), gui);
        } else if (holder instanceof MyItemsGUI gui) {
            handleMyItemsGUIClick(player, gui, event.getSlot(), clickedItem);
        } else if (holder instanceof RecipeDetailGUI gui) {
            handleRecipeDetailGUIClick(player, gui, event.getSlot(), event.getClick());
        } else if (holder instanceof AdminGUI gui) {
            handleAdminGUIClick(player, clickedItem, event.getClick(), gui);
        } else if (holder instanceof AdminCategoryGUI gui) {
            handleAdminCategoryGUIClick(player, event.getSlot(), gui);
        } else if (holder instanceof DeliveryGUI gui) {
            DeliveryJob job = gui.getJobForSlot(event.getSlot());
            if (job != null) {
                plugin.getDeliveryManager().startDelivery(player, job);
            }
        } else if (holder instanceof SpeedUpGUI gui) {
            handleSpeedUpGUIClick(player, gui, event.getSlot());
        } else if (holder instanceof DeliveryAdminGUI gui) {
            handleDeliveryAdminGUIClick(player, clickedItem, event.getClick(), gui);
        } else if (holder instanceof QuantitySelectGUI gui) {
            handleQuantitySelectGUIClick(player, gui, event.getSlot());
        }
    }

    private void handleAdminCategoryGUIClick(Player player, int slot, AdminCategoryGUI gui) {
        String category = gui.getCategoryForSlot(slot);
        if (category != null) {
            new AdminGUI(plugin, player, category).open();
            playSound(player, "gui-click");
        }
    }

    private void handleQuantitySelectGUIClick(Player player, QuantitySelectGUI gui, int slot) {
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("quantity-select-gui");
        if (guiConfig == null) return;

        ConfigurationSection backButtonConfig = guiConfig.getConfigurationSection("back-button");
        if (backButtonConfig != null && slot == backButtonConfig.getInt("slot")) {
            new RecipeDetailGUI(plugin, player, gui.getRecipe());
            playSound(player, "gui-click");
            return;
        }

        int quantity = gui.getQuantityForSlot(slot);
        if (quantity != 0) {
            plugin.getCraftingManager().startCraftMultiple(player, gui.getRecipe(), quantity);
        }
    }

    private void handleDeliveryAdminGUIClick(Player player, ItemStack clickedItem, ClickType clickType, DeliveryAdminGUI currentGui) {
        String displayName = clickedItem.hasItemMeta() ? clickedItem.getItemMeta().getDisplayName() : "";
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("delivery-admin-gui");
        if (guiConfig == null) return;

        if (clickedItem.isSimilar(new ItemBuilder(guiConfig.getConfigurationSection("next-page-button")).build())) {
            currentGui.nextPage();
        } else if (clickedItem.isSimilar(new ItemBuilder(guiConfig.getConfigurationSection("prev-page-button")).build())) {
            currentGui.prevPage();
        } else if (clickedItem.isSimilar(new ItemBuilder(guiConfig.getConfigurationSection("create-button")).build())) {
            new DeliveryEditorGUI(plugin, null).open(player);
        } else {
            if (clickedItem.getItemMeta() == null || clickedItem.getItemMeta().getLore() == null || clickedItem.getItemMeta().getLore().isEmpty()) return;
            String jobIdLine = clickedItem.getItemMeta().getLore().stream().filter(l -> l.contains("ID:")).findFirst().orElse(null);
            if (jobIdLine == null) return;
            String jobId = ChatColor.stripColor(jobIdLine.split(": ")[1]);
            DeliveryJob job = plugin.getDeliveryManager().getJob(jobId);
            if (job == null) return;

            if (clickType.isLeftClick()) {
                new DeliveryEditorGUI(plugin, job).open(player);
            } else if (clickType.isRightClick()) {
                plugin.getDeliveryManager().deleteJob(jobId);
                new DeliveryAdminGUI(plugin, player).open();
            }
        }
    }

    private void handleDeliveryEditorClick(InventoryClickEvent event, Player player, DeliveryEditorGUI editor) {
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        if (clickedInventory == event.getView().getBottomInventory()) {
            event.setCancelled(false);
            return;
        }

        if (clickedInventory == null) {
            return;
        }

        int clickedSlot = event.getSlot();
        boolean isItemSlot = (clickedSlot == DeliveryEditorGUI.GUI_ITEM_SLOT) ||
                DeliveryEditorGUI.REQUIREMENT_SLOTS.contains(clickedSlot) ||
                DeliveryEditorGUI.REWARD_SLOTS.contains(clickedSlot);

        if (isItemSlot) {
            boolean isPlaceholder = currentItem != null && currentItem.hasItemMeta() && (currentItem.getItemMeta().getDisplayName().contains("Slot") || currentItem.getItemMeta().getDisplayName().contains("Item"));

            if (isPlaceholder) {
                if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                    event.setCancelled(true);
                    clickedInventory.setItem(clickedSlot, cursorItem);
                    player.setItemOnCursor(null);
                } else {
                    event.setCancelled(true);
                }
            } else {
                event.setCancelled(false);
            }
        } else {
            event.setCancelled(true);
            handleDeliveryEditorButtonClick(player, clickedSlot, editor);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                editor.updateControlButtons();
            }
        }.runTask(plugin);
    }

    private void handleDeliveryEditorButtonClick(Player player, int clickedSlot, DeliveryEditorGUI editor) {
        switch (clickedSlot) {
            case DeliveryEditorGUI.BACK_SLOT:
                new DeliveryAdminGUI(plugin, player).open();
                break;
            case DeliveryEditorGUI.ID_SLOT:
                if (editor.isEditing()) {
                    player.sendMessage(ChatColor.RED + "You cannot change the ID of an existing job.");
                    return;
                }
                playerInputMode.put(player.getUniqueId(), "DELIVERY_ID");
                editingDeliveryPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "delivery-editor-prompt-id");
                break;
            case DeliveryEditorGUI.MONEY_SLOT:
                playerInputMode.put(player.getUniqueId(), "DELIVERY_MONEY");
                editingDeliveryPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "delivery-editor-prompt-money");
                break;
            case DeliveryEditorGUI.POINTS_SLOT:
                playerInputMode.put(player.getUniqueId(), "DELIVERY_POINTS");
                editingDeliveryPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "delivery-editor-prompt-points");
                break;
            case DeliveryEditorGUI.TIME_SLOT:
                playerInputMode.put(player.getUniqueId(), "DELIVERY_TIME");
                editingDeliveryPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "delivery-editor-prompt-time");
                break;
            case DeliveryEditorGUI.PERMISSION_SLOT:
                playerInputMode.put(player.getUniqueId(), "DELIVERY_PERMISSION");
                editingDeliveryPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "delivery-editor-prompt-permission");
                break;
            case DeliveryEditorGUI.COOLDOWN_SLOT:
                playerInputMode.put(player.getUniqueId(), "DELIVERY_COOLDOWN");
                editingDeliveryPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "delivery-editor-prompt-cooldown");
                break;
            case DeliveryEditorGUI.SAVE_SLOT:
                if (editor.getJobId() == null || editor.getGuiItem() == null || editor.getRequirements().isEmpty()) {
                    plugin.getConfigManager().sendMessage(player, "delivery-save-missing-info");
                    return;
                }
                if (!editor.isEditing() && plugin.getDeliveryManager().getJob(editor.getJobId()) != null) {
                    plugin.getConfigManager().sendMessage(player, "delivery-id-exists", "{id}", editor.getJobId());
                    return;
                }

                DeliveryJob newJob = new DeliveryJob(
                        editor.getJobId(), editor.getGuiItem(), editor.getPermission(),
                        editor.getRequirements(), editor.getRewardItems(), editor.getRewardCommands(),
                        editor.getRewardMoney(), editor.getRewardPoints(),
                        editor.getDeliveryTime(), editor.getCooldown()
                );

                plugin.getDeliveryManager().saveJob(newJob);
                plugin.getConfigManager().reloadDeliveriesConfig();
                plugin.getDeliveryManager().loadDeliveries();
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "delivery-save-success", "{id}", editor.getJobId());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        new DeliveryAdminGUI(plugin, player).open();
                    }
                }.runTask(plugin);
                break;
        }
    }

    private void handleSpeedUpGUIClick(Player player, SpeedUpGUI gui, int slot) {
        String optionId = gui.getOptionId(slot);
        if (optionId != null) {
            plugin.getCraftingManager().speedUpCraft(player, gui.getCraft(), optionId);
        }
    }

    private void handleRecipeDetailGUIClick(Player player, RecipeDetailGUI gui, int slot, ClickType clickType) {
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("recipe-detail-gui");
        if (guiConfig == null) return;

        ConfigurationSection backButtonConfig = guiConfig.getConfigurationSection("back-button");
        if (backButtonConfig != null && slot == backButtonConfig.getInt("slot")) {
            new PlayerGUI(plugin, player).openRecipeMenu(gui.getRecipe().getCategory());
            playSound(player, "gui-click");
            return;
        }

        if (slot == guiConfig.getInt("output-slot")) {
            CraftingRecipe recipe = gui.getRecipe();
            Optional<ActiveCraft> activeCraftOpt = plugin.getCraftingManager().getActiveCrafts(player.getUniqueId()).stream()
                    .filter(ac -> ac.getRecipeId().equalsIgnoreCase(recipe.getId()))
                    .findFirst();

            if (activeCraftOpt.isPresent()) {
                if (clickType.isRightClick()) {
                    if (plugin.getConfigManager().getConfig().getBoolean("craft-speed-up.enabled", false)) {
                        new SpeedUpGUI(plugin, player, activeCraftOpt.get());
                    }
                }
            } else {
                if (clickType.isLeftClick()) {
                    new QuantitySelectGUI(plugin, player, recipe);
                }
            }
        }
    }

    private void handleMyItemsGUIClick(Player player, MyItemsGUI gui, int slot, ItemStack clickedItem) {
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("my-items-gui");
        if (guiConfig == null) return;

        if (slot == guiConfig.getInt("back-button.slot")) {
            new PlayerGUI(plugin, player).openMainMenu();
            playSound(player, "gui-click");
        } else if (slot == guiConfig.getInt("claim-all-button.slot")) {
            plugin.getCraftingManager().claimAllPendingItems(player);
            gui.updateItems();
            playSound(player, "gui-click");
        } else if (slot == guiConfig.getInt("next-page-button.slot")) {
            gui.nextPage();
            playSound(player, "gui-click");
        } else if (slot == guiConfig.getInt("prev-page-button.slot")) {
            gui.prevPage();
            playSound(player, "gui-click");
        } else {
            plugin.getCraftingManager().claimSpecificPendingItem(player, clickedItem);
            gui.updateItems();
        }
    }

    private void handlePlayerGUIClick(Player player, String title, int slot, ClickType click, PlayerGUI gui) {
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("player-gui");
        if (guiConfig == null) return;

        String mainTitle = plugin.getConfigManager().getMessage("player-gui-main-title", false);
        boolean isMainMenu = title.equals(mainTitle);

        if (!isMainMenu) {
            ConfigurationSection backButtonConfig = guiConfig.getConfigurationSection("back-button");
            if (backButtonConfig != null && backButtonConfig.getBoolean("enabled") && slot == backButtonConfig.getInt("slot")) {
                new PlayerGUI(plugin, player).openMainMenu();
                playSound(player, "gui-click");
                return;
            }

            ConfigurationSection nextButtonConfig = guiConfig.getConfigurationSection("next-page-button");
            if (nextButtonConfig != null && slot == nextButtonConfig.getInt("slot")) {
                gui.nextPage();
                playSound(player, "gui-click");
                return;
            }
            ConfigurationSection prevButtonConfig = guiConfig.getConfigurationSection("prev-page-button");
            if (prevButtonConfig != null && slot == prevButtonConfig.getInt("slot")) {
                gui.prevPage();
                playSound(player, "gui-click");
                return;
            }
        }

        if (isMainMenu) {
            ConfigurationSection myItemsButtonConfig = guiConfig.getConfigurationSection("my-items-button");
            if (myItemsButtonConfig != null && myItemsButtonConfig.getBoolean("enabled") && slot == myItemsButtonConfig.getInt("slot")) {
                new MyItemsGUI(plugin, player);
                playSound(player, "gui-click");
                return;
            }

            ConfigurationSection categories = guiConfig.getConfigurationSection("categories");
            if (categories != null) {
                for (String categoryKey : categories.getKeys(false)) {
                    if (categories.getInt(categoryKey + ".slot") == slot) {
                        new PlayerGUI(plugin, player).openRecipeMenu(categoryKey);
                        playSound(player, "gui-click");
                        return;
                    }
                }
            }

            List<Integer> craftSlots = plugin.getConfig().getIntegerList("crafting-slots.gui-slots");
            if (craftSlots.contains(slot)) {
                CraftingManager cm = plugin.getCraftingManager();
                int slotIndex = craftSlots.indexOf(slot);
                List<ActiveCraft> activeCrafts = cm.getActiveCrafts(player.getUniqueId());
                int unlockedSlots = cm.getUnlockedSlots(player.getUniqueId());

                if (slotIndex < activeCrafts.size()) {
                    if (click.isRightClick()) {
                        ActiveCraft craft = activeCrafts.get(slotIndex);
                        new SpeedUpGUI(plugin, player, craft);
                    }
                } else if (slotIndex == unlockedSlots) {
                    cm.unlockNextSlot(player);
                }
            }

        } else {
            CraftingRecipe recipe = gui.getRecipeForSlot(slot);
            if (recipe != null) {

                if (plugin.getCraftingManager().getForgingLevel(player.getUniqueId()) < recipe.getForgingLevelRequirement()) {
                    playSound(player, "locked-click"); 
                    return; 
                }
                new RecipeDetailGUI(plugin, player, recipe);
                playSound(player, "gui-click");
            }
        }
    }

    private void handleRecipeEditorClick(InventoryClickEvent event, Player player, RecipeEditorGUI editor) {
        Inventory clickedInventory = event.getClickedInventory();
        ItemStack cursorItem = event.getCursor();
        ItemStack currentItem = event.getCurrentItem();

        if (clickedInventory == event.getView().getBottomInventory()) {
            event.setCancelled(false);
            return;
        }

        if (clickedInventory == null) {
            return;
        }

        int clickedSlot = event.getSlot();
        boolean isItemSlot = RecipeEditorGUI.INGREDIENT_SLOTS.contains(clickedSlot) || clickedSlot == RecipeEditorGUI.OUTPUT_SLOT;

        if (isItemSlot) {
            boolean isPlaceholder = currentItem != null && currentItem.hasItemMeta() && currentItem.getItemMeta().getDisplayName().contains("Slot");

            if (isPlaceholder) {
                if (cursorItem != null && cursorItem.getType() != Material.AIR) {
                    event.setCancelled(true);
                    clickedInventory.setItem(clickedSlot, cursorItem);
                    player.setItemOnCursor(null);
                } else {
                    event.setCancelled(true);
                }
            } else {
                event.setCancelled(false);
            }
        } else {
            event.setCancelled(true);
            handleEditorButtonClick(player, clickedSlot, editor);
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                editor.updateControlButtons();
            }
        }.runTask(plugin);
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (playerInputMode.containsKey(uuid)) {
            return;
        }

        InventoryHolder holder = event.getInventory().getHolder();
        if (holder instanceof PlayerGUI gui) {
            gui.cancelUpdateTask();
        } else if (holder instanceof RecipeDetailGUI gui) {
            gui.cancelUpdateTask();
        } else if (holder instanceof DeliveryGUI gui) {
            gui.cancelUpdateTask();
        } else if (holder instanceof QuantitySelectGUI) {
        } else if (holder instanceof RecipeEditorGUI) {
            editingPlayers.remove(uuid);
        } else if (holder instanceof DeliveryEditorGUI) {
            editingDeliveryPlayers.remove(uuid);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        editingPlayers.remove(uuid);
        editingDeliveryPlayers.remove(uuid);
        playerInputMode.remove(uuid);

        Inventory topInventory = player.getOpenInventory().getTopInventory();
        if (topInventory != null) {
            InventoryHolder holder = topInventory.getHolder();
            if (holder instanceof PlayerGUI gui) {
                gui.cancelUpdateTask();
            } else if (holder instanceof RecipeDetailGUI gui) {
                gui.cancelUpdateTask();
            } else if (holder instanceof DeliveryGUI gui) {
                gui.cancelUpdateTask();
            }
        }
    }

    private void playSound(Player player, String soundKey) {
        try {
            Sound sound = Sound.valueOf(plugin.getConfig().getString("sounds." + soundKey, "UI_BUTTON_CLICK").toUpperCase());
            player.playSound(player.getLocation(), sound, 1, 1);
        } catch (IllegalArgumentException e) {
            plugin.getLogger().warning("Invalid sound '" + soundKey + "' in config.yml.");
        }
    }

    private void handleAdminGUIClick(Player player, ItemStack clickedItem, ClickType clickType, AdminGUI currentGui) {
        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("admin-recipe-list-gui");
        if (guiConfig == null) return;

        int slot = -1;
        for (String key : guiConfig.getKeys(false)) {
            if (guiConfig.isConfigurationSection(key)) {
                ItemStack buttonItem = new ItemBuilder(guiConfig.getConfigurationSection(key)).build();
                if (buttonItem.isSimilar(clickedItem)) {
                    slot = guiConfig.getInt(key + ".slot");
                    break;
                }
            }
        }

        if (slot == guiConfig.getInt("next-page-button.slot")) {
            currentGui.nextPage();
        } else if (slot == guiConfig.getInt("prev-page-button.slot")) {
            currentGui.prevPage();
        } else if (slot == guiConfig.getInt("create-button.slot")) {
            new RecipeEditorGUI(plugin, currentGui.getCategory()).open(player);
        } else if (slot == guiConfig.getInt("back-button.slot")) {
            new AdminCategoryGUI(plugin, player).open();
        } else {
            if (clickedItem.getItemMeta() == null || clickedItem.getItemMeta().getLore() == null || clickedItem.getItemMeta().getLore().isEmpty()) return;
            String recipeIdLine = clickedItem.getItemMeta().getLore().stream().filter(l -> l.contains("ID:")).findFirst().orElse(null);
            if (recipeIdLine == null) return;
            String recipeId = ChatColor.stripColor(recipeIdLine.split(": ")[1]);
            CraftingRecipe recipe = plugin.getRecipeManager().getRecipe(recipeId);
            if (recipe == null) return;

            if (clickType == ClickType.LEFT) {
                new RecipeEditorGUI(plugin, recipe).open(player);
            } else if (clickType == ClickType.RIGHT) {
                plugin.getRecipeManager().deleteRecipe(recipeId);
                new AdminGUI(plugin, player, currentGui.getCategory()).open();
            }
        }
    }

    private void handleEditorButtonClick(Player player, int clickedSlot, RecipeEditorGUI editor) {
        switch (clickedSlot) {
            case RecipeEditorGUI.BACK_SLOT:
                if(editor.getCategory() != null) {
                    new AdminGUI(plugin, player, editor.getCategory()).open();
                } else {
                    new AdminCategoryGUI(plugin, player).open();
                }
                break;
            case RecipeEditorGUI.ID_SLOT:
                if (editor.isEditing()) {
                    player.sendMessage(ChatColor.RED + "You cannot change the ID of an existing recipe.");
                    return;
                }
                playerInputMode.put(player.getUniqueId(), "ID");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-id");
                break;
            case RecipeEditorGUI.CATEGORY_SLOT:
                playerInputMode.put(player.getUniqueId(), "CATEGORY");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-category");
                break;
            case RecipeEditorGUI.MONEY_SLOT:
                playerInputMode.put(player.getUniqueId(), "MONEY");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-money");
                break;
            case RecipeEditorGUI.POINTS_SLOT:
                playerInputMode.put(player.getUniqueId(), "POINTS");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-points");
                break;
            case RecipeEditorGUI.TIME_SLOT:
                playerInputMode.put(player.getUniqueId(), "TIME");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-time");
                break;
            case RecipeEditorGUI.PERMISSION_SLOT:
                playerInputMode.put(player.getUniqueId(), "PERMISSION");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-permission");
                break;
            case RecipeEditorGUI.COOLDOWN_SLOT:
                playerInputMode.put(player.getUniqueId(), "COOLDOWN");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-cooldown");
                break;
            case RecipeEditorGUI.LEVEL_SLOT:
                playerInputMode.put(player.getUniqueId(), "LEVEL");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-level");
                break;
            case RecipeEditorGUI.XP_SLOT:
                playerInputMode.put(player.getUniqueId(), "XP");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-xp");
                break;
            case RecipeEditorGUI.PRIORITY_SLOT: 
                playerInputMode.put(player.getUniqueId(), "PRIORITY");
                editingPlayers.put(player.getUniqueId(), editor);
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-editor-prompt-priority");
                break;
            case RecipeEditorGUI.SAVE_SLOT:
                String currentCategory = editor.getCategory();
                if (currentCategory == null || currentCategory.isEmpty()) {
                    currentCategory = editor.getOriginalCategory();
                }

                if (editor.getRecipeId() == null || currentCategory == null || editor.getOutputItem() == null || editor.getIngredients().isEmpty()) {
                    plugin.getConfigManager().sendMessage(player, "recipe-save-missing-info");
                    return;
                }
                if (!editor.isEditing() && plugin.getRecipeManager().getRecipe(editor.getRecipeId()) != null) {
                    plugin.getConfigManager().sendMessage(player, "recipe-id-exists", "{id}", editor.getRecipeId());
                    return;
                }
                CraftingRecipe newRecipe = new CraftingRecipe(
                        editor.getRecipeId(), editor.getOutputItem(), currentCategory, editor.getIngredients(),
                        editor.getPermission(), editor.getMoneyRequirement(), editor.getPointsRequirement(),
                        editor.getForgingLevelRequirement(), editor.getXpReward(), editor.getCooldown(), editor.getCraftingTime(),
                        editor.getPriority() 
                );
                plugin.getRecipeManager().saveRecipe(newRecipe);
                plugin.getConfigManager().reloadRecipesConfig();
                plugin.getRecipeManager().loadRecipes();
                player.closeInventory();
                plugin.getConfigManager().sendMessage(player, "recipe-save-success", "{id}", editor.getRecipeId());
                new BukkitRunnable() {
                    @Override
                    public void run() {
                        new AdminGUI(plugin, player, newRecipe.getCategory()).open();
                    }
                }.runTask(plugin);
                break;
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();
        if (!playerInputMode.containsKey(uuid)) return;

        event.setCancelled(true);
        String input = event.getMessage();
        RecipeEditorGUI recipeEditor = editingPlayers.get(uuid);
        DeliveryEditorGUI deliveryEditor = editingDeliveryPlayers.get(uuid);

        if (recipeEditor == null && deliveryEditor == null) {
            playerInputMode.remove(uuid);
            return;
        }

        if (input.equalsIgnoreCase("cancel")) {
            playerInputMode.remove(uuid);
            editingPlayers.remove(uuid);
            editingDeliveryPlayers.remove(uuid);
            plugin.getConfigManager().sendMessage(player, "recipe-editor-input-cancelled");
            new BukkitRunnable() {
                @Override
                public void run() {
                    if (recipeEditor != null) {
                        recipeEditor.open(player);
                    } else if (deliveryEditor != null) {
                        deliveryEditor.open(player);
                    }
                }
            }.runTask(plugin);
            return;
        }

        String mode = playerInputMode.get(uuid);
        try {
            if (recipeEditor != null) {
                switch (mode) {
                    case "ID": recipeEditor.setRecipeId(input.toLowerCase().replaceAll("\\s", "_")); break;
                    case "CATEGORY": recipeEditor.setCategory(input); break;
                    case "MONEY": recipeEditor.setMoneyRequirement(Double.parseDouble(input)); break;
                    case "POINTS": recipeEditor.setPointsRequirement(Integer.parseInt(input)); break;
                    case "TIME": recipeEditor.setCraftingTime(Integer.parseInt(input)); break;
                    case "PERMISSION": recipeEditor.setPermission(input.equalsIgnoreCase("none") || input.equalsIgnoreCase("remove") ? null : input); break;
                    case "COOLDOWN": recipeEditor.setCooldown(Integer.parseInt(input)); break;
                    case "LEVEL": recipeEditor.setForgingLevelRequirement(Integer.parseInt(input)); break;
                    case "XP": recipeEditor.setXpReward(Double.parseDouble(input)); break;
                    case "PRIORITY": recipeEditor.setPriority(Integer.parseInt(input)); break; 
                }
            } else if (deliveryEditor != null) {
                switch (mode) {
                    case "DELIVERY_ID": deliveryEditor.setJobId(input.toLowerCase().replaceAll("\\s", "_")); break;
                    case "DELIVERY_MONEY": deliveryEditor.setRewardMoney(Double.parseDouble(input)); break;
                    case "DELIVERY_POINTS": deliveryEditor.setRewardPoints(Integer.parseInt(input)); break;
                    case "DELIVERY_TIME": deliveryEditor.setDeliveryTime(Integer.parseInt(input)); break;
                    case "DELIVERY_PERMISSION": deliveryEditor.setPermission(input.equalsIgnoreCase("none") || input.equalsIgnoreCase("remove") ? null : input); break;
                    case "DELIVERY_COOLDOWN": deliveryEditor.setCooldown(Integer.parseInt(input)); break;
                }
            }
            playerInputMode.remove(uuid);
        } catch (NumberFormatException e) {
            plugin.getConfigManager().sendMessage(player, "recipe-editor-invalid-number");
        }

        new BukkitRunnable() {
            @Override
            public void run() {
                if (editingPlayers.containsKey(uuid)) {
                    recipeEditor.updateItems();
                    player.openInventory(recipeEditor.getInventory());
                    editingPlayers.remove(uuid);
                } else if (editingDeliveryPlayers.containsKey(uuid)) {
                    deliveryEditor.updateItems();
                    player.openInventory(deliveryEditor.getInventory());
                    editingDeliveryPlayers.remove(uuid);
                }
            }
        }.runTask(plugin);
    }
}
