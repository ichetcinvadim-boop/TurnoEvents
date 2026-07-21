package pro.turnoworld.events;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

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
        inv.setItem(49, item(Material.ARROW, "&fНазад", List.of()));
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
        List<EventTemplate> templates = new ArrayList<>(plugin.catalog().all());
        for (int i = 0; i < Math.min(TEMPLATE_SLOTS.length, templates.size()); i++) if (slot == TEMPLATE_SLOTS[i]) {
            boolean started = plugin.manager().start(templates.get(i).id(), 60);
            player.sendMessage(plugin.color(plugin.prefix() + (started ? "&aИвент объявлен." : "&cУже идёт другой ивент."))); openAdmin(player); return;
        }
        if (slot == 40) plugin.manager().cancel();
        else if (slot == 41) plugin.manager().finish();
        else if (slot == 42) plugin.manager().scheduleEnabled(!plugin.manager().scheduleEnabled());
        else if (slot == 49) { open(player); return; }
        openAdmin(player);
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
