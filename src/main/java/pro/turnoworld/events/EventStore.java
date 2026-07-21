package pro.turnoworld.events;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EventStore {
    private final Path root;
    private final Path players;
    private final Path active;
    private final Path history;
    private final Map<UUID, PlayerEventData> cache = new ConcurrentHashMap<>();

    public EventStore(Path root) throws IOException {
        this.root = root;
        this.players = root.resolve("players");
        this.active = root.resolve("active-event.properties");
        this.history = root.resolve("history.log");
        Files.createDirectories(players);
    }

    public PlayerEventData getOrCreate(UUID uuid, String name) {
        PlayerEventData data = cache.computeIfAbsent(uuid, this::load);
        if (name != null && !name.isBlank()) data.lastName = name;
        data.lastSeen = System.currentTimeMillis();
        return data;
    }

    public PlayerEventData find(String uuidOrName) {
        try { return getOrCreate(UUID.fromString(uuidOrName), null); }
        catch (IllegalArgumentException ignored) { }
        String wanted = uuidOrName.toLowerCase(Locale.ROOT);
        for (PlayerEventData data : knownPlayers()) if (data.lastName.toLowerCase(Locale.ROOT).equals(wanted)) return data;
        return null;
    }

    private PlayerEventData load(UUID uuid) {
        Path file = players.resolve(uuid + ".properties");
        if (!Files.isRegularFile(file)) return new PlayerEventData(uuid, "unknown");
        Properties properties = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            properties.load(reader);
            return PlayerEventData.from(properties);
        } catch (Exception ignored) {
            return new PlayerEventData(uuid, "unknown");
        }
    }

    public synchronized void save(PlayerEventData data) throws IOException {
        Files.createDirectories(players);
        Path target = players.resolve(data.uuid + ".properties");
        Path temporary = players.resolve(data.uuid + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(temporary, StandardCharsets.UTF_8)) {
            data.toProperties().store(writer, "TurnoEvents player data");
        }
        try {
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public synchronized void saveAll() throws IOException {
        for (PlayerEventData data : cache.values()) save(data);
    }

    public Collection<PlayerEventData> knownPlayers() {
        if (Files.isDirectory(players)) try (var files = Files.list(players)) {
            files.filter(p -> p.getFileName().toString().endsWith(".properties")).forEach(path -> {
                String id = path.getFileName().toString().replace(".properties", "");
                try { getOrCreate(UUID.fromString(id), null); } catch (IllegalArgumentException ignored) { }
            });
        } catch (IOException ignored) { }
        return List.copyOf(cache.values());
    }

    public List<PlayerEventData> lifetimeTop(int limit) {
        return knownPlayers().stream().sorted(Comparator.comparingLong((PlayerEventData d) -> d.lifetimePoints).reversed())
                .limit(Math.max(0, limit)).toList();
    }

    public synchronized void saveActive(EventSession session) throws IOException {
        if (session == null || session.state() == EventState.FINISHED || session.state() == EventState.CANCELLED) {
            clearActive(); return;
        }
        Properties p = new Properties();
        p.setProperty("runId", session.runId());
        p.setProperty("template", session.template().id());
        p.setProperty("state", session.state().name());
        p.setProperty("startAt", String.valueOf(session.startAt()));
        p.setProperty("endAt", String.valueOf(session.endAt()));
        p.setProperty("pausedRemaining", String.valueOf(session.pausedRemainingMillis()));
        List<String> scores = new ArrayList<>();
        for (UUID uuid : session.participants()) scores.add(uuid + ":" + session.points(uuid));
        p.setProperty("scores", String.join(",", scores));
        try (BufferedWriter writer = Files.newBufferedWriter(active, StandardCharsets.UTF_8)) {
            p.store(writer, "TurnoEvents active event");
        }
    }

    public synchronized EventSession restoreActive(EventCatalog catalog) {
        if (!Files.isRegularFile(active)) return null;
        Properties p = new Properties();
        try (BufferedReader reader = Files.newBufferedReader(active, StandardCharsets.UTF_8)) {
            p.load(reader);
            EventTemplate template = catalog.get(p.getProperty("template"));
            if (template == null) return null;
            EventState state = EventState.valueOf(p.getProperty("state"));
            EventSession session = new EventSession(p.getProperty("runId"), template, state,
                    Long.parseLong(p.getProperty("startAt", "0")), Long.parseLong(p.getProperty("endAt", "0")));
            session.restorePaused(Long.parseLong(p.getProperty("pausedRemaining", "0")));
            for (String value : p.getProperty("scores", "").split(",")) {
                if (value.isBlank()) continue;
                int delimiter = value.lastIndexOf(':');
                session.setPoints(UUID.fromString(value.substring(0, delimiter)), Long.parseLong(value.substring(delimiter + 1)));
            }
            return session;
        } catch (Exception ignored) { return null; }
    }

    public synchronized void clearActive() throws IOException { Files.deleteIfExists(active); }

    public synchronized void history(EventSession session, List<EventSession.Standing> standings) throws IOException {
        Files.createDirectories(root);
        String winners = standings.stream().limit(3).map(s -> s.name() + "=" + s.points()).reduce((a, b) -> a + "," + b).orElse("-");
        String line = System.currentTimeMillis() + "\t" + session.runId() + "\t" + session.template().id() + "\t" + winners + System.lineSeparator();
        Files.writeString(history, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
    }
}
