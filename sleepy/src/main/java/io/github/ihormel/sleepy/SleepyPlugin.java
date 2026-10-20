package io.github.ihormel.sleepy;

import java.util.List;
import java.util.Locale;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class SleepyPlugin extends JavaPlugin implements Listener {
    static final String ADMIN = "sleepy.admin";
    private SleepingRule rule;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        rule = new SleepingRule(getLogger());
        loadSettings();
        getServer().getPluginManager().registerEvents(this, this);
        applyAll();
        // Player counts change through joins, quits, deaths, world and game-mode changes;
        // vanilla needs 100 ticks of deep sleep, so a once-per-second refresh is always in time.
        getServer().getScheduler().runTaskTimer(this, this::applyAll, 20L, 20L);
    }

    SleepingRule rule() {
        return rule;
    }

    private void loadSettings() {
        var config = getConfig();
        int percent = config.getInt("sleeping-percentage", SleepingRule.DEFAULT_PERCENT);
        int minPlayers = config.getInt("min-sleeping-players", SleepingRule.OFF);
        if (percent < 0 || percent > 100) {
            getLogger().warning("sleeping-percentage must be 0-100; using " + SleepingRule.DEFAULT_PERCENT + ".");
            percent = SleepingRule.DEFAULT_PERCENT;
        }
        if (minPlayers < 0) {
            getLogger().warning("min-sleeping-players must be 0 (off) or more; turning it off.");
            minPlayers = SleepingRule.OFF;
        }
        rule.configure(percent, minPlayers);
    }

    private void applyAll() {
        getServer().getWorlds().forEach(rule::apply);
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        rule.apply(event.getWorld());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBedEnter(PlayerBedEnterEvent event) {
        rule.apply(event.getPlayer().getWorld());
    }

    private String status(CommandSender sender) {
        String text = rule.minPlayers() == SleepingRule.OFF
                ? "Night skips when " + rule.percent() + "% of players are sleeping (minimum players: off)."
                : "Night skips when " + rule.percent() + "% of players or " + rule.minPlayers()
                    + (rule.minPlayers() == 1 ? " player is" : " players are") + " sleeping, whichever is fewer.";
        if (sender instanceof Player player) {
            int active = SleepingRule.active(player.getWorld());
            text += " Here: " + rule.needed(active) + " of " + active + " needed.";
        }
        return text;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || (args.length == 1 && args[0].equalsIgnoreCase("status"))) {
            sender.sendMessage(status(sender));
            return true;
        }
        if (args.length != 2) return false;
        String setting = args[0].toLowerCase(Locale.ROOT);
        if (!setting.equals("percent") && !setting.equals("minplayers")) return false;
        if (!sender.hasPermission(ADMIN)) {
            sender.sendMessage("You do not have permission to change sleep settings.");
            return true;
        }
        int value;
        try {
            value = setting.equals("minplayers") && args[1].equalsIgnoreCase("off")
                    ? SleepingRule.OFF : Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            sender.sendMessage("Value must be a whole number" + (setting.equals("minplayers") ? " or off." : "."));
            return true;
        }
        if (setting.equals("percent") && (value < 0 || value > 100)) {
            sender.sendMessage("Percent must be between 0 and 100.");
            return true;
        }
        if (setting.equals("minplayers") && value < 0) {
            sender.sendMessage("Minimum players must be 0 (off) or more.");
            return true;
        }
        if (setting.equals("percent")) {
            rule.configure(value, rule.minPlayers());
            getConfig().set("sleeping-percentage", value);
        } else {
            rule.configure(rule.percent(), value);
            getConfig().set("min-sleeping-players", value);
        }
        saveConfig();
        applyAll();
        sender.sendMessage("Sleep settings updated. " + status(sender));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> options;
        if (args.length == 1) {
            options = sender.hasPermission(ADMIN) ? List.of("status", "percent", "minplayers") : List.of("status");
        } else if (args.length == 2 && sender.hasPermission(ADMIN)) {
            options = switch (args[0].toLowerCase(Locale.ROOT)) {
                case "percent" -> List.of("25", "50", "100");
                case "minplayers" -> List.of("off", "5", "10");
                default -> List.of();
            };
        } else {
            options = List.of();
        }
        String prefix = args.length == 0 ? "" : args[args.length - 1];
        return options.stream().filter(o -> o.regionMatches(true, 0, prefix, 0, prefix.length())).toList();
    }
}
