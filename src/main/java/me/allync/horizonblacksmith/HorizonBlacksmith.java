package me.allync.horizonblacksmith;

import me.allync.horizonblacksmith.api.CustomItemsHook;
import me.allync.horizonblacksmith.api.PlayerPointsHook;
import me.allync.horizonblacksmith.api.VaultHook;
import me.allync.horizonblacksmith.commands.AdminCommand;
import me.allync.horizonblacksmith.commands.BlacksmithCommand;
import me.allync.horizonblacksmith.commands.DeliveryCommand;
import me.allync.horizonblacksmith.commands.StatusCommand;
import me.allync.horizonblacksmith.data.ActiveCraft;
import me.allync.horizonblacksmith.data.ActiveDelivery;
import me.allync.horizonblacksmith.listeners.GUIListener;
import me.allync.horizonblacksmith.listeners.ItemUsageListener;
import me.allync.horizonblacksmith.managers.ConfigManager;
import me.allync.horizonblacksmith.managers.CraftingManager;
import me.allync.horizonblacksmith.managers.DeliveryManager;
import me.allync.horizonblacksmith.managers.RecipeManager;
import org.bukkit.Bukkit;
import org.bukkit.configuration.serialization.ConfigurationSerialization;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Objects;

public final class HorizonBlacksmith extends JavaPlugin {

    private static HorizonBlacksmith instance;

    private ConfigManager configManager;
    private RecipeManager recipeManager;
    private DeliveryManager deliveryManager;
    private CraftingManager craftingManager;

    private VaultHook vaultHook;
    private PlayerPointsHook playerPointsHook;
    private CustomItemsHook customItemsHook;

    @Override
    public void onEnable() {
        instance = this;
        getLogger().info("CustomBlackSmith is enabling...");

        ConfigurationSerialization.registerClass(ActiveCraft.class);
        ConfigurationSerialization.registerClass(ActiveDelivery.class);

        configManager = new ConfigManager(this);
        recipeManager = new RecipeManager(this);
        craftingManager = new CraftingManager(this);
        deliveryManager = new DeliveryManager(this, craftingManager);

        setupHooks();

        registerCommands();

        getServer().getPluginManager().registerEvents(new GUIListener(this), this);

        getServer().getPluginManager().registerEvents(new ItemUsageListener(this), this);
        getLogger().info("Registered general item usage listener for forge levels.");

        recipeManager.loadRecipes();
        deliveryManager.loadDeliveries();
        craftingManager.loadPlayerData();
        deliveryManager.loadPlayerData();

        getLogger().info("CustomBlackSmith has been enabled successfully!");
    }

    @Override
    public void onDisable() {
        if (craftingManager != null) {
            craftingManager.savePlayerData();
        }
        if (deliveryManager != null) {
            deliveryManager.savePlayerData();
        }
        getLogger().info("CustomBlackSmith has been disabled.");
    }

    private void setupHooks() {
        vaultHook = new VaultHook();
        if (vaultHook.setupEconomy()) {
            getLogger().info("Successfully hooked into Vault.");
        } else {
            getLogger().warning("Vault not found! Economy features will be disabled.");
        }

        playerPointsHook = new PlayerPointsHook();
        if (playerPointsHook.setupPlayerPoints()) {
            getLogger().info("Successfully hooked into PlayerPoints.");
        } else {
            getLogger().warning("PlayerPoints not found! Points features will be disabled.");
        }

        customItemsHook = new CustomItemsHook();
        customItemsHook.setupHooks();
        getLogger().info("Custom item hooks initialized.");
    }

    private void registerCommands() {
        AdminCommand adminCommand = new AdminCommand(this);
        Objects.requireNonNull(getCommand("blacksmith")).setExecutor(new BlacksmithCommand(this));
        Objects.requireNonNull(getCommand("blacksmithadmin")).setExecutor(adminCommand);
        Objects.requireNonNull(getCommand("blacksmithadmin")).setTabCompleter(adminCommand);
        Objects.requireNonNull(getCommand("craftstatus")).setExecutor(new StatusCommand(this));
        Objects.requireNonNull(getCommand("delivery")).setExecutor(new DeliveryCommand(this));
    }

    public static HorizonBlacksmith getInstance() {
        return instance;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public RecipeManager getRecipeManager() {
        return recipeManager;
    }

    public DeliveryManager getDeliveryManager() {
        return deliveryManager;
    }

    public CraftingManager getCraftingManager() {
        return craftingManager;
    }

    public VaultHook getVaultHook() {
        return vaultHook;
    }

    public PlayerPointsHook getPlayerPointsHook() {
        return playerPointsHook;
    }

    public CustomItemsHook getCustomItemsHook() {
        return customItemsHook;
    }
}
