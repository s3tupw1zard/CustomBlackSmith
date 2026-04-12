package me.allync.horizonblacksmith.utils;

import org.bukkit.ChatColor;

import java.util.List;
import java.util.stream.Collectors;

public class ChatUtil {

    public static String color(String text) {
        if (text == null) {
            return "";
        }
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> color(List<String> list) {
        if (list == null) {
            return null;
        }
        return list.stream().map(ChatUtil::color).collect(Collectors.toList());
    }

    public static String generateProgressBar(long current, long max, int totalBars, String symbol, String completedColor, String notCompletedColor) {
        if (max == 0) return "";
        float percent = (float) current / max;
        int progressBars = (int) (totalBars * percent);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < totalBars; i++) {
            if (i < progressBars) {
                sb.append(ChatUtil.color(completedColor)).append(symbol);
            } else {
                sb.append(ChatUtil.color(notCompletedColor)).append(symbol);
            }
        }
        return sb.toString();
    }
}
