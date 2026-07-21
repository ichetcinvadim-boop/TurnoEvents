package pro.turnoworld.events;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

public final class TestRunner {
    private static int tests;

    public static void main(String[] args) throws Exception {
        templateTests(); sessionTests(); persistenceTests(); resourceTests();
        System.out.println("TurnoEvents: OK — " + tests + " проверок пройдено");
        check(tests >= 500, "должно быть не меньше 500 проверок");
    }

    private static EventTemplate template(String id, EventType type) {
        Map<String, Long> points = new LinkedHashMap<>(); points.put("ANY", 1L); points.put("DIAMOND_ORE", 15L);
        return new EventTemplate(id, type, "Тест " + id, true, 120, 100, Set.of("world"), points,
                List.of(new RewardDefinition(1, 1000, "test_item"), new RewardDefinition(2, 500, ""), new RewardDefinition(3, 250, "")));
    }

    private static void templateTests() {
        check(EventType.values().length == 6, "ровно шесть базовых типов");
        for (EventType type : EventType.values()) {
            EventTemplate t = template(type.name().toLowerCase(), type);
            check(t.type() == type, "тип сохранён"); check(t.enabled(), "шаблон включён");
            check(t.durationSeconds() == 120, "длительность"); check(t.goal() == 100, "цель");
            check(t.pointsFor("DIAMOND_ORE") == 15, "точный вес"); check(t.pointsFor("diamond_ore") == 15, "вес без регистра");
            check(t.pointsFor("STONE") == 1, "fallback ANY"); check(t.pointsFor(null) == 1, "null fallback");
            check(t.worldAllowed("world"), "разрешённый мир"); check(!t.worldAllowed("creative"), "запрещённый мир");
            check(t.reward(1).money() == 1000, "награда первого места"); check(t.reward(1).hasItem(), "особый предмет");
            check(!t.reward(2).hasItem(), "пустой предмет"); check(t.reward(4) == null, "нет награды вне топ-3");
            check(!type.scoreLabel().isBlank(), "подпись типа"); check(type.icon() != null, "иконка типа");
        }
        Question q = new Question("Ответ?", List.of("Ёлка", "два слова", "64"), 10);
        check(q.matches("елка"), "ё нормализуется"); check(q.matches("  ДВА   СЛОВА "), "пробелы нормализуются");
        check(q.matches("64"), "числовой ответ"); check(!q.matches("65"), "ошибочный ответ"); check(!q.matches(null), "null ответ");
    }

    private static void sessionTests() {
        long now = 1_000_000L;
        EventSession session = new EventSession("run-1", template("miner", EventType.MINER), EventState.ANNOUNCING, now + 10_000, now + 130_000);
        check(session.state() == EventState.ANNOUNCING, "объявление"); check(session.remainingSeconds(now) == 10, "время объявления");
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 120; i++) {
            UUID id = new UUID(0, i + 1); ids.add(id);
            check(session.join(id), "первое присоединение " + i); check(!session.join(id), "повторное присоединение " + i);
            check(session.joined(id), "участник найден " + i); check(session.points(id) == 0, "нулевые очки " + i);
            session.setPoints(id, i * 10L);
            check(session.points(id) == i * 10L, "установка очков " + i);
        }
        check(session.participants().size() == 120, "120 участников");
        session.start(now); check(session.state() == EventState.RUNNING, "старт"); check(session.remainingSeconds(now) == 120, "полная длительность");
        for (int i = 0; i < 120; i++) {
            long before = session.points(ids.get(i)); long after = session.addPoints(ids.get(i), i + 1);
            check(after == before + i + 1, "добавление очков " + i); check(session.points(ids.get(i)) == after, "чтение очков " + i);
        }
        check(session.addPoints(UUID.randomUUID(), 100) == 0, "неучастнику очки не начисляются");
        check(session.addPoints(ids.get(0), 0) == 1, "нулевое начисление игнорируется");
        List<EventSession.Standing> standings = session.standings(id -> "P" + id.getLeastSignificantBits());
        check(standings.size() == 120, "полная таблица");
        for (int i = 1; i < standings.size(); i++) check(standings.get(i - 1).points() >= standings.get(i).points(), "сортировка " + i);
        check(session.place(standings.get(0).uuid(), id -> "P" + id.getLeastSignificantBits()) == 1, "первое место");
        check(session.pause(now + 20_000), "пауза"); check(session.state() == EventState.PAUSED, "состояние паузы"); check(session.remainingSeconds(now + 999_999) == 100, "пауза не тикает");
        session.extend(5_000); check(session.remainingSeconds(now) == 105, "продление паузы");
        check(session.resume(now + 30_000), "возобновление"); check(session.remainingSeconds(now + 30_000) == 105, "время после возобновления");
        session.setRemaining(42_000, now + 40_000); check(session.remainingSeconds(now + 40_000) == 42, "установка времени");
        UUID removed = ids.get(40); check(session.leave(removed), "выход"); check(!session.joined(removed), "исключён из участников");
        check(session.place(removed, Object::toString) == 0, "нет места после выхода"); check(!session.leave(removed), "повторный выход");
        session.state(EventState.FINISHED); check(session.addPoints(ids.get(1), 100) == session.points(ids.get(1)), "после финиша очки закрыты");
    }

    private static void persistenceTests() throws Exception {
        UUID id = UUID.randomUUID(); PlayerEventData original = new PlayerEventData(id, "OfflineTester");
        original.wins = 7; original.podiums = 11; original.eventsJoined = 25; original.lifetimePoints = 123456;
        original.pendingMoney = 5432.10; original.lastSeen = 987654321;
        for (int i = 0; i < 40; i++) { original.pendingItems.add("item_" + i); original.rewardedRuns.add("run_" + i); }
        Properties p = original.toProperties(); PlayerEventData restored = PlayerEventData.from(p);
        check(restored.uuid.equals(id), "uuid properties"); check(restored.lastName.equals("OfflineTester"), "ник properties");
        check(restored.wins == 7, "победы properties"); check(restored.podiums == 11, "подиумы properties");
        check(restored.eventsJoined == 25, "участия properties"); check(restored.lifetimePoints == 123456, "очки properties");
        check(Math.abs(restored.pendingMoney - 5432.10) < 0.001, "деньги properties"); check(restored.lastSeen == 987654321, "lastSeen properties");
        for (int i = 0; i < 40; i++) { check(restored.pendingItems.contains("item_" + i), "pending item " + i); check(restored.rewardedRuns.contains("run_" + i), "ledger " + i); }

        Path temp = Files.createTempDirectory("turnoevents-test-"); EventStore first = new EventStore(temp);
        for (int i = 0; i < 35; i++) {
            UUID uuid = new UUID(10, i + 1); PlayerEventData data = first.getOrCreate(uuid, "User" + i);
            data.wins = i; data.lifetimePoints = i * 100L; data.pendingMoney = i + 0.5; first.save(data);
        }
        EventStore second = new EventStore(temp);
        for (int i = 0; i < 35; i++) {
            PlayerEventData data = second.find("User" + i);
            check(data != null, "поиск офлайн игрока " + i); check(data.wins == i, "победы с диска " + i);
            check(data.lifetimePoints == i * 100L, "очки с диска " + i); check(Math.abs(data.pendingMoney - (i + 0.5)) < 0.001, "деньги с диска " + i);
        }
        List<PlayerEventData> top = second.lifetimeTop(10); check(top.size() == 10, "топ-10");
        for (int i = 1; i < top.size(); i++) check(top.get(i - 1).lifetimePoints >= top.get(i).lifetimePoints, "топ сортировка " + i);

        EventTemplate template = template("persist", EventType.BUILDER); long now = System.currentTimeMillis();
        EventSession active = new EventSession("persist-run", template, EventState.RUNNING, now, now + 60_000);
        for (int i = 0; i < 35; i++) active.setPoints(new UUID(10, i + 1), i * 3L);
        first.saveActive(active);
        check(Files.isRegularFile(temp.resolve("active-event.properties")), "активный ивент записан");
        first.clearActive(); check(!Files.exists(temp.resolve("active-event.properties")), "активный ивент очищен");
    }

    private static void resourceTests() throws Exception {
        String events = Files.readString(Path.of("TurnoEvents/src/main/resources/events.yml"));
        String plugin = Files.readString(Path.of("TurnoEvents/src/main/resources/plugin.yml"));
        String tab = Files.readString(Path.of("TurnoEvents/src/main/resources/tab-scoreboard.yml"));
        String config = Files.readString(Path.of("TurnoEvents/src/main/resources/config.yml"));
        for (String id : List.of("miner", "builder", "hunter", "fisher", "marathon", "quiz")) check(events.contains("  " + id + ":"), "ресурс шаблона " + id);
        for (EventType type : EventType.values()) check(events.contains("type: " + type.name()), "ресурс типа " + type);
        for (String key : List.of("Vault", "PlaceholderAPI", "ExecutableItems", "TAB", "floodgate")) check(plugin.contains(key), "интеграция " + key);
        for (String permission : List.of("admin.start", "admin.stop", "admin.time", "admin.points", "admin.players", "admin.reward", "admin.templates", "admin.schedule", "admin.reload")) check(plugin.contains("turnoevents." + permission), "право " + permission);
        for (String placeholder : List.of("active", "joined", "name", "time", "your_points", "your_place", "goal", "leader_1", "leader_2", "leader_3", "participants")) check(tab.contains("%turnoevents_" + placeholder + "%"), "TAB placeholder " + placeholder);
        check(tab.contains("use-numbers: false"), "TAB без красных чисел"); check(tab.contains("static-number: 0"), "TAB static-number");
        check(config.contains("auto-join: false"), "явное участие"); check(config.contains("ignore-spawner-and-egg-mobs: true"), "защита мобов");
        String lower = (events + plugin + tab + config).toLowerCase();
        for (String forbidden : List.of("mythicmobs", "citizens", "boss-arena", "bosses:", "оскол")) check(!lower.contains(forbidden), "нет лишней зависимости " + forbidden);
    }

    private static void check(boolean condition, String name) {
        tests++; if (!condition) throw new AssertionError("Тест #" + tests + " провален: " + name);
    }
}
