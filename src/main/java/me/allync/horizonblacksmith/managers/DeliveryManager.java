package me.allync.horizonblacksmith.managers;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.ActiveDelivery;
import me.allync.horizonblacksmith.data.DeliveryJob;
import me.allync.horizonblacksmith.utils.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class DeliveryManager {

    private final HorizonBlacksmith plugin;
    private final CraftingManager craftingManager; 
    private final Map<String, DeliveryJob> deliveryJobs = new HashMap<>();
    private final Map<UUID, List<ActiveDelivery>> activeDeliveries = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public DeliveryManager(HorizonBlacksmith plugin, CraftingManager craftingManager) {
        this.plugin = plugin;
        this.craftingManager = craftingManager;
        startDeliveryCheckTask();
    }

    public void loadDeliveries() {
        deliveryJobs.clear();
        FileConfiguration config = plugin.getConfigManager().getDeliveriesConfig();
        for (String id : config.getKeys(false)) {
            ConfigurationSection section = config.getConfigurationSection(id);
            if (section != null) {
                try {
                    deliveryJobs.put(id.toLowerCase(), new DeliveryJob(id, section));
                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load delivery job '" + id + "'. Error: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        plugin.getLogger().info("Loaded " + deliveryJobs.size() + " delivery jobs.");
    }

    public void saveJob(DeliveryJob job) {
        FileConfiguration config = plugin.getConfigManager().getDeliveriesConfig();
        ConfigurationSection section = config.createSection(job.getId());
        job.saveTo(section);
        try {
            config.save(plugin.getConfigManager().getDeliveriesFile());
            deliveryJobs.put(job.getId().toLowerCase(), job); 
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not save job to deliveries.yml", e);
        }
    }

    public void deleteJob(String id) {
        FileConfiguration config = plugin.getConfigManager().getDeliveriesConfig();
        config.set(id, null); 
        try {
            config.save(plugin.getConfigManager().getDeliveriesFile());
            deliveryJobs.remove(id.toLowerCase()); 
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not delete job from deliveries.yml", e);
        }
    }

    public void startDelivery(Player player, DeliveryJob job) {
        if (!canDeliver(player, job, true)) {
            return;
        }

        for (ItemStack required : job.getRequirements()) {
            player.getInventory().removeItem(required);
        }

        long startTime = System.currentTimeMillis();
        long finishTime = startTime + (job.getDeliveryTime() * 1000L);
        ActiveDelivery newDelivery = new ActiveDelivery(job.getId(), startTime, finishTime);
        activeDeliveries.computeIfAbsent(player.getUniqueId(), k -> new ArrayList<>()).add(newDelivery);

        player.closeInventory();
        plugin.getConfigManager().sendMessage(player, "delivery-start",
                "{job}", job.getGuiItem().getItemMeta().getDisplayName(),
                "{time}", TimeUtil.formatDuration(job.getDeliveryTime())
        );
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_WORK_FLETCHER, 1, 1);
    }

    private void checkFinishedDeliveries() {
        long currentTime = System.currentTimeMillis();
        for (Map.Entry<UUID, List<ActiveDelivery>> entry : activeDeliveries.entrySet()) {
            UUID uuid = entry.getKey();

            entry.getValue().removeIf(delivery -> {
                if (currentTime >= delivery.getFinishTime()) {

                    new BukkitRunnable() {
                        @Override
                        public void run() {
                            DeliveryJob job = getJob(delivery.getJobId());
                            if (job != null) {
                                Player player = Bukkit.getPlayer(uuid); 
                                finishDelivery(player, uuid, job);
                            }
                        }
                    }.runTask(plugin);
                    return true; 
                }
                return false;
            });
        }
    }

    private void finishDelivery(Player player, UUID uuid, DeliveryJob job) {

        if (job.getRewardMoney() > 0) plugin.getVaultHook().giveMoney(uuid, job.getRewardMoney());
        if (job.getRewardPoints() > 0) plugin.getPlayerPointsHook().givePoints(uuid, job.getRewardPoints());

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
        job.getRewardCommands().forEach(cmd -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("{player}", offlinePlayer.getName())));

        job.getRewardItems().forEach(item -> craftingManager.addPendingItem(uuid, item));

        setCooldown(uuid, job);

        if (player != null && player.isOnline()) {
            plugin.getConfigManager().sendMessage(player, "delivery-finish", "{job}", job.getGuiItem().getItemMeta().getDisplayName());
            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_VILLAGER_CELEBRATE, 1, 1);
        }
    }

    public boolean canDeliver(Player player, DeliveryJob job, boolean sendMessage) {
        if (job.getPermission().isPresent() && !player.hasPermission(job.getPermission().get())) {
            if (sendMessage) plugin.getConfigManager().sendMessage(player, "no-permission");
            return false;
        }

        if (getActiveDeliveries(player.getUniqueId()).size() >= getMaxSlots(player)) {
            if (sendMessage) plugin.getConfigManager().sendMessage(player, "delivery-max-slots");
            return false;
        }

        if (getRemainingCooldownMillis(player.getUniqueId(), job) > 0) {
            if (sendMessage) {
                String timeRemaining = TimeUtil.formatDuration(getRemainingCooldownMillis(player.getUniqueId(), job) / 1000);
                plugin.getConfigManager().sendMessage(player, "delivery-cooldown-active", "{time}", timeRemaining);
            }
            return false;
        }

        for (ItemStack required : job.getRequirements()) {
            if (!player.getInventory().containsAtLeast(required, required.getAmount())) {
                if (sendMessage) plugin.getConfigManager().sendMessage(player, "delivery-no-items");
                return false;
            }
        }
        return true;
    }

    private void startDeliveryCheckTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                checkFinishedDeliveries();
            }
        }.runTaskTimerAsynchronously(plugin, 40L, 20L);
    }

    private void setCooldown(UUID uuid, DeliveryJob job) {
        if (job.getCooldown() > 0) {
            long cooldownFinish = System.currentTimeMillis() + (job.getCooldown() * 1000L);
            cooldowns.computeIfAbsent(uuid, k -> new HashMap<>()).put(job.getId().toLowerCase(), cooldownFinish);
        }
    }

    public void loadPlayerData() {
        activeDeliveries.clear();
        cooldowns.clear();
        FileConfiguration playerData = craftingManager.getPlayerDataConfig();

        ConfigurationSection deliveriesSection = playerData.getConfigurationSection("active-deliveries");
        if (deliveriesSection != null) {
            for (String uuidString : deliveriesSection.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                List<?> rawList = deliveriesSection.getList(uuidString, new ArrayList<>());
                List<ActiveDelivery> playerDeliveries = rawList.stream()
                        .filter(obj -> obj instanceof ActiveDelivery)
                        .map(obj -> (ActiveDelivery) obj)
                        .collect(Collectors.toList());
                activeDeliveries.put(uuid, new ArrayList<>(playerDeliveries));
            }
        }
        ConfigurationSection cooldownsSection = playerData.getConfigurationSection("delivery-cooldowns");
        if (cooldownsSection != null) {
            for (String uuidString : cooldownsSection.getKeys(false)) {
                UUID uuid = UUID.fromString(uuidString);
                ConfigurationSection playerSection = cooldownsSection.getConfigurationSection(uuidString);
                if (playerSection != null) {
                    Map<String, Long> playerCooldowns = new HashMap<>();
                    for (String jobId : playerSection.getKeys(false)) {
                        playerCooldowns.put(jobId, playerSection.getLong(jobId));
                    }
                    cooldowns.put(uuid, playerCooldowns);
                }
            }
        }
    }

    public void savePlayerData() {
        FileConfiguration playerData = craftingManager.getPlayerDataConfig();
        playerData.set("active-deliveries", null);
        for (Map.Entry<UUID, List<ActiveDelivery>> entry : activeDeliveries.entrySet()) {
            if (!entry.getValue().isEmpty()) {
                playerData.set("active-deliveries." + entry.getKey().toString(), entry.getValue());
            }
        }

        playerData.set("delivery-cooldowns", null);
        for (Map.Entry<UUID, Map<String, Long>> entry : cooldowns.entrySet()) {
            ConfigurationSection playerCooldownSection = playerData.createSection("delivery-cooldowns." + entry.getKey().toString());
            entry.getValue().forEach((jobId, finishTime) -> {
                if (finishTime > System.currentTimeMillis()) {
                    playerCooldownSection.set(jobId, finishTime);
                }
            });
        }
        craftingManager.savePlayerDataFile();
    }

    public int getMaxSlots(Player player) {
        if (player.hasPermission("customblacksmith.delivery.slot.5")) return 5;
        if (player.hasPermission("customblacksmith.delivery.slot.4")) return 4;
        if (player.hasPermission("customblacksmith.delivery.slot.3")) return 3;
        return 2;
    }

    public Collection<DeliveryJob> getAllJobs() {
        return deliveryJobs.values();
    }
    public DeliveryJob getJob(String id) {
        return deliveryJobs.get(id.toLowerCase());
    }
    public List<ActiveDelivery> getActiveDeliveries(UUID uuid) {
        return activeDeliveries.getOrDefault(uuid, Collections.emptyList());
    }
    public long getRemainingCooldownMillis(UUID uuid, DeliveryJob job) {
        long cooldownFinishTime = cooldowns.getOrDefault(uuid, Collections.emptyMap()).getOrDefault(job.getId().toLowerCase(), 0L);
        return Math.max(0, cooldownFinishTime - System.currentTimeMillis());
    }
}

