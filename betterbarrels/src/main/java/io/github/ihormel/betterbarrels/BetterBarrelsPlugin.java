package io.github.ihormel.betterbarrels;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.logging.Level;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public final class BetterBarrelsPlugin extends JavaPlugin {
    private WorldScan scan;
    private ScanState state;

    @Override
    public void onEnable() {
        try {
            state = new ScanState(getDataFolder().toPath().resolve("migration.properties"));
            BarrelStorage storage = new BarrelStorage(ItemStack::deserializeBytes, ItemStack::serializeAsBytes);
            Conversion conversion = new Conversion(storage, getDataFolder().toPath().resolve("conversion-backups"),
                    ItemStack::serializeAsBytes);
            scan = new WorldScan(this, state, conversion);
            getServer().getPluginManager().registerEvents(new BarrelListener(this, storage, conversion, state, scan), this);
            scan.start(false, state.uninstall());
            getServer().getScheduler().runTaskTimer(this, scan::tick, 5L, 5L);
            getLogger().info("Scanning generated chunks, one per 5 ticks. Back up worlds before any plugin migration.");
        } catch (IOException | RuntimeException error) {
            getLogger().log(Level.SEVERE, "Cannot initialize BetterBarrels; do not remove its data or uninstall with stored items", error);
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("betterbarrels.admin")) {
            sender.sendMessage("You do not have permission to manage BetterBarrels.");
            return true;
        }
        try {
            if (args.length == 1 && args[0].equalsIgnoreCase("status")) {
                sender.sendMessage(scan.status());
                if (scan.safeToUninstall()) {
                    sender.sendMessage("Conversion complete for loaded Bukkit worlds. Collect overflow drops NOW, then stop the server and remove the JAR. Load any other worlds and rescan before removing.");
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("uninstall") && args[1].equalsIgnoreCase("confirm")) {
                scan.start(true, true);
                sender.sendMessage("UNINSTALL MODE saved. All barrels become chests, including empty barrels. Overflow drops on the ground and may despawn. Check /betterbarrels status; do not remove the JAR until COMPLETE.");
            } else if (args.length == 1 && args[0].equalsIgnoreCase("rescan")) {
                scan.start(true, state.uninstall());
                sender.sendMessage("Full rescan started. Uninstall mode, if active, stays active.");
            } else {
                sender.sendMessage("/betterbarrels status | /betterbarrels uninstall confirm | /betterbarrels rescan");
                sender.sendMessage("Uninstall conversion is irreversible. Back up worlds first; load every world to include it. Overflow becomes ordinary dropped items and can despawn.");
            }
        } catch (IOException | RuntimeException error) {
            scan.fail(error);
            sender.sendMessage("Scan failed; do not uninstall. Check the server log.");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("betterbarrels.admin")) return List.of();
        List<String> choices = args.length == 1 ? List.of("status", "uninstall", "rescan")
                : args.length == 2 && args[0].equalsIgnoreCase("uninstall") ? List.of("confirm") : List.of();
        String prefix = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        return choices.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
