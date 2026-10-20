package io.github.ihormel.inventorysorter;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerQuitEvent;

final class SortingControls implements Listener, CommandExecutor, TabCompleter {
    private final DoubleShiftClick clicks;
    private final Consumer<Runnable> schedule;

    SortingControls(Consumer<Runnable> schedule) {
        this(schedule, new DoubleShiftClick(System::nanoTime));
    }

    SortingControls(Consumer<Runnable> schedule, DoubleShiftClick clicks) {
        this.schedule = schedule;
        this.clicks = clicks;
    }

    private boolean allowed(Player player) {
        return player.hasPermission("inventorysorter.use") && player.isOnline()
                && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        UUID id = player.getUniqueId();
        var target = SortTargets.clicked(player, event.getView(), event.getClickedInventory(), event.getSlot());
        if (event.isCancelled() || !allowed(player) || !event.isShiftClick()
                || !InventorySorting.empty(event.getCurrentItem())
                || !InventorySorting.empty(event.getCursor()) || target == null) {
            clicks.clear(id);
            return;
        }
        if (!clicks.click(id, event.getView(), event.getRawSlot())) return;
        event.setCancelled(true);
        var view = event.getView();
        int rawSlot = event.getRawSlot();
        var clicked = event.getClickedInventory();
        int slot = event.getSlot();
        // InventoryClickEvent forbids editing some slots during dispatch; recheck next tick.
        schedule.accept(() -> {
            if (!allowed(player) || player.getOpenInventory() != view
                    || !InventorySorting.empty(view.getCursor())
                    || !InventorySorting.empty(view.getItem(rawSlot))) return;
            var current = SortTargets.clicked(player, view, clicked, slot);
            if (current == null || !current.equals(target)) return;
            target.sort();
            player.sendMessage("Inventory sorted; matching stacks combined.");
        });
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        clicks.clear(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void quit(PlayerQuitEvent event) {
        clicks.clear(event.getPlayer().getUniqueId());
    }

    void clear() {
        clicks.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Only players can sort inventories.");
            return true;
        }
        if (!allowed(player)) {
            player.sendMessage("You cannot sort inventories right now.");
            return true;
        }
        if (args.length > 1 || (args.length == 1 && !args[0].equalsIgnoreCase("player")
                && !args[0].equalsIgnoreCase("container"))) return false;
        var view = player.getOpenInventory();
        if (!InventorySorting.empty(view.getCursor()) || !SortTargets.supportedView(player, view)) {
            player.sendMessage("Empty your cursor first.");
            return true;
        }
        var target = SortTargets.container(player, view);
        if (args.length == 1 && args[0].equalsIgnoreCase("player")) {
            target = SortTargets.player(player);
        } else if (target == null && args.length == 0) {
            target = SortTargets.player(player);
        }
        if (target == null) {
            player.sendMessage("Open a chest, barrel, shulker box or ender chest first.");
            return true;
        }
        clicks.clear(player.getUniqueId());
        target.sort();
        player.sendMessage("Inventory sorted; matching stacks combined.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return args.length == 1 && sender.hasPermission("inventorysorter.use")
                ? List.of("player", "container").stream()
                    .filter(value -> value.regionMatches(true, 0, args[0], 0, args[0].length())).toList()
                : List.of();
    }
}
