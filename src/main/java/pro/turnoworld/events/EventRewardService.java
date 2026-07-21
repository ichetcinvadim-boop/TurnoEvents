package pro.turnoworld.events;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EventRewardService {
    private final TurnoEvents plugin;
    private Economy economy;

    public EventRewardService(TurnoEvents plugin) { this.plugin = plugin; hookEconomy(); }

    public boolean hookEconomy() {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        economy = registration == null ? null : registration.getProvider();
        return economy != null;
    }

    public boolean queue(PlayerEventData data, EventSession session, int place) {
        if (!data.rewardedRuns.add(session.runId())) return false;
        RewardDefinition reward = session.template().reward(place);
        data.eventsJoined++;
        data.lifetimePoints += session.points(data.uuid);
        if (place == 1) data.wins++;
        if (place > 0 && place <= 3) data.podiums++;
        if (reward != null) {
            data.pendingMoney += Math.max(0, reward.money());
            if (reward.hasItem()) data.pendingItems.add(reward.item());
        }
        return true;
    }

    public List<String> claim(Player player, PlayerEventData data) {
        List<String> result = new ArrayList<>();
        if (data.pendingMoney > 0 && (economy != null || hookEconomy())) {
            double amount = data.pendingMoney;
            if (economy.depositPlayer((OfflinePlayer) player, amount).transactionSuccess()) {
                data.pendingMoney = 0;
                result.add("&aПолучено: &e" + economy.format(amount));
            }
        }
        if (!data.pendingItems.isEmpty() && plugin.getServer().getPluginManager().isPluginEnabled("ExecutableItems")) {
            List<String> delivered = new ArrayList<>();
            String command = plugin.getConfig().getString("executable-items.give-command", "ei give %player% %item% 1");
            for (String item : data.pendingItems) {
                String ready = command.replace("%player%", player.getName()).replace("%item%", item);
                if (Bukkit.dispatchCommand(Bukkit.getConsoleSender(), ready)) {
                    delivered.add(item);
                    result.add("&aПолучен особый предмет: &e" + item);
                }
            }
            data.pendingItems.removeAll(delivered);
        }
        if (data.pendingMoney > 0) result.add("&eДеньги сохранены до подключения Vault-экономики.");
        if (!data.pendingItems.isEmpty()) result.add("&eПредмет сохранён до подключения ExecutableItems.");
        return result;
    }

    public boolean grantManual(PlayerEventData data, double money, String item) {
        if (money > 0) data.pendingMoney += money;
        if (item != null && !item.isBlank() && !item.equals("-")) data.pendingItems.add(item.toLowerCase(Locale.ROOT));
        return money > 0 || (item != null && !item.isBlank() && !item.equals("-"));
    }
}
