package me.allync.horizonblacksmith.api;

import org.black_ixx.playerpoints.PlayerPoints;
import org.black_ixx.playerpoints.PlayerPointsAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.UUID;

public class PlayerPointsHook {

    private PlayerPointsAPI ppAPI = null;

    public boolean setupPlayerPoints() {
        if (Bukkit.getPluginManager().getPlugin("PlayerPoints") == null) {
            return false;
        }
        ppAPI = PlayerPoints.getInstance().getAPI();
        return ppAPI != null;
    }

    public boolean isEnabled() {
        return ppAPI != null;
    }

    public int getPoints(UUID uuid) {
        if (!isEnabled() || uuid == null) return 0;
        return ppAPI.look(uuid);
    }

    public boolean hasEnough(UUID uuid, int amount) {
        if (!isEnabled() || uuid == null) return true; 
        return ppAPI.look(uuid) >= amount;
    }

    public void takePoints(UUID uuid, int amount) {
        if (isEnabled() && uuid != null) {
            ppAPI.take(uuid, amount);
        }
    }

    public void givePoints(UUID uuid, int amount) {
        if (isEnabled() && uuid != null) {
            ppAPI.give(uuid, amount);
        }
    }

    public int getPoints(Player player) {
        if (player == null) return 0;
        return getPoints(player.getUniqueId());
    }

    public boolean hasEnough(Player player, int amount) {
        if (player == null) return false;
        return hasEnough(player.getUniqueId(), amount);
    }

    public void takePoints(Player player, int amount) {
        if (player == null) return;
        takePoints(player.getUniqueId(), amount);
    }

    public void givePoints(Player player, int amount) {
        if (player == null) return;
        givePoints(player.getUniqueId(), amount);
    }
}

