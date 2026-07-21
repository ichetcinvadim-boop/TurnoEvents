package pro.turnoworld.events;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class EventCatalog {
    private final File file;
    private final Map<String, EventTemplate> templates = new LinkedHashMap<>();
    private final List<Question> questions = new ArrayList<>();
    private long questionDelaySeconds = 12;

    public EventCatalog(File file) { this.file = file; reload(); }

    public synchronized void reload() {
        templates.clear(); questions.clear();
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection events = yml.getConfigurationSection("events");
        if (events != null) for (String rawId : events.getKeys(false)) {
            String id = rawId.toLowerCase(Locale.ROOT);
            ConfigurationSection s = events.getConfigurationSection(rawId);
            if (s == null) continue;
            EventType type;
            try { type = EventType.valueOf(s.getString("type", "MINER").toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException e) { continue; }
            Map<String, Long> points = new LinkedHashMap<>();
            ConfigurationSection ps = s.getConfigurationSection("points");
            if (ps != null) for (String key : ps.getKeys(false)) points.put(key.toUpperCase(Locale.ROOT), ps.getLong(key, 0));
            List<RewardDefinition> rewards = new ArrayList<>();
            ConfigurationSection rs = s.getConfigurationSection("rewards");
            if (rs != null) for (String key : rs.getKeys(false)) try {
                int place = Integer.parseInt(key);
                ConfigurationSection reward = rs.getConfigurationSection(key);
                if (reward != null) rewards.add(new RewardDefinition(place, reward.getDouble("money", 0), reward.getString("item", "")));
            } catch (NumberFormatException ignored) { }
            Set<String> worlds = new LinkedHashSet<>(s.getStringList("worlds"));
            templates.put(id, new EventTemplate(id, type, s.getString("name", id), s.getBoolean("enabled", true),
                    Math.max(30, s.getLong("duration-seconds", 900)), Math.max(1, s.getLong("goal", 100)),
                    Set.copyOf(worlds), Map.copyOf(points), List.copyOf(rewards)));
        }
        questionDelaySeconds = Math.max(3, yml.getLong("quiz.question-delay-seconds", 12));
        ConfigurationSection qs = yml.getConfigurationSection("quiz.questions");
        if (qs != null) for (String key : qs.getKeys(false)) {
            ConfigurationSection q = qs.getConfigurationSection(key);
            if (q != null) questions.add(new Question(q.getString("text", key), q.getStringList("answers"), Math.max(1, q.getLong("points", 10))));
        }
    }

    public synchronized boolean create(String id, EventType type, long minutes) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!normalized.matches("[a-z0-9_-]{2,32}") || templates.containsKey(normalized)) return false;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        String root = "events." + normalized;
        yml.set(root + ".enabled", true);
        yml.set(root + ".type", type.name());
        yml.set(root + ".name", normalized);
        yml.set(root + ".duration-seconds", Math.max(1, minutes) * 60L);
        yml.set(root + ".goal", 100);
        yml.set(root + ".worlds", List.of("world", "survival2"));
        yml.set(root + ".points.ANY", 1);
        yml.set(root + ".rewards.1.money", 10000);
        yml.set(root + ".rewards.1.item", "");
        try { yml.save(file); reload(); return true; } catch (IOException e) { return false; }
    }

    public synchronized boolean delete(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        if (!templates.containsKey(normalized)) return false;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        yml.set("events." + normalized, null);
        try { yml.save(file); reload(); return true; } catch (IOException e) { return false; }
    }

    public EventTemplate get(String id) { return id == null ? null : templates.get(id.toLowerCase(Locale.ROOT)); }
    public Collection<EventTemplate> all() { return List.copyOf(templates.values()); }
    public List<EventTemplate> enabled() { return templates.values().stream().filter(EventTemplate::enabled).toList(); }
    public List<Question> questions() { return List.copyOf(questions); }
    public long questionDelaySeconds() { return questionDelaySeconds; }

    public List<String> validate() {
        List<String> errors = new ArrayList<>();
        if (templates.isEmpty()) errors.add("Не найдено ни одного шаблона");
        for (EventTemplate t : templates.values()) {
            if (t.points().isEmpty()) errors.add(t.id() + ": нет таблицы очков");
            if (t.rewards().isEmpty()) errors.add(t.id() + ": нет наград");
            if (t.type() == EventType.QUIZ && questions.isEmpty()) errors.add(t.id() + ": нет вопросов викторины");
        }
        return errors;
    }
}
