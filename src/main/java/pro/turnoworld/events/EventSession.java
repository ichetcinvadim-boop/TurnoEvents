package pro.turnoworld.events;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class EventSession {
    public record Standing(UUID uuid, String name, long points) { }

    private final String runId;
    private final EventTemplate template;
    private volatile EventState state;
    private volatile long startAt;
    private volatile long endAt;
    private volatile long pausedRemainingMillis;
    private final Set<UUID> participants = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Long> points = new ConcurrentHashMap<>();

    public EventSession(String runId, EventTemplate template, EventState state, long startAt, long endAt) {
        this.runId = runId; this.template = template; this.state = state; this.startAt = startAt; this.endAt = endAt;
    }

    public String runId() { return runId; }
    public EventTemplate template() { return template; }
    public EventState state() { return state; }
    public long startAt() { return startAt; }
    public long endAt() { return endAt; }
    public long pausedRemainingMillis() { return pausedRemainingMillis; }
    public Set<UUID> participants() { return Set.copyOf(participants); }
    public Map<UUID, Long> points() { return Map.copyOf(points); }
    public boolean joined(UUID uuid) { return participants.contains(uuid); }
    public long points(UUID uuid) { return points.getOrDefault(uuid, 0L); }

    public synchronized boolean join(UUID uuid) {
        if (state != EventState.ANNOUNCING && state != EventState.RUNNING && state != EventState.PAUSED) return false;
        points.putIfAbsent(uuid, 0L);
        return participants.add(uuid);
    }
    public synchronized boolean leave(UUID uuid) { return participants.remove(uuid); }
    public synchronized long addPoints(UUID uuid, long amount) {
        if (state != EventState.RUNNING || !participants.contains(uuid) || amount <= 0) return points(uuid);
        return points.merge(uuid, amount, Long::sum);
    }
    public synchronized void setPoints(UUID uuid, long value) {
        participants.add(uuid); points.put(uuid, Math.max(0, value));
    }
    public synchronized void start(long now) {
        state = EventState.RUNNING; startAt = now; endAt = now + template.durationSeconds() * 1000L; pausedRemainingMillis = 0;
    }
    public synchronized boolean pause(long now) {
        if (state != EventState.RUNNING) return false;
        pausedRemainingMillis = Math.max(0, endAt - now); state = EventState.PAUSED; return true;
    }
    public synchronized boolean resume(long now) {
        if (state != EventState.PAUSED) return false;
        endAt = now + pausedRemainingMillis; pausedRemainingMillis = 0; state = EventState.RUNNING; return true;
    }
    public synchronized void extend(long millis) {
        if (state == EventState.PAUSED) pausedRemainingMillis = Math.max(0, pausedRemainingMillis + millis);
        else endAt = Math.max(System.currentTimeMillis(), endAt + millis);
    }
    public synchronized void setRemaining(long millis, long now) {
        if (state == EventState.PAUSED) pausedRemainingMillis = Math.max(0, millis);
        else endAt = now + Math.max(0, millis);
    }
    public synchronized void state(EventState state) { this.state = state; }
    public synchronized void restorePaused(long value) { pausedRemainingMillis = Math.max(0, value); }
    public long remainingSeconds(long now) {
        long value = state == EventState.PAUSED ? pausedRemainingMillis : (state == EventState.ANNOUNCING ? startAt - now : endAt - now);
        return Math.max(0, (value + 999) / 1000);
    }
    public List<Standing> standings(Function<UUID, String> names) {
        List<Standing> result = new ArrayList<>();
        for (UUID uuid : participants) result.add(new Standing(uuid, names.apply(uuid), points(uuid)));
        result.sort(Comparator.comparingLong(Standing::points).reversed().thenComparing(s -> s.name().toLowerCase()));
        return result;
    }
    public int place(UUID uuid, Function<UUID, String> names) {
        List<Standing> list = standings(names);
        for (int i = 0; i < list.size(); i++) if (list.get(i).uuid().equals(uuid)) return i + 1;
        return 0;
    }
}
