package me.allync.horizonblacksmith.data;

import org.bukkit.configuration.serialization.ConfigurationSerializable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

public class ActiveCraft implements ConfigurationSerializable {

    private final String recipeId;
    private final long startTime;
    private long finishTime; 

    public ActiveCraft(String recipeId, long startTime, long finishTime) {
        this.recipeId = recipeId;
        this.startTime = startTime;
        this.finishTime = finishTime;
    }

    public void reduceFinishTime(long millisToReduce) {
        this.finishTime -= millisToReduce;
    }

    public String getRecipeId() {
        return recipeId;
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
        map.put("recipeId", recipeId);
        map.put("startTime", startTime);
        map.put("finishTime", finishTime);
        return map;
    }

    public static ActiveCraft deserialize(Map<String, Object> map) {
        return new ActiveCraft(
                (String) map.get("recipeId"),
                ((Number) map.get("startTime")).longValue(),
                ((Number) map.get("finishTime")).longValue()
        );
    }
}

