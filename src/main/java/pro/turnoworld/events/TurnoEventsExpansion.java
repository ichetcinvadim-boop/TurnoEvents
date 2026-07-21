package pro.turnoworld.events;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

import java.util.List;

public final class TurnoEventsExpansion extends PlaceholderExpansion {
    private final TurnoEvents plugin;
    public TurnoEventsExpansion(TurnoEvents plugin) { this.plugin = plugin; }
    @Override public String getIdentifier() { return "turnoevents"; }
    @Override public String getAuthor() { return "TurnoWorld"; }
    @Override public String getVersion() { return plugin.getDescription().getVersion(); }
    @Override public boolean persist() { return true; }

    @Override public String onRequest(OfflinePlayer player, String raw) {
        String id = raw == null ? "" : raw.toLowerCase();
        EventSession session = plugin.manager().session();
        if (id.equals("active")) return String.valueOf(plugin.manager().active());
        if (id.equals("next_event")) return plugin.manager().nextTime();
        if (session == null) return switch (id) {
            case "state" -> "Нет ивента"; case "name", "id", "type", "goal", "score_label", "your_points", "your_place", "participants" -> "-"; case "time" -> "00:00"; case "joined" -> "false"; default -> "";
        };
        if (id.equals("state")) return state(session.state());
        if (id.equals("name")) return session.template().name();
        if (id.equals("id")) return session.template().id();
        if (id.equals("type")) return session.template().type().name();
        if (id.equals("time")) return EventManager.formatSeconds(session.remainingSeconds(System.currentTimeMillis()));
        if (id.equals("goal")) return String.valueOf(session.template().goal());
        if (id.equals("score_label")) return session.template().type().scoreLabel();
        if (id.equals("participants")) return String.valueOf(session.participants().size());
        if (id.equals("joined")) return String.valueOf(player != null && session.joined(player.getUniqueId()));
        if (id.equals("your_points")) return String.valueOf(player == null ? 0 : session.points(player.getUniqueId()));
        if (id.equals("your_place")) return String.valueOf(player == null ? 0 : plugin.manager().place(player.getUniqueId()));
        if (id.startsWith("leader_")) return leader(id, plugin.manager().standings());
        return null;
    }
    private String leader(String id, List<EventSession.Standing> list) {
        boolean points = id.endsWith("_points");
        String number = id.replace("leader_", "").replace("_points", "");
        try {
            int index = Integer.parseInt(number) - 1;
            if (index < 0 || index >= list.size()) return "-";
            return points ? String.valueOf(list.get(index).points()) : list.get(index).name();
        } catch (NumberFormatException e) { return "-"; }
    }
    private String state(EventState state) { return switch (state) { case ANNOUNCING -> "Скоро"; case RUNNING -> "Идёт"; case PAUSED -> "Пауза"; case FINISHED -> "Завершён"; case CANCELLED -> "Отменён"; }; }
}
