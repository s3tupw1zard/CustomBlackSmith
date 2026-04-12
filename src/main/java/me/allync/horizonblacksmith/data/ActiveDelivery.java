package me.allync.horizonblacksmith.data;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ActiveDelivery implements ConfigurationSerializable {

    private final String jobId;
    private final long startTime;
    private final long finishTime;

    public ActiveDelivery(String jobId, long startTime, long finishTime) {
        this.jobId = jobId;
        this.startTime = startTime;
        this.finishTime = finishTime;
    }

    public String getJobId() {
        return jobId;
    }

    public long getStartTime() {
        return startTime;
    }

    public long getFinishTime() {
        return finishTime;
    }

    @NotNull
    @Override
    public Map<String, Object> serialize() {
        Map<String, Object> map = new HashMap<>();
        map.put("jobId", jobId);
        map.put("startTime", startTime);
        map.put("finishTime", finishTime);
        return map;
    }

    public static ActiveDelivery deserialize(Map<String, Object> map) {
        return new ActiveDelivery(
                (String) map.get("jobId"),
                ((Number) map.get("startTime")).longValue(),
                ((Number) map.get("finishTime")).longValue()
        );
    }
}
