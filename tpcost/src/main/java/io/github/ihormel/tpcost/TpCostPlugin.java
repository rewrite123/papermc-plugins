package io.github.ihormel.tpcost;

import java.util.List;
import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class TpCostPlugin extends JavaPlugin {
    private final TeleportRequests requests = new TeleportRequests(System::nanoTime);

    @Override
    public void onEnable() {
        var commands = new TeleportCommands(getServer(), requests);
        for (String name : List.of("tpa", "tpaccept", "tpdeny", "tpcancel", "tpcost")) {
            var command = Objects.requireNonNull(getCommand(name), "Missing command in plugin.yml: " + name);
            command.setExecutor(commands);
            command.setTabCompleter(commands);
        }
        getServer().getPluginManager().registerEvents(commands, this);
        getServer().getScheduler().runTaskTimer(this, commands::expireRequests, 20L, 20L);
    }

    @Override
    public void onDisable() {
        requests.clear();
    }
}
