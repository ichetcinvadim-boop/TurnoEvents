package pro.turnoworld.events;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record EventTemplate(String id, EventType type, String name, boolean enabled, long durationSeconds,
                            long goal, Set<String> worlds, Map<String, Long> points, List<RewardDefinition> rewards) {
    public long pointsFor(String target) {
        if (target == null) return points.getOrDefault("ANY", 0L);
        return points.getOrDefault(target.toUpperCase(), points.getOrDefault("ANY", 0L));
    }
    public boolean worldAllowed(String world) { return worlds.isEmpty() || worlds.contains(world); }
    public RewardDefinition reward(int place) { return rewards.stream().filter(r -> r.place() == place).findFirst().orElse(null); }
}
