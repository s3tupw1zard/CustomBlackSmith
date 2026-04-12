package me.allync.horizonblacksmith.managers;

import io.lumine.mythic.lib.api.item.ItemTag;
import io.lumine.mythic.lib.api.item.NBTItem;
import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.ActiveCraft;
import me.allync.horizonblacksmith.data.CraftingRecipe;
import me.allync.horizonblacksmith.gui.PlayerGUI;
import me.allync.horizonblacksmith.utils.ChatUtil;
import me.allync.horizonblacksmith.utils.ItemUtil;
import me.allync.horizonblacksmith.utils.NumberUtil;
import me.allync.horizonblacksmith.utils.TimeUtil;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class CraftingManager {

    private final HorizonBlacksmith plugin;
    private final Map<UUID, List<ActiveCraft>> activeCrafts = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> unlockedSlots = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> forgingLevels = new ConcurrentHashMap<>();
    private final Map<UUID, Double> forgingExp = new ConcurrentHashMap<>();
    private File playerDataFile;
    private FileConfiguration playerDataConfig;

    public CraftingManager(HorizonBlacksmith plugin) {
        this.plugin = plugin;
        setupPlayerData();
        startCraftingCheckTask();
    }

    public void startCraft(Player player, CraftingRecipe recipe) {
        if (!canCraft(player, recipe, true)) {
            return;
        }

        takeIngredients(player, recipe.getIngredients());
        if (recipe.getMoneyRequirement() > 0) {
            plugin.getVaultHook().takeMoney(player, recipe.getMoneyRequirement());
        }
        if (recipe.getPointsRequirement() > 0) {
            plugin.getPlayerPointsHook().takePoints(player, recipe.getPointsRequirement());
        }

        long startTime = System.currentTimeMillis();
        long finishTime = startTime + (recipe.getCraftingTime() * 1000L);
        ActiveCraft newCraft = new ActiveCraft(recipe.getId(), startTime, finishTime);
        activeCrafts.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(newCraft);

        player.closeInventory();
        plugin.getConfigManager().sendMessage(player, "craft-start",
                "{item}", getItemName(recipe.getOutputItem()),
                "{time}", TimeUtil.formatDuration(recipe.getCraftingTime())
        );
        player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.craft-start", "BLOCK_ANVIL_USE")), 1, 1);
    }

    public void startCraftMultiple(Player player, CraftingRecipe recipe, int quantity) {
        int maxAmountToCraft = (quantity == -1) ? getUnlockedSlots(player.getUniqueId()) - getActiveCrafts(player.getUniqueId()).size() : quantity;
        int successfullyStarted = 0;

        if (maxAmountToCraft <= 0) {
            plugin.getConfigManager().sendMessage(player, "craft-max-slots");
            return;
        }

        for (int i = 0; i < maxAmountToCraft; i++) {
            boolean shouldSendMessage = (i == 0);
            if (!canCraft(player, recipe, shouldSendMessage)) {
                break;
            }

            ItemUtil.takeIngredients(player, recipe.getIngredients());
            if (recipe.getMoneyRequirement() > 0) {
                plugin.getVaultHook().takeMoney(player, recipe.getMoneyRequirement());
            }
            if (recipe.getPointsRequirement() > 0) {
                plugin.getPlayerPointsHook().takePoints(player, recipe.getPointsRequirement());
            }

            long startTime = System.currentTimeMillis();
            long finishTime = startTime + (recipe.getCraftingTime() * 1000L);
            ActiveCraft newCraft = new ActiveCraft(recipe.getId(), startTime, finishTime);
            activeCrafts.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(newCraft);
            successfullyStarted++;
        }

        if (successfullyStarted > 0) {
            player.closeInventory();
            if (quantity > 0 && successfullyStarted < quantity) {
                plugin.getConfigManager().sendMessage(player, "craft-start-partial",
                        "{amount}", String.valueOf(successfullyStarted),
                        "{item}", getItemName(recipe.getOutputItem()));
            } else {
                plugin.getConfigManager().sendMessage(player, "craft-start-multiple",
                        "{amount}", String.valueOf(successfullyStarted),
                        "{item}", getItemName(recipe.getOutputItem()));
            }
            player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.craft-start", "BLOCK_ANVIL_USE")), 1, 1);
        }
    }
    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String name = item.getType().toString().replace("_", " ").toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public void speedUpCraft(Player player, ActiveCraft craft, String optionId) {
        ConfigurationSection speedUpConfig = plugin.getConfigManager().getConfig().getConfigurationSection("craft-speed-up");
        if (speedUpConfig == null || !speedUpConfig.getBoolean("enabled", false)) {
            return;
        }

        ConfigurationSection option = plugin.getConfigManager().getConfig().getConfigurationSection("craft-speed-up.options." + optionId);
        if (option == null) {
            plugin.getLogger().warning("Player " + player.getName() + " tried to use an invalid speed-up option: " + optionId);
            return;
        }

        long remainingMillis = Math.max(0, craft.getFinishTime() - System.currentTimeMillis());
        long remainingSeconds = remainingMillis / 1000;
        if (remainingSeconds <= 0) return;

        int pointsCost = 0;
        long millisToReduce = 0;

        if (option.contains("reduce-seconds")) {
            millisToReduce = option.getLong("reduce-seconds") * 1000L;
            pointsCost = option.getInt("points-cost");
        } else if (option.contains("reduce-percentage")) {
            double percentage = option.getDouble("reduce-percentage");
            millisToReduce = (long) (remainingMillis * (percentage / 100.0));
            pointsCost = option.getInt("points-cost");
        } else if (option.contains("points-per-second")) {
            millisToReduce = remainingMillis;
            double costPerSecond = option.getDouble("points-per-second");
            pointsCost = (int) Math.ceil(remainingSeconds * costPerSecond);
        }

        if (pointsCost <= 0) return;

        if (!plugin.getPlayerPointsHook().hasEnough(player, pointsCost)) {
            plugin.getConfigManager().sendMessage(player, "craft-speed-up-no-points", "{cost}", NumberUtil.format(pointsCost));
            return;
        }

        plugin.getPlayerPointsHook().takePoints(player, pointsCost);
        craft.reduceFinishTime(millisToReduce);

        plugin.getConfigManager().sendMessage(player, "craft-speed-up-success", "{time}", TimeUtil.formatDuration(millisToReduce / 1000));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1, 1.5f);
        player.closeInventory();
    }

    public boolean canCraft(Player player, CraftingRecipe recipe) {
        return canCraft(player, recipe, false);
    }

    public boolean canCraft(Player player, CraftingRecipe recipe, boolean sendMessage) {
        if (plugin.getConfigManager().getConfig().getBoolean("forging-level-system.enabled", false)) {
            if (getForgingLevel(player.getUniqueId()) < recipe.getForgingLevelRequirement()) {
                if (sendMessage) plugin.getConfigManager().sendMessage(player, "level-requirement-not-met-craft", "{level}", String.valueOf(recipe.getForgingLevelRequirement()));
                return false;
            }
        }

        if (recipe.getPermission().isPresent() && !player.hasPermission(recipe.getPermission().get())) {
            if (sendMessage) plugin.getConfigManager().sendMessage(player, "craft-no-permission");
            return false;
        }

        if (getActiveCrafts(player.getUniqueId()).size() >= getUnlockedSlots(player.getUniqueId())) {
            if (sendMessage) plugin.getConfigManager().sendMessage(player, "craft-max-slots");
            return false;
        }

        if (getRemainingCooldownMillis(player.getUniqueId(), recipe) > 0) {
            if (sendMessage) {
                String timeRemaining = TimeUtil.formatDuration(getRemainingCooldownMillis(player.getUniqueId(), recipe) / 1000);
                plugin.getConfigManager().sendMessage(player, "cooldown-active", "{time}", timeRemaining);
            }
            return false;
        }

        if (!plugin.getVaultHook().hasEnough(player, recipe.getMoneyRequirement())) {
            if (sendMessage) plugin.getConfigManager().sendMessage(player, "craft-no-money", "{money}", NumberUtil.format(recipe.getMoneyRequirement()));
            return false;
        }
        if (!plugin.getPlayerPointsHook().hasEnough(player, recipe.getPointsRequirement())) {
            if (sendMessage) plugin.getConfigManager().sendMessage(player, "craft-no-points", "{points}", NumberUtil.format(recipe.getPointsRequirement()));
            return false;
        }
        if (!ItemUtil.hasIngredients(player, recipe.getIngredients())) {
            if (sendMessage) plugin.getConfigManager().sendMessage(player, "craft-no-items");
            return false;
        }
        return true;
    }

    private void checkFinishedCrafts() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<UUID, List<ActiveCraft>> entry : activeCrafts.entrySet()) {
            UUID uuid = entry.getKey();

            entry.getValue().removeIf(craft -> {
                if (currentTime >= craft.getFinishTime()) {
                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            CraftingRecipe recipe = plugin.getRecipeManager().getRecipe(craft.getRecipeId());
                            if (recipe != null) {
                                Player player = plugin.getServer().getPlayer(uuid);
                                finishCraft(player, uuid, recipe);
                            }
                        }
                    }.runTask(plugin);
                    return true;
                }
                return false;
            });
        }
    }

    private void finishCraft(Player player, UUID uuid, CraftingRecipe recipe) {
        ItemStack finalItem = recipe.getOutputItem();
        int requiredLevel = recipe.getForgingLevelRequirement();

        if (requiredLevel > 0) {

            NBTItem nbt = NBTItem.get(finalItem);
            nbt.addTag(new ItemTag("HORIZON_FORGE_LEVEL", requiredLevel));
            finalItem = nbt.toItem(); 

            ItemMeta meta = finalItem.getItemMeta();
            if (meta != null) {
                List<String> lore = meta.hasLore() ? new ArrayList<>(meta.getLore()) : new ArrayList<>();

                if (!lore.isEmpty()) {
                    lore.add("");
                }
                String levelLore = plugin.getConfigManager().getMessage("level-requirement-lore", false,
                        "{level}", String.valueOf(requiredLevel));
                lore.add(levelLore);
                meta.setLore(lore);
                finalItem.setItemMeta(meta);
            }
        }

        addPendingItem(uuid, finalItem);
        setCooldown(uuid, recipe);

        if (player != null && player.isOnline()) {
            plugin.getConfigManager().sendMessage(player, "craft-finish", "{item}", getItemName(recipe.getOutputItem()));
            player.playSound(player.getLocation(), org.bukkit.Sound.valueOf(plugin.getConfig().getString("sounds.craft-finish", "ENTITY_PLAYER_LEVELUP")), 1, 1);
        }

        if (plugin.getConfigManager().getConfig().getBoolean("forging-level-system.enabled", false)) {
            double xpToGive = recipe.getXpReward();
            if (xpToGive <= 0) {
                xpToGive = plugin.getConfigManager().getConfig().getDouble("forging-level-system.default-xp-per-craft", 10.0);
            }
            if (xpToGive > 0) {
                addForgingExp(uuid, xpToGive);
                if (player != null && player.isOnline()) {
                    plugin.getConfigManager().sendMessage(player, "craft-exp-gain", "{xp}", NumberUtil.format(xpToGive));
                }
            }
        }
    }

    public void addPendingItem(UUID uuid, ItemStack item) {
        List<ItemStack> pendingItems = getPendingItems(uuid);
        pendingItems.add(item);
        playerDataConfig.set("pending-items." + uuid, pendingItems);
        savePlayerDataFile();
    }

    public List<ItemStack> getPendingItems(UUID uuid) {
        return (List<ItemStack>) playerDataConfig.getList("pending-items." + uuid, new ArrayList<ItemStack>());
    }

    public void claimAllPendingItems(Player player) {
        UUID uuid = player.getUniqueId();
        List<ItemStack> pendingItems = getPendingItems(uuid);
        if (pendingItems.isEmpty()) return;

        List<ItemStack> remainingItems = new ArrayList<>();
        int claimedCount = 0;

        for (ItemStack item : pendingItems) {
            if (player.getInventory().firstEmpty() != -1) {
                player.getInventory().addItem(item);
                claimedCount++;
            } else {
                remainingItems.add(item);
            }
        }

        playerDataConfig.set("pending-items." + uuid, remainingItems);
        savePlayerDataFile();

        if (claimedCount > 0) {
            plugin.getConfigManager().sendMessage(player, "claim-all-success", "{amount}", String.valueOf(claimedCount));
        }
        if (!remainingItems.isEmpty()) {
            plugin.getConfigManager().sendMessage(player, "inventory-full");
        }
    }

    public void claimSpecificPendingItem(Player player, ItemStack clickedItem) {
        UUID uuid = player.getUniqueId();
        List<ItemStack> pendingItems = getPendingItems(uuid);
        if (pendingItems.isEmpty()) return;

        Optional<ItemStack> itemToClaimOpt = pendingItems.stream().filter(clickedItem::isSimilar).findFirst();

        if (itemToClaimOpt.isPresent()) {
            ItemStack itemToClaim = itemToClaimOpt.get();
            if (player.getInventory().firstEmpty() == -1) {
                plugin.getConfigManager().sendMessage(player, "inventory-full");
                return;
            }

            player.getInventory().addItem(itemToClaim);
            pendingItems.remove(itemToClaim);
            playerDataConfig.set("pending-items." + uuid, pendingItems);
            savePlayerDataFile();
            plugin.getConfigManager().sendMessage(player, "claim-item-success", "{item}", getItemName(itemToClaim));
        }
    }

    private void setCooldown(UUID uuid, CraftingRecipe recipe) {
        if (recipe.getCooldown() > 0) {
            long cooldownFinish = System.currentTimeMillis() + (recipe.getCooldown() * 1000L);
            cooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(recipe.getId().toLowerCase(), cooldownFinish);
        }
    }

    private void startCraftingCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkFinishedCrafts();
            }
        }.runTaskTimerAsynchronously(plugin, 40L, 20L);
    }

    public int getUnlockedSlots(UUID uuid) {
        int defaultSlots = plugin.getConfig().getInt("crafting-slots.default-slots", 2);
        return unlockedSlots.getOrDefault(uuid, defaultSlots);
    }

    public void unlockNextSlot(Player player) {
        UUID uuid = player.getUniqueId();
        int currentSlots = getUnlockedSlots(uuid);
        int maxSlots = plugin.getConfig().getInt("crafting-slots.max-slots", 8);

        if (currentSlots >= maxSlots) {
            plugin.getConfigManager().sendMessage(player, "slot-unlock-maxed");
            return;
        }

        int nextSlot = currentSlots + 1;
        ConfigurationSection costSection = plugin.getConfig().getConfigurationSection("crafting-slots.unlock-costs." + nextSlot);
        if (costSection == null) {
            plugin.getLogger().warning("No unlock cost defined for slot #" + nextSlot);
            return;
        }

        double moneyCost = costSection.getDouble("money", 0.0);
        int pointsCost = costSection.getInt("points", 0);

        if (moneyCost > 0 && !plugin.getVaultHook().hasEnough(player, moneyCost)) {
            plugin.getConfigManager().sendMessage(player, "slot-unlock-no-money", "{money}", NumberUtil.format(moneyCost));
            return;
        }
        if (pointsCost > 0 && !plugin.getPlayerPointsHook().hasEnough(player, pointsCost)) {
            plugin.getConfigManager().sendMessage(player, "slot-unlock-no-points", "{points}", NumberUtil.format(pointsCost));
            return;
        }

        if (moneyCost > 0) plugin.getVaultHook().takeMoney(player, moneyCost);
        if (pointsCost > 0) plugin.getPlayerPointsHook().takePoints(player, pointsCost);

        unlockedSlots.put(uuid, nextSlot);
        plugin.getConfigManager().sendMessage(player, "slot-unlock-success", "{slot_number}", String.valueOf(nextSlot));
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 1, 1.2f);

        new PlayerGUI(plugin, player).openMainMenu();
    }

    public boolean hasIngredients(Player player, List<ItemStack> ingredients) {
        return ItemUtil.hasIngredients(player, ingredients);
    }

    public void takeIngredients(Player player, List<ItemStack> ingredients) {
        ItemUtil.takeIngredients(player, ingredients);
    }

    public int getForgingLevel(UUID uuid) {
        return forgingLevels.getOrDefault(uuid, 1);
    }

    public double getForgingExp(UUID uuid) {
        return forgingExp.getOrDefault(uuid, 0.0);
    }

    public double getExpForNextLevel(UUID uuid) {
        int currentLevel = getForgingLevel(uuid);
        ConfigurationSection levelConfig = plugin.getConfigManager().getConfig().getConfigurationSection("forging-level-system");
        if (levelConfig == null) return Double.MAX_VALUE;

        double base = levelConfig.getDouble("base-xp", 100);
        double multiplier = levelConfig.getDouble("xp-multiplier", 1.5);

        return base * Math.pow(currentLevel, multiplier);
    }

    public void addForgingExp(UUID uuid, double amount) {
        double currentExp = getForgingExp(uuid);
        double newExp = currentExp + amount;

        double neededExp = getExpForNextLevel(uuid);

        while (newExp >= neededExp) {
            newExp -= neededExp;
            int newLevel = getForgingLevel(uuid) + 1;
            forgingLevels.put(uuid, newLevel);
            neededExp = getExpForNextLevel(uuid);

            Player player = plugin.getServer().getPlayer(uuid);
            if (player != null && player.isOnline()) {
                plugin.getConfigManager().sendMessage(player, "level-up", "{level}", String.valueOf(newLevel));
                player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1);
            }
        }

        forgingExp.put(uuid, newExp);
    }

    public void setForgingLevel(UUID uuid, int level) {
        if (level < 1) {
            level = 1;
        }
        forgingLevels.put(uuid, level);
        forgingExp.put(uuid, 0.0);
    }

    public void setForgingExp(UUID uuid, double amount) {
        if (amount < 0) {
            amount = 0;
        }
        forgingExp.put(uuid, amount);
    }

    public void giveForgingLevel(UUID uuid, int amount) {
        int currentLevel = getForgingLevel(uuid);
        int newLevel = currentLevel + amount;
        if (newLevel < 1) {
            newLevel = 1;
        }
        forgingLevels.put(uuid, newLevel);
    }

    public List<ActiveCraft> getActiveCrafts(UUID uuid) {
        return activeCrafts.getOrDefault(uuid, Collections.emptyList());
    }

    public long getRemainingCooldownMillis(UUID uuid, CraftingRecipe recipe) {
        long cooldownFinishTime = cooldowns.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(recipe.getId().toLowerCase(), 0L);
        return Math.max(0, cooldownFinishTime - System.currentTimeMillis());
    }

    public long getTotalCooldownMillis(CraftingRecipe recipe) {
        return recipe.getCooldown() * 1000L;
    }

    private void setupPlayerData() {
        playerDataFile = new File(plugin.getDataFolder(), "playerdata.yml");
        if (!playerDataFile.exists()) {
            try {
                playerDataFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not create playerdata.yml", e);
            }
        }
        playerDataConfig = YamlConfiguration.loadConfiguration(playerDataFile);
    }

    public void loadPlayerData() {
        activeCrafts.clear();
        cooldowns.clear();
        unlockedSlots.clear();
        forgingLevels.clear();
        forgingExp.clear();

        ConfigurationSection craftsSection = playerDataConfig.getConfigurationSection("active-crafts");
        if (craftsSection != null) {
            for (String uuidString : craftsSection.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                List<?> rawList = craftsSection.getList(uuidString, new ArrayList<>());
                List<ActiveCraft> playerCrafts = rawList.stream()
                        .filter(obj -> obj instanceof ActiveCraft)
                        .map(obj -> (ActiveCraft) obj)
                        .collect(Collectors.toList());
                activeCrafts.put(uuid, new ArrayList<>(playerCrafts));
            }
        }
        ConfigurationSection cooldownsSection = playerDataConfig.getConfigurationSection("cooldowns");
        if (cooldownsSection != null) {
            for (String uuidString : cooldownsSection.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                ConfigurationSection playerSection = cooldownsSection.getConfigurationSection(uuidString);
                if (playerSection != null) {
                    Map<String, Long> playerCooldowns = new HashMap<>();
                    for (String recipeId : playerSection.getKeys(false)) {
                        playerCooldowns.put(recipeId, playerSection.getLong(recipeId));
                    }
                    cooldowns.put(uuid, playerCooldowns);
                }
            }
        }
        ConfigurationSection slotsSection = playerDataConfig.getConfigurationSection("unlocked-slots");
        if (slotsSection != null) {
            for (String uuidString : slotsSection.getKeys(false)) {
                unlockedSlots.put(UUID.fromString(uuidString), slotsSection.getInt(uuidString));
            }
        }

        ConfigurationSection levelsSection = playerDataConfig.getConfigurationSection("forging-levels");
        if (levelsSection != null) {
            for (String uuidString : levelsSection.getKeys(false)) {
                forgingLevels.put(UUID.fromString(uuidString), levelsSection.getInt(uuidString));
            }
        }
        ConfigurationSection expSection = playerDataConfig.getConfigurationSection("forging-exp");
        if (expSection != null) {
            for (String uuidString : expSection.getKeys(false)) {
                forgingExp.put(UUID.fromString(uuidString), expSection.getDouble(uuidString));
            }
        }

        plugin.getLogger().info("Loaded player crafting data.");
    }

    public void savePlayerData() {
        playerDataConfig.set("active-crafts", null);
        for (Map.Entry<UUID, List<ActiveCraft>> entry : activeCrafts.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                playerDataConfig.set("active-crafts." + entry.getKey().toString(), entry.getValue());
            }
        }

        playerDataConfig.set("cooldowns", null);
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            ConfigurationSection playerCooldownSection = playerDataConfig.createSection("cooldowns." + entry.getKey().toString());
            entry.getValue().forEach((recipeId, finishTime) -> {
                if (finishTime > System.currentTimeMillis()) {
                    playerCooldownSection.set(recipeId, finishTime);
                }
            });
        }

        playerDataConfig.set("unlocked-slots", null);
        unlockedSlots.forEach((uuid, count) -> {
            playerDataConfig.set("unlocked-slots." + uuid.toString(), count);
        });

        playerDataConfig.set("forging-levels", null);
        forgingLevels.forEach((uuid, level) -> {
            playerDataConfig.set("forging-levels." + uuid.toString(), level);
        });
        playerDataConfig.set("forging-exp", null);
        forgingExp.forEach((uuid, exp) -> {
            playerDataConfig.set("forging-exp." + uuid.toString(), exp);
        });

        savePlayerDataFile();
    }

    public void savePlayerDataFile() {
        try {
            playerDataConfig.save(playerDataFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save playerdata.yml", e);
        }
    }
    public FileConfiguration getPlayerDataConfig() {
        return playerDataConfig;
    }

    public File getPlayerDataFile() {
        return playerDataFile;
    }
}
