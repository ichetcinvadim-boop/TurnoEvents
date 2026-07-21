package pro.turnoworld.events;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

import java.util.UUID;

public final class EventsHolder implements InventoryHolder {
    public enum View { PLAYER, ADMIN, ADMIN_STANDINGS, ADMIN_PLACE }
    private final View view;
    private final int page;
    private final UUID selectedPlayer;
    private Inventory inventory;
    public EventsHolder(View view) { this(view, 0, null); }
    public EventsHolder(View view, int page, UUID selectedPlayer) {
        this.view = view; this.page = Math.max(0, page); this.selectedPlayer = selectedPlayer;
    }
    public View view() { return view; }
    public int page() { return page; }
    public UUID selectedPlayer() { return selectedPlayer; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
