package io.github.ihormel.craftablebottleofenchanting;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.ExpBottleEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

final class BottleListener implements Listener {
    private final JavaPlugin plugin;

    BottleListener(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.useInteractedBlock() == Event.Result.DENY || event.getClickedBlock() == null
                || event.getClickedBlock().getType() != Material.ENCHANTING_TABLE
                || event.getItem() == null || event.getItem().getType() != Material.GLASS_BOTTLE) {
            return;
        }
        event.setCancelled(true);
        Player player = event.getPlayer();
        if (!allowed(player)) {
            player.sendMessage("You cannot bottle experience right now.");
            return;
        }
        var menu = new BottleMenu(player.getUniqueId(), event.getClickedBlock().getLocation(),
                player.getInventory().getHeldItemSlot());
        player.openInventory(menu.getInventory());
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void click(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof BottleMenu menu)) return;
        boolean cancelled = event.isCancelled();
        event.setCancelled(true);
        if (cancelled || !(event.getWhoClicked() instanceof Player player)
                || event.getRawSlot() != BottleMenu.ACTION_SLOT
                || (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT)) return;
        var view = event.getView();
        // Commit after inventory-event dispatch, never move the menu's display item.
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (!player.isOnline() || player.getOpenInventory() != view) return;
            if (!allowed(player) || !player.getUniqueId().equals(menu.owner())) {
                player.sendMessage("You cannot bottle experience right now.");
                return;
            }
            var table = menu.table();
            if (!table.getWorld().equals(player.getWorld())
                    || table.clone().add(0.5, 0.5, 0.5).distanceSquared(player.getLocation()) > 36
                    || table.getBlock().getType() != Material.ENCHANTING_TABLE) {
                player.sendMessage("Stay near the enchanting table to bottle experience.");
                player.closeInventory();
                return;
            }
            if (!player.getItemOnCursor().getType().isAir()) {
                player.sendMessage("Empty your cursor before bottling experience.");
                return;
            }
            BottleConversion.convert(player, menu.bottleSlot());
        });
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof BottleMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void breakBottle(ExpBottleEvent event) {
        var bottle = event.getEntity();
        bottle.getWorld().dropItemNaturally(bottle.getLocation(), new ItemStack(Material.GLASS_BOTTLE));
    }

    private boolean allowed(Player player) {
        return player.hasPermission("craftablebottleofenchanting.use")
                && !player.isDead() && player.getGameMode() != GameMode.SPECTATOR;
    }
}
