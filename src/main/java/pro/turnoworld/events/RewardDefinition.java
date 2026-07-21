package pro.turnoworld.events;

public record RewardDefinition(int place, double money, String item) {
    public boolean hasItem() { return item != null && !item.isBlank(); }
}
