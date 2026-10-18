package io.github.ihormel.inventorysorter;

import java.util.Objects;
import org.bukkit.plugin.java.JavaPlugin;

public final class InventorySorterPlugin extends JavaPlugin {
    private SortingControls controls;

    @Override
    public void onEnable() {
        controls = new SortingControls(task -> getServer().getScheduler().runTask(this, task));
        var command = Objects.requireNonNull(getCommand("sort"), "Missing sort command in plugin.yml");
        command.setExecutor(controls);
        command.setTabCompleter(controls);
        getServer().getPluginManager().registerEvents(controls, this);
    }

    @Override
    public void onDisable() {
        if (controls != null) controls.clear();
    }
}
