package pro.turnoworld.events;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public final class EventsGui {
    private static final int[] TEMPLATE_SLOTS = {10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34};
    private final TurnoEvents plugin;
    public EventsGui(TurnoEvents plugin) { this.plugin = plugin; }

    public void open(Player player) {
        EventsHolder holder = new EventsHolder(EventsHolder.View.PLAYER);
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.color(plugin.getConfig().getString("gui.title", "&0Ивенты TurnoWorld")));
        holder.inventory(inv); fill(inv);
        EventSession session = plugin.manager().session();
        if (session == null) {
            inv.setItem(22, item(Material.CLOCK, "&e&lСледующий ивент", List.of("&7Через: &f" + plugin.manager().nextTime(), "&7Расписание: " + (plugin.manager().scheduleEnabled() ? "&aвключено" : "&cвыключено"))));
        } else {
            inv.setItem(13, item(session.template().type().icon(), "&6&l" + session.template().name(), List.of(
                    "&7Статус: &f" + session.state(), "&7Осталось: &f" + EventManager.formatSeconds(session.remainingSeconds(System.currentTimeMillis())),
                    "&7Участников: &f" + session.participants().size(), "&7Цель: &f" + session.template().goal() + " " + session.template().type().scoreLabel())));
            boolean joined = session.joined(player.getUniqueId());
            inv.setItem(31, item(joined ? Material.RED_DYE : Material.LIME_DYE, joined ? "&c&lПокинуть ивент" : "&a&lУчаствовать",
                    List.of(joined ? "&7Ваши очки: &f" + session.points(player.getUniqueId()) : "&7Нажмите, чтобы присоединиться")));
            List<EventSession.Standing> top = plugin.manager().standings();
            for (int i = 0; i < Math.min(3, top.size()); i++) {
                EventSession.Standing standing = top.get(i);
                inv.setItem(20 + i, item(i == 0 ? Material.GOLD_INGOT : i == 1 ? Material.IRON_INGOT : Material.COPPER_INGOT,
                        "&e#" + (i + 1) + " &f" + standing.name(), List.of("&7Очки: &6" + standing.points())));
            }
        }
        PlayerEventData data = plugin.data(player);
        inv.setItem(49, item(Material.BOOK, "&f&lВаша статистика", List.of("&7Побед: &e" + data.wins, "&7Призовых мест: &e" + data.podiums,
                "&7Участий: &e" + data.eventsJoined, "&7Всего очков: &e" + data.lifetimePoints)));
        if (player.hasPermission("turnoevents.admin")) inv.setItem(53, item(Material.COMPARATOR, "&c&lПанель администратора", List.of("&7Нажмите для управления")));
        player.openInventory(inv);
    }

    public void openAdmin(Player player) {
        EventsHolder holder = new EventsHolder(EventsHolder.View.ADMIN);
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.color(plugin.getConfig().getString("gui.admin-title", "&0Управление ивентами")));
        holder.inventory(inv); fill(inv);
        List<EventTemplate> templates = new ArrayList<>(plugin.catalog().all());
        for (int i = 0; i < Math.min(TEMPLATE_SLOTS.length, templates.size()); i++) {
            EventTemplate template = templates.get(i);
            inv.setItem(TEMPLATE_SLOTS[i], item(template.type().icon(), "&6" + template.name(), List.of("&7ID: &f" + template.id(), "&7Тип: &f" + template.type(),
                    "&7Длительность: &f" + EventManager.formatSeconds(template.durationSeconds()), "", "&eНажмите: объявить через 60 сек.")));
        }
        EventSession session = plugin.manager().session();
        inv.setItem(40, item(Material.BARRIER, "&cОстановить без наград", List.of(session == null ? "&7Нет активного ивента" : "&7" + session.template().name())));
        inv.setItem(41, item(Material.NETHER_STAR, "&aЗавершить с наградами", List.of("&7Подвести итоги сейчас")));
        inv.setItem(42, item(Material.CLOCK, "&eРасписание: " + (plugin.manager().scheduleEnabled() ? "&aвключено" : "&cвыключено"), List.of("&7Нажмите для переключения")));
        inv.setItem(43, item(Material.PLAYER_HEAD, "&b&lУправление местами", List.of(
                session == null ? "&7Нет текущего ивента" : "&7Участников: &f" + session.participants().size(),
                "&7Назначить место или", "&7аннулировать результат игрока")));
        inv.setItem(49, item(Material.ARROW, "&fНазад", List.of()));
        player.openInventory(inv);
    }

    public void openStandings(Player player, int requestedPage) {
        EventSession session = plugin.manager().session();
        if (session == null) { openAdmin(player); return; }
        List<UUID> players = adminPlayers(session);
        int maxPage = Math.max(0, (players.size() - 1) / 45);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        EventsHolder holder = new EventsHolder(EventsHolder.View.ADMIN_STANDINGS, page, null);
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.color("&0Управление местами"));
        holder.inventory(inv); fill(inv);
        List<EventSession.Standing> standings = plugin.manager().standings();
        for (int slot = 0, index = page * 45; slot < 45 && index < players.size(); slot++, index++) {
            UUID uuid = players.get(index);
            boolean excluded = session.disqualified(uuid);
            int place = placeOf(standings, uuid);
            inv.setItem(slot, item(excluded ? Material.BARRIER : Material.PLAYER_HEAD,
                    excluded ? "&c&lАННУЛИРОВАНО &f" + plugin.manager().name(uuid) : "&e#" + place + " &f" + plugin.manager().name(uuid),
                    List.of("&7Очки: &6" + session.points(uuid),
                            excluded ? "&cНаграда не будет выдана" : session.manualOrder().isEmpty() ? "&7Место рассчитано по очкам" : "&dПорядок изменён вручную",
                            "", "&eНажмите для управления")));
        }
        if (page > 0) inv.setItem(45, item(Material.ARROW, "&eПредыдущая страница", List.of("&7Страница " + page)));
        inv.setItem(48, item(Material.MILK_BUCKET, "&aСбросить ручные изменения", List.of("&7Вернуть места по очкам", "&7и восстановить аннулированных")));
        inv.setItem(49, item(Material.ARROW, "&fНазад в админ-панель", List.of()));
        inv.setItem(50, item(Material.WRITABLE_BOOK, "&f&lКак это работает", List.of(
                "&7Выберите игрока, затем место.", "&7Аннулированный игрок не попадёт", "&7в итоговый топ и не получит награду.",
                plugin.manager().active() ? "&aРедактирование доступно" : "&cИтоги уже закрыты")));
        if (page < maxPage) inv.setItem(53, item(Material.ARROW, "&eСледующая страница", List.of("&7Страница " + (page + 2))));
        player.openInventory(inv);
    }

    public void openPlace(Player player, UUID selected, int requestedPage) {
        EventSession session = plugin.manager().session();
        if (session == null || !session.joined(selected)) { openStandings(player, 0); return; }
        List<EventSession.Standing> current = plugin.manager().standings();
        int totalPlaces = current.size() + (session.disqualified(selected) ? 1 : 0);
        int maxPage = Math.max(0, (Math.max(1, totalPlaces) - 1) / 45);
        int page = Math.max(0, Math.min(requestedPage, maxPage));
        EventsHolder holder = new EventsHolder(EventsHolder.View.ADMIN_PLACE, page, selected);
        Inventory inv = Bukkit.createInventory(holder, 54, plugin.color("&0Место: " + plugin.manager().name(selected)));
        holder.inventory(inv); fill(inv);
        for (int slot = 0, place = page * 45 + 1; slot < 45 && place <= totalPlaces; slot++, place++) {
            String occupant = place <= current.size() ? current.get(place - 1).name() : "свободно после восстановления";
            boolean selectedPlace = place <= current.size() && current.get(place - 1).uuid().equals(selected);
            inv.setItem(slot, item(selectedPlace ? Material.EMERALD : Material.PAPER, "&e&lМесто #" + place,
                    List.of("&7Сейчас: &f" + occupant, selectedPlace ? "&aЭто текущее место игрока" : "&eНажмите, чтобы назначить")));
        }
        if (page > 0) inv.setItem(45, item(Material.ARROW, "&eПредыдущие места", List.of()));
        boolean excluded = session.disqualified(selected);
        inv.setItem(47, item(excluded ? Material.LIME_DYE : Material.BARRIER,
                excluded ? "&a&lВосстановить игрока" : "&c&lАннулировать место",
                excluded ? List.of("&7Вернуть игрока в таблицу", "&7без выдачи награды прямо сейчас")
                        : List.of("&7Убрать из итогового топа", "&7и не выдавать награду", "&aДействие можно отменить")));
        inv.setItem(49, item(Material.ARROW, "&fНазад к участникам", List.of()));
        inv.setItem(51, item(Material.NAME_TAG, "&f" + plugin.manager().name(selected), List.of("&7Очки: &6" + session.points(selected),
                excluded ? "&cРезультат аннулирован" : "&7Текущее место: &e#" + placeOf(current, selected))));
        if (page < maxPage) inv.setItem(53, item(Material.ARROW, "&eСледующие места", List.of()));
        player.openInventory(inv);
    }

    public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof EventsHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        if (holder.view() == EventsHolder.View.PLAYER) {
            if (slot == 31 && plugin.manager().active()) {
                EventSession session = plugin.manager().session();
                if (session.joined(player.getUniqueId())) plugin.manager().leave(player.getUniqueId());
                else plugin.manager().join(player.getUniqueId(), player.getName());
                open(player);
            } else if (slot == 53 && player.hasPermission("turnoevents.admin")) openAdmin(player);
            return;
        }
        if (!player.hasPermission("turnoevents.admin")) return;
        if (holder.view() == EventsHolder.View.ADMIN_STANDINGS) {
            if (!player.hasPermission("turnoevents.admin.placements")) return;
            EventSession session = plugin.manager().session();
            if (session == null) { openAdmin(player); return; }
            List<UUID> players = adminPlayers(session);
            int index = holder.page() * 45 + slot;
            if (slot < 45 && index < players.size()) { openPlace(player, players.get(index), 0); return; }
            if (slot == 45 && holder.page() > 0) { openStandings(player, holder.page() - 1); return; }
            if (slot == 53) { openStandings(player, holder.page() + 1); return; }
            if (slot == 48) {
                boolean changed = plugin.manager().resetPlacements();
                player.sendMessage(plugin.color(plugin.prefix() + (changed ? "&aМеста снова рассчитываются по очкам; аннулирования сняты." : "&eРучных изменений нет или итоги уже закрыты.")));
                openStandings(player, holder.page()); return;
            }
            if (slot == 49) { openAdmin(player); return; }
            return;
        }
        if (holder.view() == EventsHolder.View.ADMIN_PLACE) {
            if (!player.hasPermission("turnoevents.admin.placements")) return;
            UUID selected = holder.selectedPlayer();
            EventSession session = plugin.manager().session();
            if (selected == null || session == null) { openAdmin(player); return; }
            int totalPlaces = plugin.manager().standings().size() + (session.disqualified(selected) ? 1 : 0);
            int place = holder.page() * 45 + slot + 1;
            if (slot < 45 && place <= totalPlaces) {
                boolean changed = plugin.manager().assignPlace(selected, place);
                player.sendMessage(plugin.color(plugin.prefix() + (changed ? "&aИгроку назначено место #" + place + "." : "&cНе удалось изменить место: итоги уже закрыты.")));
                openStandings(player, 0); return;
            }
            if (slot == 45 && holder.page() > 0) { openPlace(player, selected, holder.page() - 1); return; }
            if (slot == 53) { openPlace(player, selected, holder.page() + 1); return; }
            if (slot == 47) {
                boolean excluded = session.disqualified(selected);
                boolean changed = excluded ? plugin.manager().reinstate(selected) : plugin.manager().disqualify(selected);
                player.sendMessage(plugin.color(plugin.prefix() + (changed ? excluded ? "&aИгрок восстановлен в таблице." : "&cМесто игрока аннулировано." : "&eДействие невозможно или итоги уже закрыты.")));
                openStandings(player, 0); return;
            }
            if (slot == 49) { openStandings(player, 0); return; }
            return;
        }
        List<EventTemplate> templates = new ArrayList<>(plugin.catalog().all());
        for (int i = 0; i < Math.min(TEMPLATE_SLOTS.length, templates.size()); i++) if (slot == TEMPLATE_SLOTS[i]) {
            boolean started = plugin.manager().start(templates.get(i).id(), 60);
            player.sendMessage(plugin.color(plugin.prefix() + (started ? "&aИвент объявлен." : "&cУже идёт другой ивент."))); openAdmin(player); return;
        }
        if (slot == 40) plugin.manager().cancel();
        else if (slot == 41) plugin.manager().finish();
        else if (slot == 42) plugin.manager().scheduleEnabled(!plugin.manager().scheduleEnabled());
        else if (slot == 43) { openStandings(player, 0); return; }
        else if (slot == 49) { open(player); return; }
        openAdmin(player);
    }

    private List<UUID> adminPlayers(EventSession session) {
        List<UUID> result = new ArrayList<>();
        plugin.manager().standings().forEach(standing -> result.add(standing.uuid()));
        Set<UUID> included = new HashSet<>(result);
        session.disqualifiedParticipants().stream().filter(uuid -> included.add(uuid))
                .sorted(Comparator.comparing(uuid -> plugin.manager().name(uuid).toLowerCase()))
                .forEach(result::add);
        session.participants().stream().filter(uuid -> included.add(uuid))
                .sorted(Comparator.comparing(uuid -> plugin.manager().name(uuid).toLowerCase()))
                .forEach(result::add);
        return result;
    }

    private int placeOf(List<EventSession.Standing> standings, UUID uuid) {
        for (int i = 0; i < standings.size(); i++) if (standings.get(i).uuid().equals(uuid)) return i + 1;
        return 0;
    }

    private void fill(Inventory inventory) {
        ItemStack filler = item(material(plugin.getConfig().getString("gui.filler", "BLACK_STAINED_GLASS_PANE"), Material.BLACK_STAINED_GLASS_PANE), " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);
    }
    private ItemStack item(Material material, String title, List<String> lore) {
        ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta();
        if (meta != null) { meta.setDisplayName(plugin.color(title)); meta.setLore(lore.stream().map(plugin::color).toList()); stack.setItemMeta(meta); }
        return stack;
    }
    private Material material(String name, Material fallback) { Material result = Material.matchMaterial(name == null ? "" : name); return result == null ? fallback : result; }
}
