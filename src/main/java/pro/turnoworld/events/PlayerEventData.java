package pro.turnoworld.events;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class PlayerEventData {
    public final UUID uuid;
    public String lastName;
    public int wins;
    public int podiums;
    public int eventsJoined;
    public long lifetimePoints;
    public double pendingMoney;
    public final List<String> pendingItems = new ArrayList<>();
    public final Set<String> rewardedRuns = new HashSet<>();
    public long lastSeen;

    public PlayerEventData(UUID uuid, String name) { this.uuid = uuid; this.lastName = name == null ? "unknown" : name; }
    public Properties toProperties() {
        Properties p = new Properties();
        p.setProperty("uuid", uuid.toString()); p.setProperty("lastName", lastName);
        p.setProperty("wins", String.valueOf(wins)); p.setProperty("podiums", String.valueOf(podiums));
        p.setProperty("eventsJoined", String.valueOf(eventsJoined)); p.setProperty("lifetimePoints", String.valueOf(lifetimePoints));
        p.setProperty("pendingMoney", String.valueOf(pendingMoney)); p.setProperty("pendingItems", String.join(",", pendingItems));
        p.setProperty("rewardedRuns", String.join(",", rewardedRuns)); p.setProperty("lastSeen", String.valueOf(lastSeen));
        return p;
    }
    public static PlayerEventData from(Properties p) {
        PlayerEventData d = new PlayerEventData(UUID.fromString(p.getProperty("uuid")), p.getProperty("lastName", "unknown"));
        d.wins = integer(p, "wins"); d.podiums = integer(p, "podiums"); d.eventsJoined = integer(p, "eventsJoined");
        d.lifetimePoints = number(p, "lifetimePoints");
        try { d.pendingMoney = Double.parseDouble(p.getProperty("pendingMoney", "0")); } catch (NumberFormatException ignored) { }
        if (!p.getProperty("pendingItems", "").isBlank()) d.pendingItems.addAll(Arrays.asList(p.getProperty("pendingItems").split(",")));
        if (!p.getProperty("rewardedRuns", "").isBlank()) d.rewardedRuns.addAll(Arrays.asList(p.getProperty("rewardedRuns").split(",")));
        d.lastSeen = number(p, "lastSeen"); return d;
    }
    private static int integer(Properties p, String key) { try { return Integer.parseInt(p.getProperty(key, "0")); } catch (Exception e) { return 0; } }
    private static long number(Properties p, String key) { try { return Long.parseLong(p.getProperty(key, "0")); } catch (Exception e) { return 0; } }
}
