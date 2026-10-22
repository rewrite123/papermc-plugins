package io.github.ihormel.tpcost;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

public class TpCostPlugin extends JavaPlugin {
    private final TeleportRequests requests = new TeleportRequests(System::nanoTime);
    private TeleportPricing pricing;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        pricing = TeleportPricing.load(getConfig());
        var commands = new TeleportCommands(getServer(), requests, () -> pricing);
        for (String name : List.of("tpa", "tpaccept", "tpdeny", "tpcancel", "tpcost")) {
            var command = Objects.requireNonNull(getCommand(name), "Missing command in plugin.yml: " + name);
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
        getServer().getPluginManager().registerEvents(commands, this);
        var admin = Objects.requireNonNull(getCommand("tpcostadmin"), "Missing tpcostadmin command");
        admin.setExecutor(this);
        admin.setTabCompleter(this);
        getServer().getScheduler().runTaskTimer(this, commands::expireRequests, 20L, 20L);
    }

    @Override
    public void onDisable() {
        requests.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("tpcost.admin")) {
            sender.sendMessage("[tpcost] You do not have permission to manage teleport pricing.");
            return true;
        }
        if (args.length == 0) {
            TeleportPricing.SETTINGS.forEach(key -> sender.sendMessage("[tpcost] " + key + " = " + pricing.value(key)));
            return true;
        }
        String key = args[0].toLowerCase(Locale.ROOT);
        if (args.length > 2 || !TeleportPricing.SETTINGS.contains(key)) {
            sender.sendMessage("[tpcost] Usage: /tpcostadmin <" + String.join("|", TeleportPricing.SETTINGS) + "> [value]");
            return true;
        }
        if (args.length == 1) {
            sender.sendMessage("[tpcost] " + key + " = " + pricing.value(key));
            return true;
        }
        TeleportPricing updated;
        try {
            updated = pricing.with(key, Integer.parseInt(args[1]));
        } catch (NumberFormatException exception) {
            sender.sendMessage("[tpcost] The value must be a whole number from 0 to 2147483647.");
            return true;
        } catch (IllegalArgumentException exception) {
            sender.sendMessage("[tpcost] " + exception.getMessage());
            return true;
        }
        updated.write(getConfig());
        try {
            getConfig().save(getDataFolder().toPath().resolve("config.yml").toFile());
        } catch (IOException exception) {
            pricing.write(getConfig());
            getLogger().log(Level.SEVERE, "Could not save teleport pricing; change not applied.", exception);
            sender.sendMessage("[tpcost] Could not save the setting. Nothing changed; check the server log.");
            return true;
        }
        pricing = updated;
        sender.sendMessage("[tpcost] " + key + " set to " + pricing.value(key)
                + " and saved. Applies immediately, including pending requests on acceptance.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("tpcost.admin")) return List.of();
        if (args.length == 1) {
            return TeleportPricing.SETTINGS.stream()
                    .filter(key -> key.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (args.length == 2 && TeleportPricing.SETTINGS.contains(args[0].toLowerCase(Locale.ROOT))) {
            return List.of(String.valueOf(pricing.value(args[0].toLowerCase(Locale.ROOT)))).stream()
                    .filter(value -> value.startsWith(args[1])).toList();
        }
        return List.of();
    }
}
