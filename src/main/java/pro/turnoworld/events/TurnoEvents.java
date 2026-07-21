package pro.turnoworld.events;

import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.logging.Level;

public final class TurnoEvents extends JavaPlugin {
    private EventCatalog catalog;
    private EventStore store;
    private EventRewardService rewards;
    private EventManager manager;
    private EventsGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig(); ensureResource("events.yml"); ensureResource("tab-scoreboard.yml");
        try { store = new EventStore(getDataFolder().toPath().resolve("data")); }
        catch (IOException e) { getLogger().log(Level.SEVERE, "Не удалось создать хранилище", e); getServer().getPluginManager().disablePlugin(this); return; }
        catalog = new EventCatalog(new File(getDataFolder(), "events.yml"));
        rewards = new EventRewardService(this);
        manager = new EventManager(this, catalog, store, rewards);
        gui = new EventsGui(this);
        EventCommand command = new EventCommand(this);
        bind("events", command); bind("turnoevents", command);
        getServer().getPluginManager().registerEvents(new EventListener(this), this);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new TurnoEventsExpansion(this).register();
            getLogger().info("PlaceholderAPI: зарегистрированы плейсхолдеры %turnoevents_...%");
        }
        getServer().getScheduler().runTaskTimer(this, manager::tick, 20L, 20L);
        long saveTicks = Math.max(20L, getConfig().getLong("autosave-seconds", 30) * 20L);
        getServer().getScheduler().runTaskTimer(this, this::saveAll, saveTicks, saveTicks);
        List<String> errors = catalog.validate();
        if (errors.isEmpty()) getLogger().info("TurnoEvents включён: загружено " + catalog.all().size() + " типов ивентов.");
        else errors.forEach(error -> getLogger().warning("Проверка: " + error));
    }

    @Override public void onDisable() { if (store != null) saveAll(); }

    public void reloadEverything() {
        reloadConfig(); catalog.reload(); rewards.hookEconomy();
    }
    public void saveAll() {
        if (store == null) return;
        try { store.saveAll(); store.saveActive(manager == null ? null : manager.session()); }
        catch (IOException e) { getLogger().warning("Ошибка автосохранения: " + e.getMessage()); }
    }
    public PlayerEventData data(OfflinePlayer player) { return store.getOrCreate(player.getUniqueId(), player.getName()); }
    public void claim(Player player) {
        PlayerEventData data = data(player);
        for (String line : rewards.claim(player, data)) player.sendMessage(color(prefix() + line));
        try { store.save(data); } catch (IOException ignored) { }
    }
    public String prefix() { return getConfig().getString("messages.prefix", "&6&lИвенты &8» &f"); }
    public String color(String value) { return ChatColor.translateAlternateColorCodes('&', value == null ? "" : value); }
    private void bind(String name, EventCommand handler) {
        PluginCommand command = getCommand(name);
        if (command == null) throw new IllegalStateException("Команда отсутствует в plugin.yml: " + name);
        command.setExecutor(handler); command.setTabCompleter(handler);
    }
    private void ensureResource(String name) { if (!new File(getDataFolder(), name).isFile()) saveResource(name, false); }
    public EventCatalog catalog() { return catalog; }
    public EventStore store() { return store; }
    public EventRewardService rewards() { return rewards; }
    public EventManager manager() { return manager; }
    public EventsGui gui() { return gui; }
}
