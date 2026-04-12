package me.allync.horizonblacksmith.managers;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.utils.ChatUtil;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

public class ConfigManager {

    private final HorizonBlacksmith plugin;
    private FileConfiguration config;
    private FileConfiguration deliveriesConfig;
    private File deliveriesFile;
    private FileConfiguration recipesConfig;
    private File recipesFile;

    private String prefix;

    public ConfigManager(HorizonBlacksmith plugin) {
        this.plugin = plugin;
        loadConfigs();
    }

    public void loadConfigs() {

        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
        prefix = config.getString("messages.prefix", "&8[&6BlackSmith&8] ");

        recipesFile = setupFile("recipes.yml");
        recipesConfig = YamlConfiguration.loadConfiguration(recipesFile);
        deliveriesFile = setupFile("deliveries.yml");
        deliveriesConfig = YamlConfiguration.loadConfiguration(deliveriesFile);
    }

    public void reloadRecipesConfig() {
        recipesConfig = YamlConfiguration.loadConfiguration(recipesFile);
    }

    public void reloadDeliveriesConfig() {
        deliveriesConfig = YamlConfiguration.loadConfiguration(deliveriesFile);
    }

    private File setupFile(String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        if (!file.exists()) {
            plugin.saveResource(fileName, false);
        }
        return file;
    }

    public void sendMessage(CommandSender sender, String path, String... replacements) {
        String message = config.getString("messages." + path, "&cMessage not found: messages." + path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        sender.sendMessage(ChatUtil.color(prefix + message));
    }

    public String getMessage(String path, boolean withPrefix, String... replacements) {
        String message = config.getString("messages." + path, "&cMessage not found: messages." + path);
        for (int i = 0; i < replacements.length; i += 2) {
            if (i + 1 < replacements.length) {
                message = message.replace(replacements[i], replacements[i + 1]);
            }
        }
        return ChatUtil.color(withPrefix ? prefix + message : message);
    }

    public String getDeliveryActionForSlot(int slot) {
        ConfigurationSection itemsSection = getConfig().getConfigurationSection("delivery-gui.static-items");
        if (itemsSection == null) return null;

        for (String key : itemsSection.getKeys(false)) {
            ConfigurationSection itemConfig = itemsSection.getConfigurationSection(key);
            if (itemConfig != null && itemConfig.contains("action")) {
                if (itemConfig.getInt("slot") == slot) {
                    return itemConfig.getString("action");
                }
            }
        }
        return null;
    }

    public FileConfiguration getConfig() {
        return config;
    }

    public FileConfiguration getDeliveriesConfig() {
        return deliveriesConfig;
    }

    public File getDeliveriesFile() {
        return deliveriesFile;
    }

    public FileConfiguration getRecipesConfig() {
        return recipesConfig;
    }

    public File getRecipesFile() {
        return recipesFile;
    }
}
