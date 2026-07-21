package pro.turnoworld.events;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public final class EventsHolder implements InventoryHolder {
    public enum View { PLAYER, ADMIN }
    private final View view;
    private Inventory inventory;
    public EventsHolder(View view) { this.view = view; }
    public View view() { return view; }
    public void inventory(Inventory inventory) { this.inventory = inventory; }
    @Override public Inventory getInventory() { return inventory; }
}
