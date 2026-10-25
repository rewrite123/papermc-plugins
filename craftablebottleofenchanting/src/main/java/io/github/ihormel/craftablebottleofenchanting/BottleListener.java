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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

final class BottleListener implements Listener {
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interact(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND || event.getAction() != Action.RIGHT_CLICK_BLOCK
                || !event.getPlayer().isSneaking()
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
        BottleConversion.convert(player, player.getInventory().getHeldItemSlot());
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
