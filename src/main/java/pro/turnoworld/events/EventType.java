package pro.turnoworld.events;

import org.bukkit.Material;

public enum EventType {
    MINER("Добыто руды", Material.DIAMOND_PICKAXE),
    BUILDER("Поставлено блоков", Material.BRICKS),
    HUNTER("Очки охоты", Material.DIAMOND_SWORD),
    FISHER("Очки рыбалки", Material.FISHING_ROD),
    MARATHON("Пройдено блоков", Material.LEATHER_BOOTS),
    QUIZ("Очки викторины", Material.BOOK);

    private final String scoreLabel;
    private final Material icon;
    EventType(String scoreLabel, Material icon) { this.scoreLabel = scoreLabel; this.icon = icon; }
    public String scoreLabel() { return scoreLabel; }
    public Material icon() { return icon; }
}
