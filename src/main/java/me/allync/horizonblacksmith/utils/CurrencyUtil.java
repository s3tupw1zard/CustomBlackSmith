package me.allync.horizonblacksmith.utils;

import me.allync.horizonblacksmith.HorizonBlacksmith;
import org.bukkit.configuration.file.FileConfiguration;

public class CurrencyUtil {

    public static String formatMoney(double amount) {
        FileConfiguration config = HorizonBlacksmith.getInstance().getConfig();
        String prefix = config.getString("currency.money.prefix", "$");
        String suffix = config.getString("currency.money.suffix", "");
        return ChatUtil.color(prefix + NumberUtil.format(amount) + suffix);
    }

    public static String formatPoints(int amount) {
        FileConfiguration config = HorizonBlacksmith.getInstance().getConfig();
        String prefix = config.getString("currency.points.prefix", "");
        String suffix = config.getString("currency.points.suffix", " Points");
        return ChatUtil.color(prefix + NumberUtil.format(amount) + suffix);
    }
}