package pro.turnoworld.events;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
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
    private final List<UUID> manualOrder = new ArrayList<>();
    private final Set<UUID> disqualified = ConcurrentHashMap.newKeySet();

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
    public synchronized List<UUID> manualOrder() { return List.copyOf(manualOrder); }
    public Set<UUID> disqualifiedParticipants() { return Set.copyOf(disqualified); }
    public boolean joined(UUID uuid) { return participants.contains(uuid); }
    public boolean disqualified(UUID uuid) { return disqualified.contains(uuid); }
    public long points(UUID uuid) { return points.getOrDefault(uuid, 0L); }

    public synchronized boolean join(UUID uuid) {
        if (state != EventState.ANNOUNCING && state != EventState.RUNNING && state != EventState.PAUSED) return false;
        points.putIfAbsent(uuid, 0L);
        return participants.add(uuid);
    }
    public synchronized boolean leave(UUID uuid) {
        if (!participants.remove(uuid)) return false;
        manualOrder.remove(uuid); disqualified.remove(uuid); points.remove(uuid);
        return true;
    }
    public synchronized long addPoints(UUID uuid, long amount) {
        if (state != EventState.RUNNING || !participants.contains(uuid) || amount <= 0) return points(uuid);
        return points.merge(uuid, amount, Long::sum);
    }
    public synchronized void setPoints(UUID uuid, long value) {
        participants.add(uuid); points.put(uuid, Math.max(0, value));
    }
    public synchronized boolean assignPlace(UUID uuid, int place, Function<UUID, String> names) {
        if (!participants.contains(uuid) || place < 1) return false;
        disqualified.remove(uuid);
        List<Standing> current = standings(names);
        if (place > current.size()) return false;
        current.removeIf(standing -> standing.uuid().equals(uuid));
        current.add(Math.min(place - 1, current.size()), new Standing(uuid, names.apply(uuid), points(uuid)));
        manualOrder.clear();
        current.forEach(standing -> manualOrder.add(standing.uuid()));
        return true;
    }
    public synchronized boolean disqualify(UUID uuid) {
        if (!participants.contains(uuid) || !disqualified.add(uuid)) return false;
        manualOrder.remove(uuid);
        return true;
    }
    public synchronized boolean reinstate(UUID uuid) {
        if (!participants.contains(uuid) || !disqualified.remove(uuid)) return false;
        if (!manualOrder.isEmpty() && !manualOrder.contains(uuid)) manualOrder.add(uuid);
        return true;
    }
    public synchronized boolean resetPlacements() {
        if (manualOrder.isEmpty() && disqualified.isEmpty()) return false;
        manualOrder.clear(); disqualified.clear(); return true;
    }
    public synchronized void restoreAdjustments(Collection<UUID> order, Collection<UUID> excluded) {
        manualOrder.clear();
        for (UUID uuid : order) if (participants.contains(uuid) && !manualOrder.contains(uuid)) manualOrder.add(uuid);
        disqualified.clear();
        for (UUID uuid : excluded) if (participants.contains(uuid)) disqualified.add(uuid);
        manualOrder.removeIf(disqualified::contains);
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
    public synchronized List<Standing> standings(Function<UUID, String> names) {
        List<Standing> byPoints = new ArrayList<>();
        for (UUID uuid : participants) if (!disqualified.contains(uuid)) byPoints.add(new Standing(uuid, names.apply(uuid), points(uuid)));
        byPoints.sort(Comparator.comparingLong(Standing::points).reversed().thenComparing(s -> s.name().toLowerCase()));
        if (manualOrder.isEmpty()) return byPoints;
        Map<UUID, Standing> available = new HashMap<>();
        byPoints.forEach(standing -> available.put(standing.uuid(), standing));
        List<Standing> result = new ArrayList<>();
        Set<UUID> added = new HashSet<>();
        for (UUID uuid : manualOrder) {
            Standing standing = available.get(uuid);
            if (standing != null && added.add(uuid)) result.add(standing);
        }
        for (Standing standing : byPoints) if (added.add(standing.uuid())) result.add(standing);
        return result;
    }
    public int place(UUID uuid, Function<UUID, String> names) {
        List<Standing> list = standings(names);
        for (int i = 0; i < list.size(); i++) if (list.get(i).uuid().equals(uuid)) return i + 1;
        return 0;
    }
}
