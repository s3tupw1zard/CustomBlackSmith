package me.allync.horizonblacksmith.gui;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import me.allync.horizonblacksmith.data.ActiveDelivery;
import me.allync.horizonblacksmith.data.DeliveryJob;
import me.allync.horizonblacksmith.managers.DeliveryManager;
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
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class DeliveryGUI implements InventoryHolder {
    private final HorizonBlacksmith plugin;
    private final Inventory inventory;
    private final Player player;
    private BukkitTask updateTask;
    private final Map<Integer, DeliveryJob> slotJobMap = new HashMap<>();

    public DeliveryGUI(HorizonBlacksmith plugin, Player player) {
        this.plugin = plugin;
        this.player = player;

        ConfigurationSection guiConfig = plugin.getConfigManager().getConfig().getConfigurationSection("delivery-gui");
        String title = ChatUtil.color(guiConfig.getString("title", "&8Delivery Jobs"));
        int rows = guiConfig.getInt("rows", 5);
        this.inventory = Bukkit.createInventory(this, rows * 9, title);

        loadItems(guiConfig);
        startUpdateTask();
    }

    private void loadItems(ConfigurationSection guiConfig) {
        this.slotJobMap.clear();

        ConfigurationSection staticItems = guiConfig.getConfigurationSection("static-items");
        if (staticItems != null) {
            for (String key : staticItems.getKeys(false)) {
                ConfigurationSection itemConfig = staticItems.getConfigurationSection(key);
                ItemBuilder builder = new ItemBuilder(itemConfig);
                inventory.setItem(itemConfig.getInt("slot"), builder.build());
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

        List<DeliveryJob> availableJobs = plugin.getDeliveryManager().getAllJobs().stream()
                .filter(job -> !job.getPermission().isPresent() || player.hasPermission(job.getPermission().get()))
                .collect(Collectors.toList());

        List<Integer> jobSlots = guiConfig.getIntegerList("job-slots");
        for (int i = 0; i < Math.min(availableJobs.size(), jobSlots.size()); i++) {
            int slot = jobSlots.get(i);
            DeliveryJob job = availableJobs.get(i);
            inventory.setItem(slot, createJobItem(job));
            slotJobMap.put(slot, job);
        }
    }

    private ItemStack createJobItem(DeliveryJob job) {
        ItemBuilder builder = new ItemBuilder(job.getGuiItem()); 
        ConfigurationSection layoutConfig = plugin.getConfigManager().getConfig().getConfigurationSection("delivery-gui.job-item-layout");
        if (layoutConfig == null) return builder.build();

        DeliveryManager dm = plugin.getDeliveryManager();
        Optional<ActiveDelivery> activeDeliveryOpt = dm.getActiveDeliveries(player.getUniqueId()).stream()
                .filter(d -> d.getJobId().equalsIgnoreCase(job.getId()))
                .findFirst();

        List<String> finalLore = new ArrayList<>();
        for (String line : layoutConfig.getStringList("lore")) {
            if (line.contains("{job_original_lore}")) {
                if (job.getGuiItem().hasItemMeta() && job.getGuiItem().getItemMeta().hasLore()) {
                    finalLore.addAll(job.getGuiItem().getItemMeta().getLore());
                }
                continue;
            }
            if (line.contains("{job_requirements}")) {
                job.getRequirements().forEach(req -> finalLore.add(ChatUtil.color("&7- " + req.getAmount() + "x " + getItemName(req))));
                continue;
            }
            if (line.contains("{job_rewards}")) {

                if (job.getRewardMoney() > 0) finalLore.add(ChatUtil.color("&a- " + CurrencyUtil.formatMoney(job.getRewardMoney())));
                if (job.getRewardPoints() > 0) finalLore.add(ChatUtil.color("&b- " + CurrencyUtil.formatPoints(job.getRewardPoints())));
                job.getRewardItems().forEach(item -> finalLore.add(ChatUtil.color("&e- " + item.getAmount() + "x " + getItemName(item))));
                continue;
            }
            if (line.contains("{status}")) {
                if (activeDeliveryOpt.isPresent()) {
                    long remaining = (activeDeliveryOpt.get().getFinishTime() - System.currentTimeMillis()) / 1000;
                    line = line.replace("{status}", ChatUtil.color("&b&lIN PROGRESS &7(" + TimeUtil.formatDuration(remaining) + ")"));
                } else if (dm.canDeliver(player, job, false)) {
                    line = line.replace("{status}", ChatUtil.color("&a&lCLICK TO DELIVER"));
                } else {
                    long remainingCooldown = dm.getRemainingCooldownMillis(player.getUniqueId(), job);
                    if(remainingCooldown > 0) {
                        line = line.replace("{status}", ChatUtil.color("&e&lON COOLDOWN &7(" + TimeUtil.formatDuration(remainingCooldown/1000) + ")"));
                    } else {
                        line = line.replace("{status}", ChatUtil.color("&c&lCANNOT DELIVER"));
                    }
                }
            }
            if (line.contains("{delivery_progress_bar}")) {
                if (activeDeliveryOpt.isPresent()) {
                    ActiveDelivery delivery = activeDeliveryOpt.get();
                    long remainingMillis = delivery.getFinishTime() - System.currentTimeMillis();
                    long totalMillis = (long) job.getDeliveryTime() * 1000;
                    ConfigurationSection barConfig = plugin.getConfigManager().getConfig().getConfigurationSection("delivery-gui.progress-bar");
                    if (barConfig != null && barConfig.getBoolean("enabled")) {
                        String progressBar = ChatUtil.generateProgressBar(
                                totalMillis - remainingMillis, totalMillis,
                                barConfig.getInt("length"), barConfig.getString("symbol"),
                                barConfig.getString("color-full"), barConfig.getString("color-empty")
                        );
                        line = line.replace("{delivery_progress_bar}", progressBar);
                    } else {
                        line = line.replace("{delivery_progress_bar}", "");
                    }
                } else {
                    line = line.replace("{delivery_progress_bar}", "");
                }
            }

            line = line.replace("{time}", TimeUtil.formatDuration(job.getDeliveryTime()))
                    .replace("{cooldown}", TimeUtil.formatDuration(job.getCooldown()));
            finalLore.add(ChatUtil.color(line));
        }

        builder.setLore(finalLore);
        return builder.build();
    }

    private void startUpdateTask() {
        open(player);
        this.updateTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof DeliveryGUI)) {
                    this.cancel();
                    return;
                }
                for (Map.Entry<Integer, DeliveryJob> entry : slotJobMap.entrySet()) {
                    inventory.setItem(entry.getKey(), createJobItem(entry.getValue()));
                }
            }
        }.runTaskTimer(plugin, 20L, 20L);
    }

    public void cancelUpdateTask() {
        if (updateTask != null && !updateTask.isCancelled()) {
            updateTask.cancel();
        }
    }

    private String getItemName(ItemStack item) {
        if (item.hasItemMeta() && item.getItemMeta().hasDisplayName()) {
            return item.getItemMeta().getDisplayName();
        }
        String name = item.getType().toString().replace("_", " ").toLowerCase();
        return name.substring(0, 1).toUpperCase() + name.substring(1);
    }

    public void open(Player player) {
        player.openInventory(inventory);
    }

    public DeliveryJob getJobForSlot(int slot) {
        return slotJobMap.get(slot);
    }

    @NotNull
    @Override
    public Inventory getInventory() {
        return inventory;
    }
}