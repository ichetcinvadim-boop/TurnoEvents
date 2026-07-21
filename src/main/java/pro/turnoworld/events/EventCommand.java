package pro.turnoworld.events;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class EventCommand implements CommandExecutor, TabCompleter {
    private final TurnoEvents plugin;
    public EventCommand(TurnoEvents plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("events")) return playerCommand(sender, args);
        if (args.length == 0) { help(sender); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (List.of("menu", "status", "top").contains(sub) || ((sub.equals("join") || sub.equals("leave")) && args.length == 1)) return playerCommand(sender, args);
        if (sub.equals("help")) { help(sender); return true; }
        if (sub.equals("admin")) { if (sender instanceof Player p && permission(sender, "turnoevents.admin")) plugin.gui().openAdmin(p); return true; }
        if (sub.equals("templates")) {
            if (!permission(sender, "turnoevents.admin.templates")) return true;
            sender.sendMessage(plugin.color("&6Шаблоны: &f" + plugin.catalog().all().stream().map(EventTemplate::id).reduce((a, b) -> a + ", " + b).orElse("нет"))); return true;
        }
        if (sub.equals("start") || sub.equals("startnow")) {
            if (!permission(sender, "turnoevents.admin.start")) return true;
            if (args.length < 2) { sender.sendMessage("/te " + sub + " <id> [секунды до старта]"); return true; }
            long delay = sub.equals("startnow") ? 0 : args.length > 2 ? nonNegative(args[2], -1) : plugin.getConfig().getLong("schedule.announcement-seconds", 60);
            if (delay < 0) { error(sender, "Время должно быть целым неотрицательным числом."); return true; }
            sender.sendMessage(plugin.color(plugin.prefix() + (plugin.manager().start(args[1], delay) ? "&aИвент запущен/объявлен." : "&cШаблон не найден или ивент уже активен."))); return true;
        }
        if (sub.equals("stop") || sub.equals("cancel")) {
            if (!permission(sender, "turnoevents.admin.stop")) return true;
            sender.sendMessage(plugin.color(plugin.prefix() + (plugin.manager().cancel() ? "&eИвент отменён без наград." : "&cНет активного ивента."))); return true;
        }
        if (sub.equals("finish")) {
            if (!permission(sender, "turnoevents.admin.stop")) return true;
            sender.sendMessage(plugin.color(plugin.prefix() + (plugin.manager().finish() ? "&aИтоги подведены, награды начислены." : "&cНет активного ивента."))); return true;
        }
        if (sub.equals("pause") || sub.equals("resume")) {
            if (!permission(sender, "turnoevents.admin.time")) return true;
            boolean changed = sub.equals("pause") ? plugin.manager().pause() : plugin.manager().resume();
            sender.sendMessage(plugin.color(plugin.prefix() + (changed ? "&aСостояние изменено." : "&cСейчас это действие невозможно."))); return true;
        }
        if (sub.equals("extend") || sub.equals("settime")) {
            if (!permission(sender, "turnoevents.admin.time")) return true;
            if (args.length < 2) { sender.sendMessage("/te " + sub + " <секунды>"); return true; }
            long seconds = nonNegative(args[1], -1); if (seconds < 0) { error(sender, "Укажите целое число секунд."); return true; }
            boolean changed = sub.equals("extend") ? plugin.manager().extend(seconds) : plugin.manager().setRemaining(seconds);
            sender.sendMessage(plugin.color(plugin.prefix() + (changed ? "&aВремя изменено." : "&cНет активного ивента."))); return true;
        }
        if (sub.equals("addpoints") || sub.equals("setpoints")) return points(sender, args, sub.equals("addpoints"));
        if (sub.equals("join") || sub.equals("leave")) return adminMembership(sender, args, sub.equals("join"));
        if (sub.equals("reward")) return reward(sender, args);
        if (sub.equals("create")) return create(sender, args);
        if (sub.equals("delete")) {
            if (!permission(sender, "turnoevents.admin.templates")) return true;
            if (args.length < 2) { sender.sendMessage("/te delete <id>"); return true; }
            sender.sendMessage(plugin.color(plugin.prefix() + (plugin.catalog().delete(args[1]) ? "&aШаблон удалён." : "&cШаблон не найден."))); return true;
        }
        if (sub.equals("schedule")) return schedule(sender, args);
        if (sub.equals("reload")) {
            if (!permission(sender, "turnoevents.admin.reload")) return true;
            plugin.reloadEverything(); sender.sendMessage(plugin.color(plugin.prefix() + "&aКонфигурация перезагружена.")); return true;
        }
        if (sub.equals("validate")) {
            if (!permission(sender, "turnoevents.admin.reload")) return true;
            List<String> errors = plugin.catalog().validate(); sender.sendMessage(plugin.color(plugin.prefix() + (errors.isEmpty() ? "&aОшибок не найдено." : "&eЗамечаний: " + errors.size())));
            errors.forEach(e -> sender.sendMessage(plugin.color("&8- &f" + e))); return true;
        }
        help(sender); return true;
    }

    private boolean playerCommand(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { error(sender, "Эта команда доступна игроку."); return true; }
        if (!permission(sender, "turnoevents.use")) return true;
        String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("menu")) plugin.gui().open(player);
        else if (sub.equals("join")) sender.sendMessage(plugin.color(plugin.prefix() + (plugin.manager().join(player.getUniqueId(), player.getName()) ? "&aВы участвуете в ивенте." : "&cНе удалось присоединиться.")));
        else if (sub.equals("leave")) sender.sendMessage(plugin.color(plugin.prefix() + (plugin.manager().leave(player.getUniqueId()) ? "&eВы покинули ивент." : "&cВы не участвуете.")));
        else if (sub.equals("status")) status(sender, player);
        else if (sub.equals("top")) top(sender);
        else plugin.gui().open(player);
        return true;
    }

    private void status(CommandSender sender, Player player) {
        EventSession s = plugin.manager().session();
        if (s == null) { sender.sendMessage(plugin.color(plugin.prefix() + "&7Сейчас нет ивента. Следующий через &f" + plugin.manager().nextTime())); return; }
        sender.sendMessage(plugin.color("&6" + s.template().name() + " &8| &f" + s.state() + " &8| &e" + EventManager.formatSeconds(s.remainingSeconds(System.currentTimeMillis()))));
        sender.sendMessage(plugin.color("&7Ваши очки: &f" + s.points(player.getUniqueId()) + " &7место: &f" + plugin.manager().place(player.getUniqueId())));
    }
    private void top(CommandSender sender) {
        List<EventSession.Standing> list = plugin.manager().standings();
        if (list.isEmpty()) { error(sender, "Таблица лидеров пока пуста."); return; }
        for (int i = 0; i < Math.min(10, list.size()); i++) sender.sendMessage(plugin.color("&e#" + (i + 1) + " &f" + list.get(i).name() + " &8— &6" + list.get(i).points()));
    }
    private boolean points(CommandSender sender, String[] args, boolean add) {
        if (!permission(sender, "turnoevents.admin.points")) return true;
        if (args.length < 3) { sender.sendMessage("/te " + (add ? "addpoints" : "setpoints") + " <игрок|UUID> <число>"); return true; }
        PlayerEventData data = plugin.store().find(args[1]); long value = nonNegative(args[2], -1);
        if (data == null || value < 0) { error(sender, "Игрок не найден или число неверно."); return true; }
        boolean ok = add ? plugin.manager().addPoints(data.uuid, value) >= 0 : plugin.manager().setPoints(data.uuid, value, data.lastName);
        sender.sendMessage(plugin.color(plugin.prefix() + (ok ? "&aОчки изменены." : "&cНет активного ивента или игрок не участвует."))); return true;
    }
    private boolean adminMembership(CommandSender sender, String[] args, boolean join) {
        if (!permission(sender, "turnoevents.admin.players")) return true;
        if (args.length < 2) { sender.sendMessage("/te " + (join ? "join" : "leave") + " <игрок|UUID>"); return true; }
        PlayerEventData data = plugin.store().find(args[1]); if (data == null) { error(sender, "Игрок не найден."); return true; }
        boolean ok = join ? plugin.manager().join(data.uuid, data.lastName) : plugin.manager().leave(data.uuid);
        sender.sendMessage(plugin.color(plugin.prefix() + (ok ? "&aСостав участников изменён." : "&cДействие невозможно."))); return true;
    }
    private boolean reward(CommandSender sender, String[] args) {
        if (!permission(sender, "turnoevents.admin.reward")) return true;
        if (args.length < 3) { sender.sendMessage("/te reward <игрок|UUID> <деньги> [executable-item|-]"); return true; }
        PlayerEventData data = plugin.store().find(args[1]); double money = decimal(args[2], -1); String item = args.length > 3 ? args[3] : "-";
        if (data == null || money < 0 || !plugin.rewards().grantManual(data, money, item)) { error(sender, "Игрок не найден или награда пуста."); return true; }
        try { plugin.store().save(data); } catch (IOException ignored) { }
        Player online = Bukkit.getPlayer(data.uuid); if (online != null) plugin.claim(online);
        sender.sendMessage(plugin.color(plugin.prefix() + "&aНаграда сохранена и будет выдана игроку.")); return true;
    }
    private boolean create(CommandSender sender, String[] args) {
        if (!permission(sender, "turnoevents.admin.templates")) return true;
        if (args.length < 4) { sender.sendMessage("/te create <id> <MINER|BUILDER|HUNTER|FISHER|MARATHON|QUIZ> <минуты>"); return true; }
        try {
            EventType type = EventType.valueOf(args[2].toUpperCase(Locale.ROOT)); long minutes = nonNegative(args[3], -1);
            boolean ok = minutes > 0 && plugin.catalog().create(args[1], type, minutes);
            sender.sendMessage(plugin.color(plugin.prefix() + (ok ? "&aШаблон создан. Настройте его в events.yml." : "&cНе удалось создать шаблон.")));
        } catch (IllegalArgumentException e) { error(sender, "Неизвестный тип ивента."); }
        return true;
    }
    private boolean schedule(CommandSender sender, String[] args) {
        if (!permission(sender, "turnoevents.admin.schedule")) return true;
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            sender.sendMessage(plugin.color(plugin.prefix() + "&7Расписание: " + (plugin.manager().scheduleEnabled() ? "&aвключено" : "&cвыключено") + "&7, следующий: &f" + plugin.manager().nextTime())); return true;
        }
        if (args[1].equalsIgnoreCase("on") || args[1].equalsIgnoreCase("off")) {
            plugin.manager().scheduleEnabled(args[1].equalsIgnoreCase("on")); sender.sendMessage(plugin.color(plugin.prefix() + "&aНастройка сохранена."));
        } else sender.sendMessage("/te schedule <on|off|status>"); return true;
    }
    private void help(CommandSender sender) {
        sender.sendMessage(plugin.color("&6&lTurnoEvents &f— команды"));
        sender.sendMessage(plugin.color("&e/events &7— меню; &e/events join|leave|status|top"));
        sender.sendMessage(plugin.color("&e/te admin &7— админ-меню; &e/te templates"));
        sender.sendMessage(plugin.color("&e/te start <id> [сек] &7| &e/te startnow <id> &7| &e/te finish &7| &e/te stop"));
        sender.sendMessage(plugin.color("&e/te pause|resume|extend|settime &7| &e/te addpoints|setpoints <игрок> <число>"));
        sender.sendMessage(plugin.color("&e/te join|leave <игрок> &7| &e/te reward <игрок> <деньги> [item]"));
        sender.sendMessage(plugin.color("&e/te create|delete &7| &e/te schedule &7| &e/te reload|validate"));
    }
    private boolean permission(CommandSender sender, String permission) { if (sender.hasPermission(permission)) return true; error(sender, "Нет права " + permission); return false; }
    private void error(CommandSender sender, String message) { sender.sendMessage(plugin.color(plugin.prefix() + "&c" + message)); }
    private long nonNegative(String value, long fallback) { try { long n = Long.parseLong(value); return n >= 0 ? n : fallback; } catch (Exception e) { return fallback; } }
    private double decimal(String value, double fallback) { try { double n = Double.parseDouble(value.replace(',', '.')); return n >= 0 && Double.isFinite(n) ? n : fallback; } catch (Exception e) { return fallback; } }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("events")) return filter(List.of("menu", "join", "leave", "status", "top"), args[args.length - 1]);
        if (args.length == 1) return filter(List.of("help", "menu", "admin", "templates", "start", "startnow", "finish", "stop", "pause", "resume", "extend", "settime", "addpoints", "setpoints", "join", "leave", "reward", "create", "delete", "schedule", "reload", "validate"), args[0]);
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2 && (sub.equals("start") || sub.equals("startnow") || sub.equals("delete"))) return filter(plugin.catalog().all().stream().map(EventTemplate::id).toList(), args[1]);
        if (args.length == 2 && List.of("addpoints", "setpoints", "join", "leave", "reward").contains(sub)) return filter(Arrays.stream(Bukkit.getOfflinePlayers()).map(p -> p.getName() == null ? p.getUniqueId().toString() : p.getName()).toList(), args[1]);
        if (args.length == 2 && sub.equals("schedule")) return filter(List.of("on", "off", "status"), args[1]);
        if (args.length == 3 && sub.equals("create")) return filter(Arrays.stream(EventType.values()).map(Enum::name).toList(), args[2]);
        return List.of();
    }
    private List<String> filter(List<String> values, String prefix) { String p = prefix.toLowerCase(Locale.ROOT); return values.stream().filter(v -> v.toLowerCase(Locale.ROOT).startsWith(p)).limit(50).toList(); }
}
