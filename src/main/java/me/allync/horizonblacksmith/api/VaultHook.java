package me.allync.horizonblacksmith.api;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.UUID;

public class VaultHook {

    private Economy economy = null;

    public boolean setupEconomy() {
        if (Bukkit.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = Bukkit.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    public boolean isEnabled() {
        return economy != null;
    }

    public double getBalance(UUID uuid) {
        if (!isEnabled() || uuid == null) return 0;
        return economy.getBalance(Bukkit.getOfflinePlayer(uuid));
    }

    public boolean hasEnough(UUID uuid, double amount) {
        if (!isEnabled() || uuid == null) return true; 
        return economy.has(Bukkit.getOfflinePlayer(uuid), amount);
    }

    public void takeMoney(UUID uuid, double amount) {
        if (isEnabled() && uuid != null) {
            economy.withdrawPlayer(Bukkit.getOfflinePlayer(uuid), amount);
        }
    }

    public void giveMoney(UUID uuid, double amount) {
        if (isEnabled() && uuid != null) {
            economy.depositPlayer(Bukkit.getOfflinePlayer(uuid), amount);
        }
    }

    public double getBalance(Player player) {
        if (player == null) return 0;
        return getBalance(player.getUniqueId());
    }

    public boolean hasEnough(Player player, double amount) {
        if (player == null) return false;
        return hasEnough(player.getUniqueId(), amount);
    }

    public void takeMoney(Player player, double amount) {
        if (player == null) return;
        takeMoney(player.getUniqueId(), amount);
    }

    public void giveMoney(Player player, double amount) {
        if (player == null) return;
        giveMoney(player.getUniqueId(), amount);
    }
}

