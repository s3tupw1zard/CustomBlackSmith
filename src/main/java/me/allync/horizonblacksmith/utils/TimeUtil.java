package me.allync.horizonblacksmith.utils;

import java.util.concurrent.TimeUnit;

public class TimeUtil {

    public static String formatDuration(long totalSeconds) {
        if (totalSeconds < 0) {
            return "0s";
        }

        long days = TimeUnit.SECONDS.toDays(totalSeconds);
        totalSeconds -= TimeUnit.DAYS.toSeconds(days);
        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        totalSeconds -= TimeUnit.HOURS.toSeconds(hours);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds);
        totalSeconds -= TimeUnit.MINUTES.toSeconds(minutes);
        long seconds = totalSeconds;

        StringBuilder sb = new StringBuilder();
        if (days > 0) {
            sb.append(days).append("d ");
        }
        if (hours > 0) {
            sb.append(hours).append("h ");
        }
        if (minutes > 0) {
            sb.append(minutes).append("m ");
        }
        if (seconds > 0 || sb.length() == 0) {
            sb.append(seconds).append("s");
        }

        return sb.toString().trim();
    }
}
