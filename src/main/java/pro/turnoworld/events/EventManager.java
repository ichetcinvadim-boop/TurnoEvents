package pro.turnoworld.events;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EventManager {
    private final TurnoEvents plugin;
    private final EventCatalog catalog;
    private final EventStore store;
    private final EventRewardService rewards;
    private final Random random = new Random();
    private final Set<String> placedLocations = ConcurrentHashMap.newKeySet();
    private final Set<UUID> artificialMobs = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Double> moveRemainders = new ConcurrentHashMap<>();
    private EventSession session;
    private long nextScheduleAt;
    private long resultsUntil;
    private long lastAnnouncement = -1;
    private Question currentQuestion;
    private long nextQuestionAt;
    private boolean questionSolved;

    public EventManager(TurnoEvents plugin, EventCatalog catalog, EventStore store, EventRewardService rewards) {
        this.plugin = plugin; this.catalog = catalog; this.store = store; this.rewards = rewards;
        session = store.restoreActive(catalog);
        nextScheduleAt = System.currentTimeMillis() + plugin.getConfig().getLong("schedule.first-event-delay-minutes", 30) * 60_000L;
        if (session != null) plugin.getLogger().info("Восстановлен ивент " + session.template().id() + " (" + session.state() + ")");
    }

    public synchronized EventSession session() { return session; }
    public synchronized boolean active() { return session != null && session.state() != EventState.FINISHED && session.state() != EventState.CANCELLED; }

    public synchronized void tick() {
        long now = System.currentTimeMillis();
        if (session == null) {
            if (scheduleEnabled() && now >= nextScheduleAt) {
                List<EventTemplate> enabled = new ArrayList<>(catalog.enabled());
                if (!enabled.isEmpty()) start(enabled.get(random.nextInt(enabled.size())).id(), announcementSeconds());
                scheduleNext(now);
            }
            return;
        }
        if (session.state() == EventState.ANNOUNCING) {
            long remaining = session.remainingSeconds(now);
            if (remaining == 0) {
                session.start(now); lastAnnouncement = -1;
                announce("&aИвент &e«" + session.template().name() + "» &aначался! &fУчастие: &e/events join");
                if (session.template().type() == EventType.QUIZ) nextQuestionAt = now + 3_000L;
                saveActive();
            } else if (remaining != lastAnnouncement && (remaining == 60 || remaining == 30 || remaining == 10 || remaining <= 5)) {
                lastAnnouncement = remaining;
                announce("&eИвент &6«" + session.template().name() + "» &eначнётся через &6" + remaining + " сек. &f/events join");
            }
            return;
        }
        if (session.state() == EventState.RUNNING) {
            if (now >= session.endAt()) { finish(); return; }
            if (session.template().type() == EventType.QUIZ && now >= nextQuestionAt) askQuestion(now);
            return;
        }
        if ((session.state() == EventState.FINISHED || session.state() == EventState.CANCELLED) && now >= resultsUntil) session = null;
    }

    private void askQuestion(long now) {
        List<Question> questions = catalog.questions();
        if (questions.isEmpty()) return;
        currentQuestion = questions.get(random.nextInt(questions.size()));
        questionSolved = false;
        nextQuestionAt = now + catalog.questionDelaySeconds() * 1000L;
        announce("&bВопрос: &f" + currentQuestion.text() + " &7(ответ напишите в чат)");
    }

    public synchronized boolean answer(Player player, String answer) {
        if (!isRunning(EventType.QUIZ) || !session.joined(player.getUniqueId()) || currentQuestion == null || questionSolved) return false;
        if (!currentQuestion.matches(answer)) return false;
        questionSolved = true;
        long points = currentQuestion.points();
        session.addPoints(player.getUniqueId(), points);
        nextQuestionAt = System.currentTimeMillis() + 3_000L;
        announce("&a" + player.getName() + " первым ответил правильно и получил &e" + points + " очков!");
        saveActive();
        return true;
    }

    public synchronized boolean wouldAnswer(Player player, String answer) {
        return isRunning(EventType.QUIZ) && session.joined(player.getUniqueId()) && currentQuestion != null && !questionSolved && currentQuestion.matches(answer);
    }

    public synchronized boolean start(String templateId, long delaySeconds) {
        if (active()) return false;
        EventTemplate template = catalog.get(templateId);
        if (template == null || !template.enabled()) return false;
        long now = System.currentTimeMillis();
        long starts = now + Math.max(0, delaySeconds) * 1000L;
        session = new EventSession(UUID.randomUUID().toString(), template,
                delaySeconds <= 0 ? EventState.RUNNING : EventState.ANNOUNCING, starts,
                starts + template.durationSeconds() * 1000L);
        resetRunState();
        if (delaySeconds <= 0) {
            session.start(now);
            announce("&aИвент &e«" + template.name() + "» &aначался! &f/events join");
            if (template.type() == EventType.QUIZ) nextQuestionAt = now + 3_000L;
        } else announce("&eОбъявлен ивент &6«" + template.name() + "»&e. Начало через &6" + delaySeconds + " сек.");
        saveActive();
        return true;
    }

    private void resetRunState() {
        placedLocations.clear(); artificialMobs.clear(); moveRemainders.clear(); currentQuestion = null;
        questionSolved = false; lastAnnouncement = -1; resultsUntil = 0;
    }

    public synchronized boolean finish() {
        if (session == null || session.state() == EventState.FINISHED || session.state() == EventState.CANCELLED) return false;
        session.state(EventState.FINISHED);
        List<EventSession.Standing> standings = standings();
        for (int i = 0; i < standings.size(); i++) {
            EventSession.Standing standing = standings.get(i);
            PlayerEventData data = store.getOrCreate(standing.uuid(), standing.name());
            rewards.queue(data, session, i + 1);
            try { store.save(data); } catch (IOException e) { plugin.getLogger().warning("Не удалось записать ledger награды: " + e.getMessage()); }
            Player online = Bukkit.getPlayer(standing.uuid());
            if (online != null) for (String line : rewards.claim(online, data)) online.sendMessage(plugin.color(plugin.prefix() + line));
            try { store.save(data); } catch (IOException e) { plugin.getLogger().warning("Не удалось сохранить награду: " + e.getMessage()); }
        }
        announce("&6Ивент &e«" + session.template().name() + "» &6завершён!");
        for (int i = 0; i < Math.min(3, standings.size()); i++) {
            EventSession.Standing s = standings.get(i);
            announce("&e#" + (i + 1) + " &f" + s.name() + " &7— &6" + s.points() + " " + session.template().type().scoreLabel());
        }
        try { store.history(session, standings); store.clearActive(); } catch (IOException e) { plugin.getLogger().warning(e.getMessage()); }
        resultsUntil = System.currentTimeMillis() + plugin.getConfig().getLong("results-display-seconds", 30) * 1000L;
        scheduleNext(System.currentTimeMillis());
        return true;
    }

    public synchronized boolean cancel() {
        if (!active()) return false;
        session.state(EventState.CANCELLED);
        announce("&cИвент &e«" + session.template().name() + "» &cотменён без наград.");
        try { store.clearActive(); } catch (IOException ignored) { }
        resultsUntil = System.currentTimeMillis() + 5_000L;
        scheduleNext(System.currentTimeMillis());
        return true;
    }

    public synchronized boolean pause() { boolean changed = active() && session.pause(System.currentTimeMillis()); if (changed) saveActive(); return changed; }
    public synchronized boolean resume() { boolean changed = active() && session.resume(System.currentTimeMillis()); if (changed) saveActive(); return changed; }
    public synchronized boolean extend(long seconds) { if (!active()) return false; session.extend(seconds * 1000L); saveActive(); return true; }
    public synchronized boolean setRemaining(long seconds) { if (!active()) return false; session.setRemaining(seconds * 1000L, System.currentTimeMillis()); saveActive(); return true; }

    public synchronized boolean join(UUID uuid, String name) {
        if (!active() || !session.join(uuid)) return false;
        store.getOrCreate(uuid, name); saveActive(); return true;
    }
    public synchronized boolean leave(UUID uuid) { boolean result = active() && session.leave(uuid); if (result) saveActive(); return result; }
    public synchronized long addPoints(UUID uuid, long points) {
        if (!active() || !session.joined(uuid) || points < 0) return -1;
        long result = session.points(uuid) + points;
        session.setPoints(uuid, result); saveActive(); return result;
    }
    public synchronized boolean setPoints(UUID uuid, long points, String name) {
        if (!active()) return false; store.getOrCreate(uuid, name); session.setPoints(uuid, points); saveActive(); return true;
    }
    public synchronized boolean assignPlace(UUID uuid, int place) {
        if (!active() || !session.assignPlace(uuid, place, this::name)) return false;
        saveActive(); return true;
    }
    public synchronized boolean disqualify(UUID uuid) {
        if (!active() || !session.disqualify(uuid)) return false;
        saveActive(); return true;
    }
    public synchronized boolean reinstate(UUID uuid) {
        if (!active() || !session.reinstate(uuid)) return false;
        saveActive(); return true;
    }
    public synchronized boolean resetPlacements() {
        if (!active() || !session.resetPlacements()) return false;
        saveActive(); return true;
    }

    public void markArtificial(UUID entity) { artificialMobs.add(entity); }
    public void placed(Location location) { if (active()) placedLocations.add(locationKey(location)); }
    public void miner(Player player, Location location, String material) {
        if (placedLocations.contains(locationKey(location))) return;
        score(player, EventType.MINER, material, 1);
    }
    public void builder(Player player, Location location, String material) {
        String key = locationKey(location);
        boolean unique = placedLocations.add(key);
        if (!plugin.getConfig().getBoolean("anti-exploit.unique-builder-locations", true) || unique) score(player, EventType.BUILDER, material, 1);
    }
    public void hunter(Player player, UUID entity, String type) {
        if (artificialMobs.remove(entity) && plugin.getConfig().getBoolean("anti-exploit.ignore-spawner-and-egg-mobs", true)) return;
        score(player, EventType.HUNTER, type, 1);
    }
    public void fisher(Player player, String material) { score(player, EventType.FISHER, material, 1); }
    public void move(Player player, double distance) {
        if (distance <= 0 || distance > 20 || !isEligible(player, EventType.MARATHON)) return;
        if (plugin.getConfig().getBoolean("anti-exploit.ignore-flying-gliding-vehicles", true) &&
                (player.isFlying() || player.isGliding() || player.isInsideVehicle())) return;
        double total = moveRemainders.getOrDefault(player.getUniqueId(), 0D) + distance;
        long blocks = (long) Math.floor(total);
        moveRemainders.put(player.getUniqueId(), total - blocks);
        if (blocks > 0) session.addPoints(player.getUniqueId(), blocks);
    }

    private void score(Player player, EventType type, String target, long amount) {
        if (!isEligible(player, type)) return;
        long value = session.template().pointsFor(target) * amount;
        if (value > 0) session.addPoints(player.getUniqueId(), value);
    }
    private boolean isEligible(Player player, EventType type) {
        EventSession current = session;
        if (current == null || current.state() != EventState.RUNNING || current.template().type() != type || !current.joined(player.getUniqueId())) return false;
        if (!current.template().worldAllowed(player.getWorld().getName())) return false;
        return !plugin.getConfig().getBoolean("anti-exploit.ignore-creative", true) || player.getGameMode() != org.bukkit.GameMode.CREATIVE;
    }
    private boolean isRunning(EventType type) { return session != null && session.state() == EventState.RUNNING && session.template().type() == type; }

    public synchronized List<EventSession.Standing> standings() { return session == null ? Collections.emptyList() : session.standings(this::name); }
    public synchronized int place(UUID uuid) { return session == null ? 0 : session.place(uuid, this::name); }
    public String name(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        return offline.getName() == null ? uuid.toString().substring(0, 8) : offline.getName();
    }
    private String locationKey(Location l) { return l.getWorld().getUID() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ(); }

    public boolean scheduleEnabled() { return plugin.getConfig().getBoolean("schedule.enabled", true); }
    public void scheduleEnabled(boolean value) { plugin.getConfig().set("schedule.enabled", value); plugin.saveConfig(); if (value) scheduleNext(System.currentTimeMillis()); }
    public long nextScheduleAt() { return nextScheduleAt; }
    private long announcementSeconds() { return Math.max(0, plugin.getConfig().getLong("schedule.announcement-seconds", 60)); }
    private void scheduleNext(long now) { nextScheduleAt = now + Math.max(1, plugin.getConfig().getLong("schedule.interval-minutes", 180)) * 60_000L; }
    public String nextTime() { return formatSeconds(Math.max(0, (nextScheduleAt - System.currentTimeMillis() + 999) / 1000)); }
    public static String formatSeconds(long seconds) {
        Duration d = Duration.ofSeconds(Math.max(0, seconds));
        long hours = d.toHours(); long minutes = d.minusHours(hours).toMinutes(); long secs = d.minusHours(hours).minusMinutes(minutes).toSeconds();
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, minutes, secs) : String.format("%02d:%02d", minutes, secs);
    }
    private void announce(String message) { Bukkit.broadcastMessage(plugin.color(plugin.prefix() + message)); }
    private void saveActive() { try { store.saveActive(session); } catch (IOException e) { plugin.getLogger().warning("Не удалось сохранить ивент: " + e.getMessage()); } }
}
