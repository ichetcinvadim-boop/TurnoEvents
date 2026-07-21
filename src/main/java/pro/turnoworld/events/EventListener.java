package pro.turnoworld.events;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.io.IOException;

public final class EventListener implements Listener {
    private final TurnoEvents plugin;
    public EventListener(TurnoEvents plugin) { this.plugin = plugin; }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer(); plugin.data(player); plugin.claim(player);
        EventSession session = plugin.manager().session();
        if (session != null && plugin.manager().active()) {
            if (plugin.getConfig().getBoolean("auto-join", false)) plugin.manager().join(player.getUniqueId(), player.getName());
            player.sendMessage(plugin.color(plugin.prefix() + "&eСейчас проходит «" + session.template().name() + "». &f/events"));
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        try { plugin.store().save(plugin.data(event.getPlayer())); } catch (IOException ignored) { }
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onBreak(BlockBreakEvent event) {
        plugin.manager().miner(event.getPlayer(), event.getBlock().getLocation(), event.getBlock().getType().name());
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onPlace(BlockPlaceEvent event) {
        plugin.manager().builder(event.getPlayer(), event.getBlockPlaced().getLocation(), event.getBlockPlaced().getType().name());
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onSpawn(CreatureSpawnEvent event) {
        CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
        if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG ||
                reason == CreatureSpawnEvent.SpawnReason.EGG || reason == CreatureSpawnEvent.SpawnReason.DISPENSE_EGG)
            plugin.manager().markArtificial(event.getEntity().getUniqueId());
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onDeath(EntityDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer != null) plugin.manager().hunter(killer, event.getEntity().getUniqueId(), event.getEntity().getType().name());
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onFish(PlayerFishEvent event) {
        if (event.getState() != PlayerFishEvent.State.CAUGHT_FISH || !(event.getCaught() instanceof Item item)) return;
        plugin.manager().fisher(event.getPlayer(), item.getItemStack().getType().name());
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR) public void onMove(PlayerMoveEvent event) {
        Location from = event.getFrom(), to = event.getTo();
        if (to == null || from.getWorld() != to.getWorld()) return;
        plugin.manager().move(event.getPlayer(), from.distance(to));
    }
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST) public void onChat(AsyncPlayerChatEvent event) {
        String answer = event.getMessage(); Player player = event.getPlayer();
        if (!plugin.manager().wouldAnswer(player, answer)) return;
        event.setCancelled(true);
        plugin.getServer().getScheduler().runTask(plugin, () -> plugin.manager().answer(player, answer));
    }
    @EventHandler public void onInventory(InventoryClickEvent event) { plugin.gui().click(event); }
}
